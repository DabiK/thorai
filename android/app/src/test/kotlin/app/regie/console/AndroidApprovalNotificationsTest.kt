package app.regie.console

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.SessionId
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
// Une Application neutre : aucun pont, service ou agent réel ne démarre dans ce test.
@Config(sdk = [33], application = Application::class)
class AndroidApprovalNotificationsTest {
    @Test
    fun `a blocked channel reports failure and the same adapter can deliver after reactivation`() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        val notifications = AndroidApprovalNotifications(context)
        val request = Approval(ApprovalId("pending"), SessionId("session"), ApprovalKind.COMMAND, "touch example")
        val channel = manager.getNotificationChannel("approvals")

        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(manager.areNotificationsEnabled())
        // Robolectric conserve le canal : cette mutation simule le réglage utilisateur Android.
        channel.importance = NotificationManager.IMPORTANCE_NONE
        assertFalse(notifications.show(request))
        assertTrue(manager.activeNotifications.isEmpty())

        channel.importance = NotificationManager.IMPORTANCE_LOW
        assertTrue(notifications.show(request))
        assertEquals("pending", manager.activeNotifications.single().tag)
        assertEquals("approvals", manager.activeNotifications.single().notification.channelId)

        notifications.cancel(request.id)
        channel.importance = NotificationManager.IMPORTANCE_NONE
        assertFalse(notifications.show(request))
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
