package app.regie.domain.port

/** Le parcours d'accueil a-t-il déjà été mené à son terme, ou refermé, sur cette console ? */
interface SetupMemory {
    val finished: Boolean

    fun finish()
}
