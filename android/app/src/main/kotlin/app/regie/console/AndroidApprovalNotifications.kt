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
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.port.ApprovalNotifications

/** Transport Android uniquement ; le domaine décide quand prévenir. */
class AndroidApprovalNotifications(private val context: Context) : ApprovalNotifications {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Validations", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun show(request: Approval): Boolean {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL) ?: return false
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, TopActivity::class.java)
            .setAction("app.regie.approval.${request.id.value}")
            .putExtra(TopActivity.EXTRA_APPROVAL, request.id.value)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(request.id.value, 0, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Un agent attend ta validation")
            .setContentText(request.subject ?: "Ouvrir la demande")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build())
        return true
    }

    override fun cancel(id: ApprovalId) { manager.cancel(id.value, 0) }

    private companion object { const val CHANNEL = "approvals" }
}
