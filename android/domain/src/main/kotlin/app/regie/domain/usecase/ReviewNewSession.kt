package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

/** Owns the draft until explicit confirmation; the bridge creates only explicitly requested projects. */
class ReviewNewSession(private val gateway: AgentGateway, private val capture: DictationCapture,
    private val scope: CoroutineScope, private val openSession: (SessionId) -> Unit) {
    private val mutable = MutableStateFlow(NewSessionForm())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var recording: Job? = null
    private var catalogJob: Job? = null
    private var held = false
    private var optionsJob: Job? = null

    fun open(seed: NewSessionDraft? = null, heard: String = "") {
        if (state.value.sending) return
        job?.cancel(); catalogJob?.cancel(); optionsJob?.cancel(); cancelCapture()
        mutable.value = NewSessionForm(visible = true, heard = heard, loading = true, agent = seed?.agent, project = seed?.project, newProject = seed?.newProject == true, projectName = if (seed?.newProject == true) seed.project else "",
            branchChoice = if (seed?.newBranch == true) BranchChoice.NEW else if (seed?.branch != null) BranchChoice.EXISTING else BranchChoice.CURRENT,
            branch = seed?.branch.orEmpty(), base = seed?.baseBranch.orEmpty(), instruction = seed?.instruction.orEmpty(), approvalMode = seed?.approvalMode ?: "plan")
        refreshOptions()
        catalogJob = scope.launch {
            gateway.connection.collectLatest { connection ->
                if (!state.value.visible || state.value.sending || state.value.uncertain) return@collectLatest
                mutable.value = state.value.copy(loading = true, catalogAvailable = false)
                val catalog = loadCatalog()
                val projects = catalog.orEmpty()
                if (state.value.sending || !state.value.visible) return@collectLatest
                val branches = projects.singleOrNull { it.name == state.value.project }?.branches.orEmpty()
                fun canonical(ref: String) = branches.firstOrNull { it == ref } ?: branches.singleOrNull { it.equals(ref, ignoreCase = true) } ?: ref
                mutable.value = state.value.copy(projects = projects, loading = false,
                    catalogAvailable = connection == BridgeConnection.Connected && catalog != null,
                    branch = if (state.value.branchChoice == BranchChoice.NEW) state.value.branch else canonical(state.value.branch), base = canonical(state.value.base))
            }
        }
    }

    private suspend fun loadCatalog(): List<ProjectChoice>? = try { gateway.projectCatalog() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    fun select(field: NewSessionField) { if (state.value.editable && field in state.value.fields) mutable.value = state.value.copy(field = field) }
    fun move(delta: Int) { val s = state.value; select(s.fields[(s.fields.indexOf(s.field) + delta).coerceIn(0, s.fields.lastIndex)]) }
    fun change(delta: Int) {
        val s = state.value
        if (!s.editable) return
        fun <T> next(items: List<T>, current: T?): T? {
            if (items.isEmpty()) return null
            val index = items.indexOf(current)
            return items[Math.floorMod(if (index < 0) { if (delta >= 0) 0 else items.lastIndex } else index + delta, items.size)]
        }
        mutable.value = when (s.field) {
            NewSessionField.AGENT -> s.copy(agent = next(Agent.entries, s.agent))
            NewSessionField.PROJECT -> {
                val choice = next(s.projects.map { it.name } + "", if (s.newProject) "" else s.project)
                s.copy(project = choice?.takeIf { it.isNotEmpty() }, newProject = choice == "", projectName = "", branchChoice = BranchChoice.CURRENT, branch = "", base = "")
            }
            NewSessionField.BRANCH -> {
                val choices = listOf(BranchChoice.CURRENT to "") + s.selectedProject?.branches.orEmpty().map { BranchChoice.EXISTING to it } + listOf(BranchChoice.NEW to "")
                val value = next(choices, s.branchChoice to if (s.branchChoice == BranchChoice.EXISTING) s.branch else "")!!
                s.copy(branchChoice = value.first, branch = value.second, base = "")
            }
            NewSessionField.BASE -> s.copy(base = next(s.selectedProject?.branches.orEmpty(), s.base).orEmpty())
            NewSessionField.VALIDATION -> s.copy(approvalMode = next(approvalModes, s.approvalMode)!!)
            NewSessionField.MODEL, NewSessionField.THOUGHT -> {
                if (s.optionsLoading) return
                val option = s.option(s.field) ?: return
                val value = next(option.options.map { it.value }, option.currentValue) ?: return
                refreshOptions(mapOf(option.id to value)); return
            }
            NewSessionField.INSTRUCTION -> s
        }.copy(notice = "")
        if (s.field == NewSessionField.AGENT) refreshOptions()
    }
    private fun refreshOptions(values: Map<String, String> = emptyMap()) {
        optionsJob?.cancel()
        val agent = state.value.agent ?: return
        mutable.value = state.value.copy(optionsLoading = true, optionsNotice = "", configOptions = if (values.isEmpty()) emptyList() else state.value.configOptions)
        optionsJob = scope.launch {
            try {
                val options = gateway.agentOptions(agent, values)
                mutable.value = state.value.copy(optionsLoading = false, configOptions = options, optionsNotice = "")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = state.value.copy(optionsLoading = false, configOptions = emptyList(), optionsNotice = "Indisponible · défaut de l’agent") }
        }
    }
    fun editProjectName(text: String) { if (state.value.editable && state.value.newProject) mutable.value = state.value.copy(projectName = text, project = projectFolderName(text), notice = "") }
    fun editInstruction(text: String) { if (state.value.editable) mutable.value = state.value.copy(instruction = text, notice = "") }
    fun editBranch(text: String) { if (state.value.editable && state.value.branchChoice == BranchChoice.NEW) mutable.value = state.value.copy(branch = text, notice = "") }
    fun record() {
        val s = state.value
        if (!s.editable) return
        held = true
        mutable.value = s.copy(capturing = true, partial = "", notice = if (s.dictatingName) "Relâche L2 pour relire le nom." else "Relâche L2 pour relire la consigne.")
        capture.start(listOfNotNull(s.project) + s.selectedProject?.branches.orEmpty())
        recording = scope.launch { capture.state.collect(::applyCapture) }
    }
    fun finish() {
        if (!state.value.capturing || !held) return
        held = false
        mutable.value = state.value.copy(capturing = false, finalizing = true, level = 0f)
        capture.finish(); applyCapture(capture.state.value)
    }
    private fun applyCapture(event: CaptureState) {
        val s = state.value
        if (!s.capturing && !s.finalizing) return
        mutable.value = s.copy(partial = event.text, level = event.level, notice = event.error ?: event.notice ?: s.notice)
        if (held) return
        if (event.complete || event.error != null) {
            val valid = event.error == null && event.text.isNotBlank()
            mutable.value = state.value.copy(capturing = false, finalizing = false, level = 0f,
                instruction = if (valid && !s.dictatingName) event.text.trim() else s.instruction,
                projectName = if (valid && s.dictatingName) event.text.trim().trimEnd('.', '!', '?') else s.projectName,
                project = if (valid && s.dictatingName) projectFolderName(event.text.trim().trimEnd('.', '!', '?')) else s.project,
                field = if (s.dictatingName) NewSessionField.PROJECT else NewSessionField.INSTRUCTION,
                notice = event.error ?: if (valid) "${if (s.dictatingName) "Nom remplacé" else "Consigne remplacée"}. Relis avant A." else "Aucune parole reconnue. Texte conservé.")
            recording?.cancel(); recording = null
        }
    }
    private fun cancelCapture() { recording?.cancel(); recording = null; held = false; capture.cancel() }
    fun back() {
        val s = state.value
        if (s.sending) return
        if (s.capturing || s.finalizing) {
            cancelCapture(); mutable.value = s.copy(capturing = false, finalizing = false, partial = "", level = 0f, notice = "Dictée annulée.")
        } else { job?.cancel(); catalogJob?.cancel(); optionsJob?.cancel(); cancelCapture(); mutable.value = NewSessionForm() }
    }
    fun launch() {
        val s = state.value
        if (!s.canLaunch) return
        mutable.value = s.copy(sending = true, notice = "Lancement…")
        job = scope.launch {
            val catalog = loadCatalog()
            if (catalog == null) {
                mutable.value = state.value.copy(sending = false, catalogAvailable = false, notice = "Projets indisponibles. Vérifie la connexion au pont.")
                return@launch
            }
            val checked = s.copy(projects = catalog)
            if (checked.problem != null) {
                mutable.value = checked.copy(sending = false, notice = "Projet ou branche indisponible. ${checked.problem}")
                return@launch
            }
            val branchInstruction = when (s.branchChoice) {
                BranchChoice.CURRENT -> null
                BranchChoice.EXISTING -> "Place-toi sur la branche existante ${s.branch} avant d’exécuter la consigne. Ne la crée pas si elle est absente ; explique le problème."
                BranchChoice.NEW -> "Crée une nouvelle branche ${s.branch.ifBlank { "au nom de ton choix" }} depuis ${s.base}, puis place-toi dessus avant d’exécuter la consigne. Si le nom existe déjà, demande confirmation."
            }
            val prompt = listOfNotNull(branchInstruction, s.instruction.takeIf { it.isNotBlank() }).joinToString("\n\n").ifBlank { null }
            val request = NewSessionDraft(s.agent!!, s.project!!, instruction = prompt, approvalMode = s.approvalMode, newProject = s.newProject, configValues = s.configValues)
            val result = try { gateway.createSession(request) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { SessionCreation(uncertain = true) }
            if (result.id != null) {
                mutable.value = NewSessionForm(); openSession(result.id)
            } else mutable.value = state.value.copy(sending = false, uncertain = result.uncertain,
                notice = if (result.uncertain) "Création incertaine. Reviens à la régie pour vérifier. Aucun renvoi automatique."
                    else result.error ?: "Session non créée. Vérifie le pont puis réessaie.")
        }
    }
}
