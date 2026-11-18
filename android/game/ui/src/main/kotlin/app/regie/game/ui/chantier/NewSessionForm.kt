package app.regie.game.ui.chantier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.BranchChoice
import app.regie.domain.model.NewSessionField
import app.regie.domain.model.NewSessionForm
import app.regie.domain.model.approvalModeHelp
import app.regie.game.ui.R
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyChipColors
import app.regie.game.ui.kit.KeyOrder
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import app.regie.game.ui.house.PadAction
import app.regie.game.ui.house.PadActionHint

/** Les touches de la fiche : la manette, ou le doigt sur l'écran du bas. */
enum class BuildKey { A, B, UP, DOWN, LEFT, RIGHT }

/** Ce que la fiche renvoie à l'app : les mêmes gestes que la fiche classique. */
interface BuildCallbacks {
    fun onKey(key: BuildKey)

    /** L2 tenu au doigt : dicter la consigne, ou le nom du nouveau projet. */
    fun onHold(held: Boolean)

    /** Un champ touché devient le champ choisi. */
    fun onField(field: NewSessionField)

    fun onInstruction(text: String)

    fun onBranch(text: String)

    fun onProjectName(text: String)
}

/**
 * La fiche de nouvelle session dans le jeu (maquette 10), sur l'écran du bas, ou seule sur l'écran en
 * mode un seul écran ([compact]). Mêmes champs et mêmes gestes que la fiche classique : l'agent parmi
 * ceux que le pont annonce, le projet ou un nouveau projet, la branche (et sa base), le mode, le modèle,
 * la réflexion et la consigne, que L2 dicte. La croix choisit (haut, bas) et change (gauche, droite) ;
 * A bâtit ; B annule. Une fiche pré-remplie par R2 rappelle la phrase entendue.
 *
 * Les libellés et les valeurs viennent du domaine ([NewSessionForm]), comme dans la fiche classique.
 */
@Composable
fun NewSessionBottomScreen(
    form: NewSessionForm,
    callbacks: BuildCallbacks,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    scrollVelocity: StateFlow<Float> = MutableStateFlow(0f),
) {
    val metrics = Game.metrics
    val colors = Game.colors
    val scroll = rememberScrollState()
    // Le stick droit fait défiler la fiche (ADR 13) : une longue consigne se relit jusqu'au bout avant A.
    LaunchedEffect(scroll, scrollVelocity) {
        scrollVelocity.collectLatest { velocity ->
            if (velocity == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                scroll.scrollBy(velocity * STICK_PX * (frame - last) / 1e9f)
                last = frame
            }
        }
    }
    Column(modifier.fillMaxSize().background(colors.paper).testTag("batir-fiche")) {
        Row(
            Modifier.fillMaxWidth().padding(start = metrics.dp(44), end = metrics.dp(44), top = metrics.dp(if (compact) 20 else 32), bottom = metrics.dp(8)),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(24)),
        ) {
            BasicText(stringResource(R.string.batir_title), Modifier.weight(1f), style = GameText.display(if (compact) 52 else 64), maxLines = 1)
            if (form.heard.isNotEmpty()) BasicText(stringResource(R.string.batir_from_voice), style = GameText.body(26, colors.muted), maxLines = 1)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = metrics.dp(36), vertical = metrics.dp(8)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(14)),
        ) {
            if (form.heard.isNotEmpty()) {
                BasicText("« ${form.heard} »", style = GameText.body(26, colors.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val grid = form.fields.filter { it != NewSessionField.INSTRUCTION }
            grid.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
                    pair.forEach { field -> FieldBox(form, field, callbacks, Modifier.weight(1f)) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            if (NewSessionField.INSTRUCTION in form.fields) InstructionBox(form, callbacks)
            Editors(form, callbacks)
            if (!form.newProject) NewProjectRow()
            Notices(form)
        }
        HintBar(form, callbacks, compact)
    }
}

/** La valeur d'un champ, comme la fiche classique l'écrit. */
@Composable
fun fieldValue(form: NewSessionForm, field: NewSessionField): String = when (field) {
    NewSessionField.AGENT -> form.agent?.label ?: form.agents.joinToString(" · ") { it.label }.ifEmpty { "À choisir · ← →" }
    NewSessionField.PROJECT -> form.projectLabel
    NewSessionField.BRANCH -> form.branchLabel
    NewSessionField.BASE -> form.base.ifEmpty { "À choisir" }
    NewSessionField.VALIDATION -> form.modeLabel
    NewSessionField.MODEL, NewSessionField.THOUGHT -> form.optionLabel(field)
    NewSessionField.INSTRUCTION ->
        if ((form.capturing || form.finalizing) && !form.dictatingName) form.partial.ifEmpty { "…" }
        else form.instruction.ifEmpty { stringResource(R.string.batir_instruction_empty) }
}

@Composable
private fun FieldBox(form: NewSessionForm, field: NewSessionField, callbacks: BuildCallbacks, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val on = form.field == field
    PixelFrame(
        modifier.semantics { selected = on }.testTag("batir-champ-${field.name}")
            .clickable(enabled = form.editable, interactionSource = null, indication = null) { callbacks.onField(field) },
        colors = if (on) FrameDefaults.focus() else FrameDefaults.panel(),
        contentPadding = PaddingValues(horizontal = metrics.dp(18), vertical = metrics.dp(10)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(2))) {
            BasicText(field.label, style = GameText.body(22, colors.muted), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                if (on) Arrow(ArrowDirection.LEFT, form.editable) { callbacks.onKey(BuildKey.LEFT) }
                val code = field == NewSessionField.BRANCH || field == NewSessionField.BASE
                BasicText(
                    fieldValue(form, field),
                    Modifier.weight(1f, fill = false),
                    style = if (code) GameText.code(34) else GameText.body(30, weight = 600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (on) Arrow(ArrowDirection.RIGHT, form.editable) { callbacks.onKey(BuildKey.RIGHT) }
            }
            // Le dossier visé : celui que l'agent recevra.
            if (field == NewSessionField.PROJECT) form.project?.let {
                BasicText("~/projects/$it", style = GameText.code(24, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Les flèches du champ choisi : ← → changent sa valeur, au doigt aussi. */
@Composable
private fun Arrow(direction: ArrowDirection, enabled: Boolean, onTap: () -> Unit) {
    PixelArrow(
        direction,
        Modifier.clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onTap),
        color = Game.colors.focus,
    )
}

@Composable
private fun InstructionBox(form: NewSessionForm, callbacks: BuildCallbacks) {
    val metrics = Game.metrics
    val colors = Game.colors
    val on = form.field == NewSessionField.INSTRUCTION
    PixelFrame(
        Modifier.fillMaxWidth().semantics { selected = on }.testTag("batir-champ-INSTRUCTION")
            .clickable(enabled = form.editable, interactionSource = null, indication = null) { callbacks.onField(NewSessionField.INSTRUCTION) },
        colors = if (on) FrameDefaults.focus() else FrameDefaults.panel(),
        contentPadding = PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(14)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
            BasicText(NewSessionField.INSTRUCTION.label, style = GameText.body(22, colors.muted), maxLines = 1)
            if (on && form.editable && !form.dictatingName) {
                // Au doigt, la consigne s'écrit aussi ; la dictée (L2) la remplace.
                BasicTextField(
                    form.instruction,
                    callbacks::onInstruction,
                    Modifier.fillMaxWidth().testTag("instruction"),
                    // Entière : le stick fait défiler la fiche jusqu'à sa dernière ligne.
                    textStyle = GameText.body(30, lineHeightPercent = 130),
                )
            } else {
                BasicText(
                    fieldValue(form, NewSessionField.INSTRUCTION),
                    style = GameText.body(30, lineHeightPercent = 130),
                    maxLines = if (on) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Les champs à écrire : le nom du nouveau projet, le nom d'une nouvelle branche. */
@Composable
private fun Editors(form: NewSessionForm, callbacks: BuildCallbacks) {
    if (form.dictatingName) TextEditor(stringResource(R.string.batir_project_name_hint), form.projectName, form.editable, callbacks::onProjectName, "project-name")
    if (form.field == NewSessionField.BRANCH && form.branchChoice == BranchChoice.NEW) {
        TextEditor(stringResource(R.string.batir_branch_name_hint), form.branch, form.editable, callbacks::onBranch, "branch-name")
    }
}

@Composable
private fun TextEditor(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit, tag: String) {
    val metrics = Game.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
        BasicText(label, style = GameText.body(24, Game.colors.muted), maxLines = 1)
        PixelFrame(Modifier.fillMaxWidth(), colors = FrameDefaults.paper(), contentPadding = PaddingValues(horizontal = metrics.dp(18), vertical = metrics.dp(10))) {
            BasicTextField(value, onChange, Modifier.fillMaxWidth().testTag(tag), enabled = enabled, singleLine = true, textStyle = GameText.code(34))
        }
    }
}

/** Le nouveau projet est le dernier choix du champ Projet : la fiche le rappelle (maquette 10). */
@Composable
private fun NewProjectRow() {
    val metrics = Game.metrics
    val colors = Game.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = metrics.dp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(16)),
    ) {
        BasicText("+", style = GameText.body(44, colors.line, weight = 700))
        BasicText(stringResource(R.string.batir_new_project), Modifier.weight(1f), style = GameText.body(26), maxLines = 1)
        BasicText(stringResource(R.string.batir_new_project_how), style = GameText.body(22, colors.muted), maxLines = 1)
    }
}

/** La dictée en cours, l'aide du mode choisi, puis ce qui manque ou ce qui s'est passé. */
@Composable
private fun Notices(form: NewSessionForm) {
    val colors = Game.colors
    if (form.capturing || form.finalizing) {
        val text = if (form.capturing) {
            stringResource(R.string.batir_level, (form.level * 100).toInt()) + " · " + form.partial.ifBlank { stringResource(R.string.batir_listening) }
        } else {
            stringResource(R.string.batir_transcribing)
        }
        BasicText(text, style = GameText.body(26, colors.focus), maxLines = 2)
    }
    if (form.field == NewSessionField.VALIDATION) BasicText(approvalModeHelp(form.approvalMode), style = GameText.body(24, colors.muted), maxLines = 2)
    if (form.feedback.isNotBlank()) {
        BasicText(form.feedback, Modifier.testTag("batir-avis"), style = GameText.body(26, colors.muted, weight = 600), maxLines = 2)
    }
}

/** A Bâtir, B Annuler, la croix pour les champs, L2 tenu pour dicter : touchables aussi. */
@Composable
private fun HintBar(form: NewSessionForm, callbacks: BuildCallbacks, compact: Boolean) {
    val colors = Game.colors
    val metrics = Game.metrics
    val chip = KeyChipColors(colors.keyBackground, colors.keyInk)
    val launch = stringResource(if (form.sending) R.string.batir_sending else R.string.batir_launch)
    val dictate = stringResource(if (form.dictatingName) R.string.batir_dictate_name else R.string.batir_dictate_instruction)
    Row(
        Modifier.fillMaxWidth().height(metrics.dp(96)).background(colors.bar).padding(horizontal = metrics.dp(if (compact) 28 else 40)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(30)),
    ) {
        val hints = listOf("A", "B", "L2", "Croix").sortedBy { KeyOrder.rank(it) }
        hints.forEach { key ->
            when (key) {
                // Bâtir n'est proposé que si la fiche est complète ; sinon l'avis dit ce qui manque.
                "A" -> PadActionHint(
                    PadAction(listOf("A"), launch, onTap = { callbacks.onKey(BuildKey.A) }.takeIf { form.canLaunch }),
                    Modifier.testTag("batir-lancer").semantics { selected = form.canLaunch },
                    chip = chip, ink = if (form.canLaunch) colors.barInk else colors.muted, textSizePx = 26,
                )
                "B" -> if (!form.sending) PadActionHint(PadAction("B", stringResource(R.string.batir_cancel)) { callbacks.onKey(BuildKey.B) }, chip = chip, ink = colors.barInk, textSizePx = 26)
                "L2" -> PadActionHint(
                    PadAction(listOf("L2"), dictate, onHold = { held -> if (!held || form.editable) callbacks.onHold(held) }),
                    chip = chip, ink = colors.barInk, textSizePx = 26, active = form.capturing,
                )
                else -> PadActionHint(PadAction("Croix", stringResource(R.string.batir_fields)) { callbacks.onKey(BuildKey.DOWN) }, chip = chip, ink = colors.barInk, textSizePx = 26)
            }
        }
    }
}

/** Vitesse du stick droit à fond, en pixels par seconde, comme le tableau de liège. */
private const val STICK_PX = 1600f
