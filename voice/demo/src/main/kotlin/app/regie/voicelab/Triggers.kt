package app.regie.voicelab

/**
 * L2 and R2 of the Thor arrive twice: as keys (`KEYCODE_BUTTON_L2`) and as analog axes
 * (`AXIS_LTRIGGER`/`AXIS_BRAKE`). A trigger is pressed when either says so; [onChange] is called
 * once per press and once per release, whatever the order of the two signals.
 */
class Triggers(private val onChange: (Trigger, Boolean) -> Unit) {
    enum class Trigger { L2, R2 }

    private val key = BooleanArray(Trigger.entries.size)
    private val axis = BooleanArray(Trigger.entries.size)
    private val pressed = BooleanArray(Trigger.entries.size)

    fun onKey(trigger: Trigger, down: Boolean) {
        key[trigger.ordinal] = down
        update(trigger)
    }

    /** [value] in 0..1; a hysteresis keeps a trigger held halfway from flickering. */
    fun onAxis(trigger: Trigger, value: Float) {
        val i = trigger.ordinal
        axis[i] = if (axis[i]) value > RELEASE else value > PRESS
        update(trigger)
    }

    private fun update(trigger: Trigger) {
        val i = trigger.ordinal
        val now = key[i] || axis[i]
        if (now != pressed[i]) {
            pressed[i] = now
            onChange(trigger, now)
        }
    }

    private companion object {
        const val PRESS = 0.5f
        const val RELEASE = 0.2f
    }
}
