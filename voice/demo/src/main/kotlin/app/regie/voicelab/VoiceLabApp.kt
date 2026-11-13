package app.regie.voicelab

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the [VoiceLab] for the whole process: a bench or a download outlives the activity. */
class VoiceLabApp : Application() {
    lateinit var lab: VoiceLab
        private set

    override fun onCreate() {
        super.onCreate()
        lab = VoiceLab(this, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
}
