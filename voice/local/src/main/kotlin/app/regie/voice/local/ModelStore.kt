package app.regie.voice.local

import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

/**
 * Downloads and installs the models under [root], one directory per model.
 *
 * - A download goes to `<file>.part` and resumes from there (HTTP range) after an interruption;
 *   it is renamed once complete.
 * - An archive is extracted into a temporary directory, moved into place, then deleted.
 * - A model is installed when its directory holds the [MARKER] file, written last.
 * - Free space is checked before downloading.
 */
class ModelStore(
    private val root: File,
    private val client: OkHttpClient,
    private val io: CoroutineContext = Dispatchers.IO,
    private val usableSpace: (File) -> Long = { it.usableSpace },
) {
    private val mutex = Mutex()
    private val mutableProgress = MutableStateFlow<Float?>(null)

    /** Progress of the installation in progress, in 0..1; null when nothing is being installed. */
    val progress: StateFlow<Float?> = mutableProgress.asStateFlow()

    fun directory(spec: ModelSpec): File = File(root, spec.name)

    fun isInstalled(spec: ModelSpec): Boolean = File(directory(spec), MARKER).isFile

    fun missing(specs: List<ModelSpec>): List<ModelSpec> = specs.filterNot(::isInstalled)

    /** Bytes still to download for [specs]. */
    fun remainingDownloadBytes(specs: List<ModelSpec>): Long =
        missing(specs).sumOf { (it.downloadBytes - partFile(it).length()).coerceAtLeast(0) }

    /**
     * Installs the missing models among [specs]. Concurrent calls are serialised: a second caller
     * waits, then finds the models installed. Failures are thrown as [VoiceException].
     */
    suspend fun install(specs: List<ModelSpec>) = mutex.withLock {
        val todo = missing(specs)
        if (todo.isEmpty()) return@withLock
        withContext(io) {
            try {
                root.mkdirs()
                checkFreeSpace(todo)
                val total = todo.sumOf { it.downloadBytes * (if (it.archive) 2 else 1) }.toFloat()
                var done = 0L
                mutableProgress.value = 0f
                for (spec in todo) {
                    val file = download(spec) { bytes -> mutableProgress.value = ((done + bytes) / total).coerceIn(0f, 1f) }
                    done += spec.downloadBytes
                    if (spec.archive) {
                        extract(spec, file) { bytes -> mutableProgress.value = ((done + bytes) / total).coerceIn(0f, 1f) }
                        done += spec.downloadBytes
                        file.delete()
                    } else {
                        val dir = directory(spec).apply { mkdirs() }
                        if (!file.renameTo(File(dir, spec.fileName))) throw IOException("cannot move ${file.name}")
                        File(dir, MARKER).writeText(spec.url)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: VoiceException) {
                throw e
            } catch (e: IOException) {
                throw VoiceException(VoiceError.Offline, e)
            } finally {
                mutableProgress.value = null
            }
        }
    }

    private fun checkFreeSpace(todo: List<ModelSpec>) {
        val needed = remainingDownloadBytes(todo) + todo.sumOf { it.installedBytes } + SPACE_MARGIN
        val free = usableSpace(root)
        if (free < needed) {
            throw VoiceException(
                VoiceError.Provider("storage_full", "models need ${needed / MB} MB, only ${free / MB} MB free"),
            )
        }
    }

    private fun partFile(spec: ModelSpec) = File(root, "${spec.fileName}.part")

    /** Downloads [spec] (resuming a previous `.part`) and returns the complete file. */
    private suspend fun download(spec: ModelSpec, onBytes: (Long) -> Unit): File {
        val complete = File(root, spec.fileName)
        if (complete.isFile) return complete
        val part = partFile(spec)
        repeat(2) {
            val offset = if (part.isFile) part.length() else 0L
            val request = Request.Builder().url(spec.url).apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
            val call = client.newCall(request)
            val job = currentCoroutineContext()[Job]
            val cancelOnJob = job?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    when {
                        response.code == HTTP_RANGE_NOT_SATISFIABLE -> part.delete() // stale part: start over
                        response.code == HTTP_PARTIAL && offset > 0 -> {
                            append(response, part, append = true, start = offset, onBytes)
                            check(part.renameTo(complete)) { "cannot rename ${part.name}" }
                            return complete
                        }
                        response.isSuccessful -> {
                            append(response, part, append = false, start = 0, onBytes)
                            check(part.renameTo(complete)) { "cannot rename ${part.name}" }
                            return complete
                        }
                        else -> throw VoiceException(VoiceError.Provider("download_http_${response.code}", spec.url))
                    }
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw e
            } finally {
                cancelOnJob?.dispose()
            }
        }
        throw VoiceException(VoiceError.Provider("download_failed", spec.url))
    }

    private suspend fun append(response: Response, part: File, append: Boolean, start: Long, onBytes: (Long) -> Unit) {
        val body = response.body
        val expected = body.contentLength().takeIf { it >= 0 }?.let { it + start }
        var written = start
        FileOutputStream(part, append).use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    written += n
                    onBytes(written)
                }
            }
        }
        if (expected != null && written != expected) throw IOException("download truncated: $written of $expected bytes")
    }

    /** Extracts the `.tar.bz2` [archive] of [spec] into its directory, writing the marker last. */
    private suspend fun extract(spec: ModelSpec, archive: File, onCompressedBytes: (Long) -> Unit) {
        val staging = File(root, "${spec.name}.extracting")
        staging.deleteRecursively()
        staging.mkdirs()
        val stagingPath = staging.canonicalPath + File.separator
        try {
            val counting = CountingInputStream(FileInputStream(archive))
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counting, BUFFER))).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    currentCoroutineContext().ensureActive()
                    val target = File(staging, entry.name).canonicalFile
                    if (!target.path.startsWith(stagingPath) && target != staging.canonicalFile) {
                        throw IOException("archive entry outside of its directory: ${entry.name}")
                    }
                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isFile -> {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                val buffer = ByteArray(BUFFER)
                                while (true) {
                                    val n = tar.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    onCompressedBytes(counting.count)
                                }
                            }
                        }
                        // Links and devices are not needed by the models: skipped.
                        else -> Unit
                    }
                }
            }
        } catch (e: IOException) {
            // A corrupt archive is not resumable: drop it so the next attempt downloads it again.
            archive.delete()
            staging.deleteRecursively()
            throw e
        }
        // Releases wrap their files in one top-level directory named like the model.
        val content = staging.listFiles().orEmpty()
        val source = content.singleOrNull()?.takeIf { it.isDirectory } ?: staging
        val target = directory(spec)
        target.deleteRecursively()
        if (!source.renameTo(target)) throw IOException("cannot move the extracted model to $target")
        staging.deleteRecursively()
        File(target, MARKER).writeText(spec.url)
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        @Volatile var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }

    companion object {
        /** Written in a model directory once it is complete. */
        const val MARKER = ".installed"

        private const val MB = 1_000_000L
        private const val SPACE_MARGIN = 50 * MB
        private const val BUFFER = 64 * 1024
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
