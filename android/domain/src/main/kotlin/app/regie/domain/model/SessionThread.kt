package app.regie.domain.model

/** Lignes ajoutées et retirées par une modification de fichier. */
data class LineChanges(val added: Int, val removed: Int) {
    operator fun plus(other: LineChanges) = LineChanges(added + other.added, removed + other.removed)
}

/** Ce que fait une action de l'agent, d'après le genre que l'agent lui donne. */
enum class ToolKind { READ, EDIT, EXECUTE, OTHER }

/**
 * Un élément du fil d'une session, tel que la passerelle le reconstruit à partir de ce que l'agent
 * et le pont exposent. [id] croît avec l'ordre d'arrivée : il reste le même quand l'élément change
 * (texte qui s'allonge, action qui se termine).
 */
sealed interface ThreadEntry {
    val id: Long

    /** Un message envoyé à l'agent. D'où il vient (dictée, écrit) n'est pas connu du pont. */
    data class UserMessage(override val id: Long, val text: String) : ThreadEntry

    /** Une suite de morceaux de réponse de l'agent, mis bout à bout. */
    data class AgentText(override val id: Long, val text: String) : ThreadEntry

    /**
     * Une action de l'agent. [path] : le premier fichier concerné, relatif au projet. [changes] : les
     * lignes ajoutées et retirées, si l'agent a montré le diff. [result] : la dernière ligne non vide
     * de la sortie d'une commande, si elle en a une.
     */
    data class ToolCall(
        override val id: Long,
        val kind: ToolKind,
        val title: String?,
        val command: String? = null,
        val path: String? = null,
        val changes: LineChanges? = null,
        val status: ActionStatus? = null,
        val result: String? = null,
        /** Tous les fichiers exposés par cet appel, avec le compte propre à chacun. */
        val files: List<ModifiedFile> = emptyList(),
        val diffs: List<DiffFragment> = emptyList(),
    ) : ThreadEntry

    /** Le tour a échoué ; [message] est celui du pont, s'il en donne un. */
    data class TurnFailed(override val id: Long, val message: String?) : ThreadEntry

    /** Le tour s'est terminé sur une interruption. */
    data class Interrupted(override val id: Long) : ThreadEntry
}

enum class PlanStepStatus { DONE, IN_PROGRESS, PENDING }

/** Une étape du plan que l'agent expose. */
data class PlanStep(val text: String, val status: PlanStepStatus)

/** Le contexte de l'agent : [used] sur [size] (unités de l'agent). */
data class ContextUsage(val used: Long, val size: Long) {
    init {
        require(size > 0 && used >= 0) { "contexte $used sur $size" }
    }

    /** Arrondi au plus proche. */
    val percent: Int get() = ((used * 100 + size / 2) / size).toInt().coerceIn(0, 100)
}

/**
 * Le fil d'une session : messages, réponses et actions, du plus ancien au plus récent, borné en
 * mémoire par la passerelle. [plan] est absent si l'agent n'en a pas communiqué ; [context] si l'agent
 * ne donne pas sa taille. [loaded] : le journal de la session a été lu ; sinon le fil ne contient que
 * ce qui est arrivé en direct depuis la connexion.
 */
data class SessionThread(
    val entries: List<ThreadEntry> = emptyList(),
    val plan: List<PlanStep>? = null,
    val context: ContextUsage? = null,
    val loaded: Boolean = false,
) {
    companion object {
        val EMPTY = SessionThread()
    }
}

/** Comment un message est parti (docs/protocole-pont.md, « Parler à une session »). */
enum class Delivery {
    /** Aucun tour en cours : un nouveau tour commence. */
    PROMPT,

    /** Un tour est en cours et l'agent l'a pris en compte dedans. */
    STEERING,

    /** Un tour est en cours : le message attend la fin du tour. */
    QUEUED,

    /** Le message n'est pas parti : la session a été interrompue pendant qu'il attendait. */
    CANCELLED,

    /** La requête a été mise en file, mais son accusé est perdu ou non compris. Ne pas proposer de renvoi. */
    UNKNOWN,

    /** Le message n'a pas été mis en file, ou le pont a explicitement refusé la requête. */
    NOT_SENT;

    /** Seule une non-livraison certaine permet de restaurer le texte pour un nouvel envoi. */
    val canRetry: Boolean get() = this == NOT_SENT || this == CANCELLED
}
