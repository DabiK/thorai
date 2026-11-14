package app.regie.console

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import app.regie.TopActivity
import app.regie.domain.model.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AndroidSessionNotificationsTest {
    @Test fun `each alert opens its own session and blocked delivery is retryable`() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        val port = AndroidSessionNotifications(context)
        val a = SessionNotice(SessionId("a"), SessionNoticeKind.FINISHED, "Session terminée", "Tests : 68 passent")
        val b = SessionNotice(SessionId("b"), SessionNoticeKind.QUESTION, "Question", "Quel fichier ?")
        assertTrue(port.show(a))
        assertTrue(port.show(b))
        val notices = manager.activeNotifications.associateBy { it.tag }
        assertEquals(2, notices.size)
        assertEquals("a", shadowOf(notices.getValue("session:a").notification.contentIntent).savedIntent.getStringExtra(TopActivity.EXTRA_SESSION))
        assertEquals("b", shadowOf(notices.getValue("session:b").notification.contentIntent).savedIntent.getStringExtra(TopActivity.EXTRA_SESSION))
        port.cancel(a.sessionId)
        assertEquals(1, manager.activeNotifications.size)
        manager.getNotificationChannel("sessions").importance = NotificationManager.IMPORTANCE_NONE
        assertFalse(port.show(a))
        manager.getNotificationChannel("sessions").importance = NotificationManager.IMPORTANCE_DEFAULT
        assertTrue(port.show(a))
    }
}
