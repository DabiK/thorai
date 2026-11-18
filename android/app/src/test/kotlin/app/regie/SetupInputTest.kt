package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.input.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

/** Ticket 28 : le parcours d'accueil prend la manette, et seulement ses actions. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SetupInputTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun TestScope.container(gateway: SetupGateway): AppContainer {
        app.getSharedPreferences("setup", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        return AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope) { gateway }.also {
            it.start(replay = true); runCurrent()
        }
    }

    @Test fun `the first launch opens the setup over the login offer, and the gamepad stays inside it`() = runTest {
        val gateway = SetupGateway()
        val container = container(gateway)
        // Le pont connecté a déjà proposé la connexion : le parcours la remplace au premier lancement.
        container.agentAuthentication.value.takeIf { it.visible }?.let { container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent() }
        container.offerSetup(); runCurrent()
        assertTrue(container.setup.state.value.visible)
        assertEquals(SetupStep.WELCOME, container.setup.state.value.step)
        container.handleInput(GamepadInput.Pressed(Button.X)); runCurrent()
        assertFalse(container.newSession.value.visible, "X de l'accueil ne crée pas de session sous le parcours")
        container.handleInput(GamepadInput.TriggerDown(Trigger.L2)); container.handleInput(GamepadInput.TriggerUp(Trigger.L2)); runCurrent()
        assertFalse(container.dictation.state.value.visible)
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertEquals(SetupStep.TERMUX, container.setup.state.value.step)
        container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
        assertEquals(SetupStep.WELCOME, container.setup.state.value.step)
        assertFalse(container.agentAuthentication.value.visible)
        // B sur la bienvenue referme : la manette n'est jamais enfermée dans le parcours.
        container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
        assertFalse(container.setup.state.value.visible)
    }

    @Test fun `after the setup, a reconnection of the bridge does not offer the login again`() = runTest {
        val gateway = SetupGateway(connected = false)
        val container = container(gateway)
        container.offerSetup(); runCurrent()
        container.setup.close()
        gateway.connection.value = BridgeConnection.Connected; runCurrent()
        assertFalse(container.agentAuthentication.value.visible)
    }

    @Test fun `the bridge step asks RUN_COMMAND through the activity, and a refusal for good sends to the app info`() = runTest {
        val gateway = SetupGateway(connected = false)
        val container = container(gateway)
        shadowOf(app.packageManager).installPackage(android.content.pm.PackageInfo().apply { packageName = "com.termux" })
        container.offerSetup(); runCurrent()
        val asked = mutableListOf<SetupPermission>()
        val job = backgroundScope.launch { container.permissionRequests.collect { asked += it } }
        runCurrent()
        repeat(2) { container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent() }
        assertEquals(SetupStep.BRIDGE, container.setup.state.value.step)
        assertEquals(SetupAction.GRANT_RUN_COMMAND, container.setup.state.value.keys.a)
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertEquals(listOf(SetupPermission.RUN_COMMAND), asked)
        // Fenêtre fermée sans réponse : Android redemandera, ce n'est pas un refus définitif.
        container.onPermissionAnswer(SetupPermission.RUN_COMMAND, granted = false, askAgain = false); runCurrent()
        assertEquals(SetupAction.GRANT_RUN_COMMAND, container.setup.state.value.keys.a)
        container.onPermissionAnswer(SetupPermission.RUN_COMMAND, granted = false, askAgain = true); runCurrent()
        assertEquals(SetupAction.GRANT_RUN_COMMAND, container.setup.state.value.keys.a)
        container.onPermissionAnswer(SetupPermission.RUN_COMMAND, granted = false, askAgain = false); runCurrent()
        assertEquals(SetupAction.OPEN_APP_INFO, container.setup.state.value.keys.a)
        job.cancel()
    }

    @Test fun `Préparer la console resumes at the first step not done and the end opens the new session form`() = runTest {
        val gateway = SetupGateway()
        val container = container(gateway)
        container.openSetup(); runCurrent()
        // Pont joint (Termux et pont faits), micro non accordé : la voix est la première étape à faire.
        assertEquals(SetupStep.VOICE, container.setup.state.value.step)
        container.setup.close()
        assertFalse(container.setup.state.value.visible)
        container.offerSetup(); runCurrent()
        assertFalse(container.setup.state.value.visible, "refermé, le parcours ne se rouvre plus seul")
    }

}

private class SetupGateway(connected: Boolean = true) : AgentGateway {
    override val sessions = MutableStateFlow(emptyList<Session>())
    override val approvals = MutableStateFlow(emptyList<Approval>())
    override val agents = MutableStateFlow(emptyList<AgentVersion>())
    override val connection = MutableStateFlow(if (connected) BridgeConnection.Connected else BridgeConnection.Connecting)
    override suspend fun authentication() = listOf(AgentAuthentication(Agent.CODEX, false))
    override suspend fun maintainConnection(): Nothing = awaitCancellation()
    override suspend fun isBridgeUp() = true
    override suspend fun shutdownBridge() = true
    override fun thread(id: SessionId) = MutableStateFlow(SessionThread.EMPTY)
    override suspend fun sendMessage(id: SessionId, text: String) = Delivery.NOT_SENT
    override suspend fun interrupt(id: SessionId) = false
    override suspend fun answerApproval(id: ApprovalId, optionId: String) = ApprovalReply.UNKNOWN
}
