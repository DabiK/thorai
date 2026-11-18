package app.regie.console

import android.net.LocalServerSocket
import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/** A one-use channel reports the peer identity supplied by the OS, never by the client. */
fun interface TokenChannel {
    fun serve(name: String, receive: (uid: Int, output: OutputStream) -> Unit)
}

/** Only the kernel-authenticated Termux UID can read the secret. */
class BridgeTokenTransfer(private val channel: TokenChannel = AndroidTokenChannel()) {
    fun open(token: String, termuxUid: Int): String {
        val name = "regie-pair-${UUID.randomUUID()}"
        channel.serve(name) { uid, output ->
            if (uid == termuxUid) {
                output.write(token.toByteArray(Charsets.US_ASCII))
                output.flush()
            }
        }
        return name
    }
}

/** Abstract Unix socket: no shared file or TCP port, bounded even if Termux never starts. */
private class AndroidTokenChannel : TokenChannel {
    override fun serve(name: String, receive: (Int, OutputStream) -> Unit) {
        val server = LocalServerSocket(name)
        try {
            Os.setsockoptTimeval(server.fileDescriptor, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(30_000))
        } catch (failure: Exception) {
            server.close()
            throw failure
        }
        thread(name = "regie-pairing", isDaemon = true) {
            server.use {
                try {
                    server.accept().use { peer -> receive(peer.peerCredentials.uid, peer.outputStream) }
                } catch (_: Exception) {
                    // Startup retries after 30 s; never log token, bytes or exception details.
                }
            }
        }
    }
}
