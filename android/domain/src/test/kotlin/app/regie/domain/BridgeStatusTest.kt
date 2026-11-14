package app.regie.domain

import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.LaunchResult
import app.regie.domain.model.UnreachableReason
import kotlin.test.Test
import kotlin.test.assertEquals

class BridgeStatusTest {

    private fun unreachable(reason: UnreachableReason, launch: LaunchResult?) =
        BridgeStatus.of(BridgeConnection.Unreachable(reason), launch)

    @Test
    fun `a missing or refused token asks to copy it again, whatever the launch`() {
        assertEquals(BridgeStatus.Unreachable(BridgeHint.TOKEN_MISSING), unreachable(UnreachableReason.TOKEN_MISSING, LaunchResult.SENT))
        assertEquals(BridgeStatus.Unreachable(BridgeHint.TOKEN_REFUSED), unreachable(UnreachableReason.TOKEN_REFUSED, null))
    }

    @Test
    fun `a silent bridge that Régie may not start asks for the permission`() {
        assertEquals(
            BridgeStatus.Unreachable(BridgeHint.GRANT_RUN_COMMAND),
            unreachable(UnreachableReason.NOT_RESPONDING, LaunchResult.PERMISSION_MISSING),
        )
        assertEquals(
            BridgeStatus.Unreachable(BridgeHint.TERMUX_MISSING),
            unreachable(UnreachableReason.NOT_RESPONDING, LaunchResult.TERMUX_MISSING),
        )
    }

    @Test
    fun `an external bridge must be started on its host instead of promising a restart`() {
        assertEquals(BridgeStatus.Unreachable(BridgeHint.START_EXTERNAL), unreachable(UnreachableReason.NOT_RESPONDING, LaunchResult.EXTERNAL))
    }

    @Test
    fun `otherwise Régie restarts the bridge on its own`() {
        assertEquals(BridgeStatus.Unreachable(BridgeHint.RESTARTING), unreachable(UnreachableReason.NOT_RESPONDING, LaunchResult.SENT))
        assertEquals(BridgeStatus.Unreachable(BridgeHint.RESTARTING), unreachable(UnreachableReason.NOT_RESPONDING, null))
    }

    @Test
    fun `connecting and connected are shown as such`() {
        assertEquals(BridgeStatus.Connecting, BridgeStatus.of(BridgeConnection.Connecting, LaunchResult.PERMISSION_MISSING))
        assertEquals(BridgeStatus.Connected, BridgeStatus.of(BridgeConnection.Connected, null))
    }
}
