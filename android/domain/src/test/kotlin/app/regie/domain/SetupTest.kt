package app.regie.domain

import app.regie.domain.model.Agent
import app.regie.domain.model.AgentAuthentication
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.SetupAction
import app.regie.domain.model.SetupChecks
import app.regie.domain.model.SetupPermission
import app.regie.domain.model.SetupStep
import app.regie.domain.model.VoiceModel
import app.regie.domain.port.SetupMemory
import app.regie.domain.usecase.ReviewSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupTest {
    private class Memory(override var finished: Boolean = false) : SetupMemory {
        override fun finish() { finished = true }
    }

    private val performed = mutableListOf<SetupAction>()
    private val memory = Memory()
    private val setup = ReviewSetup(memory, performed::add)

    private val everything = SetupChecks(
        termux = true, runCommand = true, bridge = BridgeStatus.Connected, microphone = true,
        voiceModel = VoiceModel.Installed, notifications = true, consoleMode = true, androidAccess = true,
        wirelessDebugging = true, agents = listOf(AgentAuthentication(Agent.CODEX, connected = true)),
    )

    @Test fun `the first launch opens the welcome, once, and never after it was finished or closed`() {
        assertTrue(setup.offerOnLaunch())
        assertEquals(SetupStep.WELCOME, setup.state.value.step)
        assertFalse(setup.offerOnLaunch())
        setup.close()
        assertTrue(memory.finished)
        assertFalse(setup.state.value.visible)
        assertFalse(ReviewSetup(memory) {}.offerOnLaunch())
    }

    @Test fun `Configurer resumes at the first step not done, skipped optional ones included`() {
        setup.update(everything.copy(consoleMode = false, notifications = true))
        setup.open()
        assertEquals(SetupStep.CONSOLE_MODE, setup.state.value.step)
        setup.close()
        setup.update(everything)
        setup.open()
        assertEquals(SetupStep.FINISH, setup.state.value.step)
    }

    @Test fun `A continues on a step already in place, B goes back, the welcome has no back`() {
        setup.update(everything)
        setup.offerOnLaunch()
        assertNull(setup.state.value.keys.b)
        setup.press(setup.state.value.keys.a)
        assertEquals(SetupStep.TERMUX, setup.state.value.step)
        assertEquals(SetupAction.NEXT, setup.state.value.keys.a)
        setup.press(setup.state.value.keys.b)
        assertEquals(SetupStep.WELCOME, setup.state.value.step)
        assertTrue(performed.isEmpty())
    }

    @Test fun `without Termux, A opens its releases and the install commands can be copied`() {
        setup.offerOnLaunch(); setup.press(SetupAction.NEXT)
        val keys = setup.state.value.keys
        assertEquals(SetupAction.OPEN_TERMUX_RELEASES, keys.a)
        assertEquals(SetupAction.COPY_INSTALL_COMMANDS, keys.y)
        setup.press(keys.a)
        assertEquals(listOf(SetupAction.OPEN_TERMUX_RELEASES), performed)
        assertEquals(SetupStep.TERMUX, setup.state.value.step)
        setup.update(SetupChecks(termux = true))
        assertEquals(SetupAction.NEXT, setup.state.value.keys.a)
        assertEquals(SetupAction.COPY_INSTALL_COMMANDS, setup.state.value.keys.y)
    }

    @Test fun `the pairing commands ask the port and the code, and grant this app only`() {
        val commands = app.regie.domain.model.SetupCommands.pairing("app.thorai")
        assertTrue("read -p" in commands && "pair.sh \"\$p\" \"\$c\"" in commands)
        assertTrue("pm grant app.thorai android.permission.WRITE_SECURE_SETTINGS" in commands)
        assertFalse("<port>" in commands)
    }

    @Test fun `the bridge step asks the permission, then waits, then retries an unreachable bridge`() {
        setup.offerOnLaunch(); setup.press(SetupAction.NEXT); setup.update(SetupChecks(termux = true)); setup.press(SetupAction.NEXT)
        assertEquals(SetupStep.BRIDGE, setup.state.value.step)
        assertEquals(SetupAction.GRANT_RUN_COMMAND, setup.state.value.keys.a)
        setup.update(SetupChecks(termux = true, runCommand = true, bridge = BridgeStatus.Connecting))
        assertNull(setup.state.value.keys.a)
        setup.update(SetupChecks(termux = true, runCommand = true, bridge = BridgeStatus.Unreachable(BridgeHint.TERMUX_MISSING)))
        assertEquals(SetupAction.RETRY_BRIDGE, setup.state.value.keys.a)
        setup.update(SetupChecks(termux = true, runCommand = true, bridge = BridgeStatus.Connected))
        assertTrue(setup.state.value.done)
        assertEquals(SetupAction.NEXT, setup.state.value.keys.a)
    }

    @Test fun `a permission refused for good sends to the app info instead of asking again`() {
        setup.update(SetupChecks(termux = true, refused = setOf(SetupPermission.RUN_COMMAND, SetupPermission.MICROPHONE)))
        setup.offerOnLaunch(); setup.press(SetupAction.NEXT); setup.press(SetupAction.NEXT)
        assertEquals(SetupAction.OPEN_APP_INFO, setup.state.value.keys.a)
    }

    @Test fun `the voice asks the microphone, then downloads, and lets the user move on while it downloads`() {
        setup.update(everything.copy(microphone = false, voiceModel = VoiceModel.Missing(487_650_000, 1_200_000_000)))
        setup.open()
        assertEquals(SetupStep.VOICE, setup.state.value.step)
        assertEquals(SetupAction.GRANT_MICROPHONE, setup.state.value.keys.a)
        setup.update(setup.state.value.checks.copy(microphone = true))
        assertEquals(SetupAction.DOWNLOAD_VOICE, setup.state.value.keys.a)
        setup.press(SetupAction.DOWNLOAD_VOICE)
        setup.update(setup.state.value.checks.copy(voiceModel = VoiceModel.Downloading(0.4f)))
        assertFalse(setup.state.value.done)
        assertEquals(SetupAction.NEXT, setup.state.value.keys.a)
        assertEquals(listOf(SetupAction.DOWNLOAD_VOICE), performed)
    }

    @Test fun `optional steps can be skipped only while not done, a stale touch does nothing`() {
        setup.update(everything.copy(consoleMode = false, androidAccess = false, agents = emptyList()))
        setup.open()
        assertEquals(SetupStep.CONSOLE_MODE, setup.state.value.step)
        assertEquals(SetupAction.SKIP, setup.state.value.keys.x)
        setup.press(SetupAction.CREATE_SESSION)
        assertEquals(SetupStep.CONSOLE_MODE, setup.state.value.step)
        assertTrue(performed.isEmpty())
        setup.press(SetupAction.SKIP); setup.press(SetupAction.SKIP)
        assertEquals(SetupStep.AGENTS, setup.state.value.step)
        assertEquals(SetupAction.OPEN_AGENTS, setup.state.value.keys.a)
        setup.update(everything)
        assertNull(setup.state.value.keys.x)
        assertEquals(SetupAction.OPEN_AGENTS, setup.state.value.keys.y)
    }

    @Test fun `connecting the agents needs the bridge`() {
        val review = app.regie.domain.model.SetupReview(true, SetupStep.AGENTS, everything.copy(bridge = BridgeStatus.Connecting, agents = null))
        assertNull(review.keys.a)
        assertEquals(SetupAction.SKIP, review.keys.x)
    }

    @Test fun `a required step refused for good or a failed download can be passed, never a missing bridge`() {
        val voice = app.regie.domain.model.SetupReview(true, SetupStep.VOICE, everything.copy(microphone = false))
        assertNull(voice.keys.x)
        assertEquals(SetupAction.SKIP, voice.copy(checks = voice.checks.copy(refused = setOf(SetupPermission.MICROPHONE))).keys.x)
        assertEquals(SetupAction.SKIP, voice.copy(checks = everything.copy(voiceModel = VoiceModel.Failed(app.regie.domain.model.VoiceModelFailure.OFFLINE))).keys.x)
        val notifications = app.regie.domain.model.SetupReview(true, SetupStep.NOTIFICATIONS, everything.copy(notifications = false, refused = setOf(SetupPermission.NOTIFICATIONS)))
        assertEquals(SetupAction.SKIP, notifications.keys.x)
        val bridge = app.regie.domain.model.SetupReview(true, SetupStep.BRIDGE, everything.copy(runCommand = false, bridge = BridgeStatus.Connecting, refused = setOf(SetupPermission.RUN_COMMAND)))
        assertNull(bridge.keys.x)
    }

    @Test fun `opening at a given step shows it, whatever is done`() {
        setup.update(everything)
        setup.open(at = SetupStep.VOICE)
        assertEquals(SetupStep.VOICE, setup.state.value.step)
        assertTrue(setup.state.value.visible)
    }

    @Test fun `the end creates the first session and closes the setup for good`() {
        setup.update(everything)
        setup.open()
        assertEquals(SetupStep.FINISH, setup.state.value.step)
        setup.press(setup.state.value.keys.a)
        assertEquals(listOf(SetupAction.CREATE_SESSION), performed)
        assertFalse(setup.state.value.visible)
        assertTrue(memory.finished)
    }
}
