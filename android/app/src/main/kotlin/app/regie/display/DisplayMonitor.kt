package app.regie.display

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Suit les écrans avec [DisplayManager] et en déduit le [ScreenMode] par [DisplayPolicy]. */
class DisplayMonitor(private val manager: DisplayManager) {

    private var forceSingle = false
    private val _mode = MutableStateFlow(compute())
    val mode: StateFlow<ScreenMode> = _mode.asStateFlow()

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    init {
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    /** Force le mode un seul écran, pour le tester sans éteindre l'écran du bas. */
    fun forceSingleScreen(force: Boolean) {
        forceSingle = force
        refresh()
    }

    private fun refresh() {
        val next = compute()
        if (next != _mode.value) Log.i(TAG, "mode d'écran : $next")
        _mode.value = next
    }

    private fun compute(): ScreenMode = DisplayPolicy.choose(manager.displays.map { it.snapshot() }, forceSingle)

    private fun Display.snapshot() = DisplaySnapshot(
        id = displayId,
        isDefault = displayId == Display.DEFAULT_DISPLAY,
        isOn = state in ON_STATES,
        isPresentation = flags and Display.FLAG_PRESENTATION != 0,
        isPrivate = flags and Display.FLAG_PRIVATE != 0,
    )

    private companion object {
        const val TAG = "Regie"
        val ON_STATES = setOf(Display.STATE_ON, Display.STATE_ON_SUSPEND, Display.STATE_VR)
    }
}
