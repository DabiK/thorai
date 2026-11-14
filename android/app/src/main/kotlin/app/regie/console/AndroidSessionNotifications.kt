package app.regie.console

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.regie.R
import app.regie.TopActivity
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionNotice
import app.regie.domain.port.SessionNotifications

/** Android transport: all event selection and wording belongs to the domain. */
class AndroidSessionNotifications(private val context: Context) : SessionNotifications {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Questions et fins de session", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun show(notice: SessionNotice): Boolean {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL) ?: return false
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, TopActivity::class.java)
            .setAction("app.regie.session.${notice.sessionId.value}")
            .putExtra(TopActivity.EXTRA_SESSION, notice.sessionId.value)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify("session:${notice.sessionId.value}", 0, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build())
        return true
    }

    override fun cancel(id: SessionId) { manager.cancel("session:${id.value}", 0) }
    private companion object { const val CHANNEL = "sessions" }
}
