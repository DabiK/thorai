package app.regie.console

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BridgeTokenTransferTest {
    @Test
    fun `kernel identity of Termux is required before any token byte is written`() {
        for (uid in listOf(10123, 10124, 2000)) {
            val output = ByteArrayOutputStream()
            var socketName = ""
            val transfer = BridgeTokenTransfer { name, receive ->
                socketName = name
                receive(uid, output)
            }
            val name = transfer.open("private-token", 10123)
            assertEquals(if (uid == 10123) "private-token" else "", output.toString(Charsets.US_ASCII))
            assertEquals(socketName, name)
            assertFalse(name.contains("private-token"))
        }
    }
}
