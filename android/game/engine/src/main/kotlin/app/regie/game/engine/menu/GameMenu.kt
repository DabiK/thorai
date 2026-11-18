package app.regie.game.engine.menu

/** Les touches qu'un menu du jeu lit ; l'app les traduit depuis la manette. */
enum class MenuKey { UP, DOWN, LEFT, RIGHT, A, B, X, Y, START }

/** Une entrée de menu : chacune mène à une fonction qui existe déjà dans l'app. */
enum class MenuEntry {
    /** Fermer l'écran titre : le hameau. */
    CONTINUE,

    /** La fiche de nouvelle session. */
    NEW_SESSION,

    /** Le courrier : les derniers événements des sessions. */
    COURIER,

    /** La connexion des agents (ADR 18). */
    AGENTS,

    /** Les réglages de la voix. */
    SETTINGS,

    /** Mettre toutes les sessions en pause (PRD, Start sur la vue d'ensemble). */
    PAUSE_ALL,

    /** Arrêter le pont et les sessions, avec confirmation si une session travaille. */
    QUIT,
}

/** Les deux menus du jeu (`da-jeu.md`, écrans 01 et 02). */
enum class MenuKind(val entries: List<MenuEntry>) {
    /** Le menu principal de l'écran titre, au lancement. */
    TITLE(listOf(MenuEntry.CONTINUE, MenuEntry.NEW_SESSION, MenuEntry.AGENTS, MenuEntry.SETTINGS, MenuEntry.QUIT)),

    /** Le menu de pause, ouvert par Start sur le hameau. */
    PAUSE(listOf(MenuEntry.COURIER, MenuEntry.AGENTS, MenuEntry.SETTINGS, MenuEntry.PAUSE_ALL, MenuEntry.QUIT)),
}

/** Ce que l'app doit faire après une touche. */
sealed interface MenuOutcome {
    /** A sur une entrée. Le menu s'est déjà fermé s'il le fallait. */
    data class Choose(val entry: MenuEntry) : MenuOutcome

    /** A sur une lettre du courrier : sa session ou sa validation. */
    data class OpenLetter(val index: Int) : MenuOutcome

    /** X dans le courrier. */
    data object MarkAllRead : MenuOutcome
}

/** Le menu après une touche, et ce qu'elle demande. */
data class MenuStep(val menu: GameMenu, val outcome: MenuOutcome? = null)

/**
 * Le menu ouvert : son genre, l'entrée sous le curseur, et la page du courrier avec sa lettre choisie.
 * Les pages Agents et Réglages sont les écrans existants : l'app les ouvre par-dessus le menu, qui
 * reste ouvert dessous et reprend la main quand ils se ferment.
 */
data class GameMenu(
    val kind: MenuKind? = null,
    val selected: Int = 0,
    val courierOpen: Boolean = false,
    val letter: Int = 0,
) {
    val open: Boolean get() = kind != null
    val entries: List<MenuEntry> get() = kind?.entries.orEmpty()
    val current: MenuEntry? get() = entries.getOrNull(selected)

    /** [letters] : le nombre de lettres du courrier, pour borner le curseur. */
    fun press(key: MenuKey, letters: Int = 0): MenuStep {
        val kind = kind ?: return MenuStep(this)
        if (courierOpen) return pressInCourier(key, letters)
        return when (key) {
            MenuKey.UP -> MenuStep(copy(selected = (selected - 1).coerceAtLeast(0)))
            MenuKey.DOWN -> MenuStep(copy(selected = (selected + 1).coerceAtMost(entries.size - 1)))
            MenuKey.A -> choose(entries[selected])
            // Start sur le titre : commencer. Sur la pause : reprendre.
            MenuKey.START -> if (kind == MenuKind.TITLE) choose(MenuEntry.CONTINUE) else MenuStep(CLOSED)
            // Le titre est le premier écran : B n'a rien à annuler.
            MenuKey.B -> if (kind == MenuKind.TITLE) MenuStep(this) else MenuStep(CLOSED)
            MenuKey.LEFT, MenuKey.RIGHT, MenuKey.X, MenuKey.Y -> MenuStep(this)
        }
    }

    private fun choose(entry: MenuEntry): MenuStep = when (entry) {
        MenuEntry.CONTINUE, MenuEntry.NEW_SESSION, MenuEntry.PAUSE_ALL -> MenuStep(CLOSED, MenuOutcome.Choose(entry))
        MenuEntry.COURIER -> MenuStep(copy(courierOpen = true, letter = 0), MenuOutcome.Choose(entry))
        MenuEntry.AGENTS, MenuEntry.SETTINGS, MenuEntry.QUIT -> MenuStep(this, MenuOutcome.Choose(entry))
    }

    private fun pressInCourier(key: MenuKey, letters: Int): MenuStep {
        val last = (letters - 1).coerceAtLeast(0)
        return when (key) {
            MenuKey.UP -> MenuStep(copy(letter = (letter - 1).coerceIn(0, last)))
            MenuKey.DOWN -> MenuStep(copy(letter = (letter + 1).coerceIn(0, last)))
            MenuKey.A -> MenuStep(this, if (letter < letters) MenuOutcome.OpenLetter(letter) else null)
            MenuKey.X -> MenuStep(this, MenuOutcome.MarkAllRead)
            MenuKey.B -> MenuStep(copy(courierOpen = false))
            MenuKey.START -> MenuStep(CLOSED)
            MenuKey.LEFT, MenuKey.RIGHT, MenuKey.Y -> MenuStep(this)
        }
    }

    companion object {
        val CLOSED = GameMenu()

        fun title() = GameMenu(MenuKind.TITLE)

        fun pause() = GameMenu(MenuKind.PAUSE)
    }
}
