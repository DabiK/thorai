package app.regie.console

import java.io.File
import java.security.SecureRandom

/** Private app storage; the legacy debug copy remains readable on each connection. */
class BridgeTokenFile(private val file: File) {
    @Synchronized
    fun readOrCreate(): String {
        file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(token)
        check(temporary.setReadable(false, false) && temporary.setWritable(false, false))
        check(temporary.setReadable(true, true) && temporary.setWritable(true, true))
        check(temporary.renameTo(file))
        return token
    }
}
