package app.regie.domain.model

import app.regie.domain.usecase.validBranchName
import app.regie.domain.usecase.projectFolderName

data class ProjectChoice(val name: String, val currentBranch: String? = null, val branches: List<String> = emptyList())
enum class NewSessionField(val label: String) { AGENT("Agent"), PROJECT("Projet"), BRANCH("Branche"), BASE("Depuis"), VALIDATION("Validation"), MODEL("Modèle"), THOUGHT("Réflexion"), INSTRUCTION("Consigne") }
enum class BranchChoice { CURRENT, EXISTING, NEW }

data class NewSessionForm(
    val visible: Boolean = false, val heard: String = "", val agent: Agent? = null, val project: String? = null,
    val newProject: Boolean = false, val projectName: String = "",
    val branchChoice: BranchChoice = BranchChoice.CURRENT, val branch: String = "", val base: String = "",
    val approvalMode: String = "plan", val instruction: String = "",
    val projects: List<ProjectChoice> = emptyList(), val field: NewSessionField = NewSessionField.AGENT,
    val catalogAvailable: Boolean = true, val loading: Boolean = false, val sending: Boolean = false, val uncertain: Boolean = false,
    val capturing: Boolean = false, val finalizing: Boolean = false, val partial: String = "", val level: Float = 0f,
    val notice: String = "",
    val configOptions: List<SessionOption> = emptyList(), val optionsLoading: Boolean = false, val optionsNotice: String = "",
) {
    val selectedProject: ProjectChoice? get() = projects.singleOrNull { it.name == project }
    val fields: List<NewSessionField> get() = NewSessionField.entries.filter { (it != NewSessionField.BASE || branchChoice == BranchChoice.NEW) && (!newProject || it != NewSessionField.BRANCH && it != NewSessionField.BASE) }
    val editable: Boolean get() = visible && !loading && !sending && !uncertain && !capturing && !finalizing
    val problem: String? get() = when {
        agent == null -> "Choisis un agent."
        newProject && projectFolderName(projectName) == null -> "Nom de projet vide ou invalide. L2 pour dicter le nom."
        newProject && projects.any { it.name == project } -> "Ce dossier existe déjà. Choisis un autre nom."
        !newProject && selectedProject == null -> "Choisis un projet disponible."
        branchChoice == BranchChoice.EXISTING && branch !in selectedProject!!.branches -> "Branche indisponible."
        branchChoice == BranchChoice.NEW && base !in selectedProject!!.branches -> "Choisis la base de la nouvelle branche."
        branchChoice == BranchChoice.NEW && branch.isNotBlank() && !validBranchName(branch) -> "Nom de branche invalide."
        branchChoice == BranchChoice.NEW && branch in selectedProject!!.branches -> "Cette branche existe déjà."
        instruction.length > 2000 -> "Consigne limitée à 2 000 caractères."
        else -> null
    }
    val canLaunch: Boolean get() = editable && !optionsLoading && catalogAvailable && problem == null
    val projectLabel: String get() = when {
        loading -> "Chargement des projets…"
        !catalogAvailable -> "Pont injoignable · reconnexion…"
        dictatingName && (capturing || finalizing) -> partial.ifBlank { "Écoute du nom…" }
        newProject -> "Nouveau projet…${project?.let { " · $it" }.orEmpty()}"
        else -> project ?: "À choisir · ← →"
    }
    val feedback: String get() = when {
        loading -> "Chargement des projets…"
        !capturing && !finalizing && !sending && !uncertain && problem != null -> problem!!
        else -> notice.ifEmpty { "Rien ne démarre avant ton A." }
    }
    val dictatingName: Boolean get() = newProject && this.field == NewSessionField.PROJECT
    val branchLabel: String get() = when (branchChoice) {
        BranchChoice.CURRENT -> "Courante${selectedProject?.currentBranch?.let { " · $it" }.orEmpty()}"
        BranchChoice.EXISTING -> branch
        BranchChoice.NEW -> "Nouvelle · ${branch.ifBlank { "nom choisi par l’agent" }}"
    }
    fun option(field: NewSessionField): SessionOption? = configOptions.firstOrNull { it.category == if (field == NewSessionField.MODEL) "model" else "thought_level" }
    fun optionLabel(field: NewSessionField): String = when {
        optionsLoading -> "Chargement…"
        optionsNotice.isNotEmpty() -> optionsNotice
        agent == null -> "Choisis un agent"
        else -> option(field)?.valueLabel ?: "Non exposé par cet agent"
    }
    val configValues: Map<String, String> get() = configOptions.filter { it.category in listOf("model", "thought_level") }.mapNotNull { o -> o.currentValue?.let { o.id to it } }.toMap()
    val modeLabel: String get() = approvalModeLabel(approvalMode)
}
