package app.regie.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.regie.R

/** Lives only for an actual microphone capture, including when the upper screen loses focus. */
class MicrophoneService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Microphone", NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(108, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic).setContentTitle("Dictée en cours")
            .setContentText("Relâche L2 pour relire").setOngoing(true).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    private companion object { const val CHANNEL = "dictation-microphone" }
}
