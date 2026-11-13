package app.regie.domain.model

/** Statut d'une session, tel que le pont le donne (docs/protocole-pont.md, « Statuts »). */
enum class SessionStatus {
    /** En cours. */
    WORKING,

    /** Attend ta validation. */
    AWAITING_APPROVAL,

    /** En attente de consigne. */
    IDLE,

    /** Arrêtée : pont arrêté ou redémarré. */
    STOPPED,

    /** Échec : tour en erreur, ou agent arrêté tout seul. */
    FAILED,
    ;

    /** La session travaille encore : « Quitter » l'interromprait. */
    val isBusy: Boolean get() = this == WORKING || this == AWAITING_APPROVAL
}
