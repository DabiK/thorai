package app.regie.voicelab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import app.regie.voicelab.Triggers.Trigger

/**
 * The single screen of the trial app, shown on the upper screen of the Thor. Hold L2 to dictate,
 * L2 or R2 cuts the voice, B reads the last sentence again in the guided reading. adb commands
 * arrive as intent extras through the `.Adb` alias (see [Command]).
 */
class MainActivity : ComponentActivity() {
    private val lab: VoiceLab get() = (application as VoiceLabApp).lab

    private val triggers = Triggers { trigger, pressed ->
        Log.i("VoiceLab", "$trigger ${if (pressed) "pressed" else "released"}")
        when (trigger) {
            Trigger.L2 -> if (pressed) lab.startDictation() else lab.finishDictation()
            Trigger.R2 -> if (pressed) lab.stopSpeaking()
        }
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Benches and dictations run for minutes without touching the console.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent { VoiceLabScreen(lab) }
        window.callback = TriggerWindowCallback(window.callback, triggers, guidedActive = { lab.guidedActive }, redo = lab::redoGuided)
        // A recreated activity must not replay the command that launched it.
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val commands = Command.parse(
            component = intent.component?.className,
            string = { intent.getStringExtra(it) },
            boolean = { if (intent.hasExtra(it)) intent.getBooleanExtra(it, false) else null },
        )
        lab.handle(commands)
    }
}

/**
 * Wraps the window callback: the window hands it every key and every gamepad motion before the
 * view hierarchy, hence before Compose (same approach as the app, ticket 01).
 */
private class TriggerWindowCallback(
    private val wrapped: Window.Callback,
    private val triggers: Triggers,
    private val guidedActive: () -> Boolean,
    private val redo: () -> Unit,
) : Window.Callback by wrapped {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // B keeps its usual meaning (back) outside of the guided reading.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B && guidedActive()) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) redo()
            return true
        }
        val trigger = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> Trigger.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> Trigger.R2
            else -> return wrapped.dispatchKeyEvent(event)
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) triggers.onKey(trigger, true)
            KeyEvent.ACTION_UP -> triggers.onKey(trigger, false)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            triggers.onAxis(Trigger.L2, maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)))
            triggers.onAxis(Trigger.R2, maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)))
        }
        return wrapped.dispatchGenericMotionEvent(event)
    }
}
