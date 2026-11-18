package app.regie.input

/**
 * Mode console (ticket 25) : la manette vue par le service d'accessibilité de Régie, avant l'appli
 * qui a le focus. Il ne prend que ce dont Régie a besoin :
 * - L2 et R2, toujours, quand Régie est [available] ;
 * - les autres boutons seulement pendant qu'un panneau de Régie les attend ([captured]) : la relecture
 *   d'une dictée (A envoie), un ordre vocal, la carte volante (B la ferme).
 * Le reste va à l'appli au premier plan. Le relâché d'une touche suit toujours son appui : une touche
 * prise à l'appui est prise au relâché, une touche laissée passer l'est jusqu'au bout.
 *
 * Quand une activité de Régie a la main, [available] est faux : sa fenêtre lit déjà la manette.
 */
class ConsoleKeys(
    private val router: InputRouter,
    private val available: () -> Boolean,
    private val captured: () -> Set<Button>,
) {
    private val owned = mutableSetOf<Int>()

    /** Retourne `true` si la touche est prise par Régie : l'appli au premier plan ne la voit pas. */
    fun onKey(keyCode: Int, isDown: Boolean, repeatCount: Int): Boolean {
        if (keyCode in owned) {
            if (!isDown) owned -= keyCode
            router.onKey(keyCode, isDown, repeatCount)
            return true
        }
        // A release or repeat whose press went elsewhere stays there.
        if (!isDown || repeatCount > 0 || !available()) return false
        val take = InputRouter.triggerOf(keyCode) != null || InputRouter.buttonOf(keyCode)?.let { it in captured() } == true
        if (!take) return false
        owned += keyCode
        router.onKey(keyCode, true, 0)
        return true
    }

    /** Le service s'arrête : les appuis en cours sont terminés. */
    fun reset() {
        owned.clear()
        router.reset()
    }
}
