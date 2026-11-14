package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.test.core.app.ApplicationProvider
import app.regie.input.GamepadInput
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DiffInputTest {
    @Test fun `stick release during a voice overlay stops hidden diff scrolling`() {
        // No start(): no gateway, service, voice capture or agent is started.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java))
        container.diffVelocity.value = 0.8f
        container.openVoiceSettings()
        container.handleInput(GamepadInput.Scroll(0f))
        container.closeVoiceSettings()
        assertEquals(0f, container.diffVelocity.value)
    }
}
