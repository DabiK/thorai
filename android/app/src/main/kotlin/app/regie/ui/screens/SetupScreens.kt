package app.regie.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.SetupAction
import app.regie.domain.model.SetupChecks
import app.regie.domain.model.SetupCommands
import app.regie.domain.model.SetupPermission
import app.regie.domain.model.SetupReview
import app.regie.domain.model.SetupStep
import app.regie.domain.model.VoiceModel
import app.regie.domain.model.VoiceModelFailure
import app.regie.ui.components.*
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans
import java.util.Locale

/**
 * Le parcours d'accueil (ticket 28). En haut, l'explication de l'étape et le chemin parcouru ; en bas,
 * l'état vérifié et les actions, au doigt comme à la manette. Un seul écran : les deux, l'un sous l'autre.
 */
@Composable
fun SetupScreen(review: SetupReview, bottom: Boolean, singleScreen: Boolean, appId: String, onAction: (SetupAction?) -> Unit,
    scrollVelocity: StateFlow<Float> = MutableStateFlow(0f)) {
    Box(Modifier.fillMaxSize().background(Palette.background).testTag("setup")) {
        when {
            singleScreen -> SingleSetup(review, appId, onAction)
            bottom -> SetupControls(review, onAction)
            else -> Row(Modifier.fillMaxSize()) {
                // Une étape à commandes prend toute la largeur : le chemin se réduit à ses repères.
                val wide = commands(review.step, appId) != null
                StepRail(review, compact = wide, modifier = Modifier.width(if (wide) 56.dp else 236.dp).fillMaxHeight())
                AnimatedContent(review.step, Modifier.weight(1f).fillMaxHeight(), transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                }, label = "étape") { step ->
                    // L'écran du haut n'est pas tactile : le stick droit fait défiler une marche à suivre trop longue.
                    val scroll = rememberScrollState()
                    StickScroll(scroll, scrollVelocity)
                    val dense = commands(step, appId) != null
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = if (dense) 28.dp else 36.dp, end = if (dense) 28.dp else 40.dp, top = if (dense) 16.dp else 28.dp, bottom = 16.dp)) {
                        StepExplanation(step, review.optionalLabel(step), appId, large = true)
                    }
                }
            }
        }
    }
}

/** Le chemin : chaque étape, son repère, l'étape affichée en clair. */
@Composable
private fun StepRail(review: SetupReview, compact: Boolean, modifier: Modifier) {
    Column(modifier.background(Palette.surface).padding(horizontal = 20.dp, vertical = 24.dp)) {
        if (!compact) {
            BasicText("ThorAI", style = sans(18.sp, 650))
            BasicText("Préparer la console", style = sans(13.sp, color = Palette.textMuted), modifier = Modifier.padding(top = 2.dp, bottom = 18.dp))
        } else Spacer(Modifier.height(47.dp))
        SetupStep.entries.forEach { step ->
            val current = step == review.step
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusMark(review.mark(step), size = 16.dp)
                if (!compact) BasicText(railLabel(step), maxLines = 1, overflow = TextOverflow.Ellipsis, style = sans(14.sp, if (current) 650 else 400,
                    when {
                        current -> Palette.text
                        review.checks.isDone(step) -> Palette.textMuted
                        else -> Palette.textSubtle
                    }))
            }
        }
    }
}

/** L'écran du bas : l'étape, ce qui est vérifié, puis les actions. */
@Composable
private fun SetupControls(review: SetupReview, onAction: (SetupAction?) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(review.counter(), style = mono(12.sp, color = Palette.textSubtle))
                BasicText(stepTitle(review.step), style = sans(21.sp, 650), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Refermer sans tout faire : « Configurer », sur l'accueil, reprend où on en est.
            if (review.keys.b != SetupAction.CLOSE && review.keys.y != SetupAction.CLOSE) BasicText("Reprendre plus tard", style = sans(14.sp, color = Palette.textMuted),
                modifier = Modifier.tappable { onAction(SetupAction.CLOSE) }.padding(vertical = 8.dp, horizontal = 4.dp).testTag("setup-later"))
        }
        StepProgress(review, Modifier.padding(horizontal = 20.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepState(review, compact = false)
        }
        SetupActions(review, onAction, compact = false, modifier = Modifier.topDivider().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp))
    }
}

/** Un seul écran : l'explication, l'état et les actions, sans le chemin. */
@Composable
private fun SingleSetup(review: SetupReview, appId: String, onAction: (SetupAction?) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StepProgress(review, Modifier.weight(1f))
            if (review.keys.b != SetupAction.CLOSE && review.keys.y != SetupAction.CLOSE) BasicText("Reprendre plus tard", style = sans(13.sp, color = Palette.textMuted),
                modifier = Modifier.tappable { onAction(SetupAction.CLOSE) }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                StepExplanation(review.step, review.optionalLabel(review.step), appId, large = false)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 24.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepState(review, compact = true)
            }
        }
        SetupActions(review, onAction, compact = true, modifier = Modifier.topDivider().padding(horizontal = 20.dp, vertical = 8.dp))
    }
}

@Composable
private fun StepProgress(review: SetupReview, modifier: Modifier) {
    Row(modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SetupStep.entries.forEach { step ->
            val color = when {
                step == review.step -> Palette.text
                review.checks.isDone(step) -> Palette.done
                step.ordinal < review.step.ordinal -> Palette.keyBorder
                else -> Palette.stepPending
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
        }
    }
}

/** A, la principale, en grand ; B, X, Y en dessous, dans l'ordre de lecture. */
@Composable
private fun SetupActions(review: SetupReview, onAction: (SetupAction?) -> Unit, compact: Boolean, modifier: Modifier) {
    val keys = review.keys
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        val primary = keys.a
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PrimaryAction("A", primary?.let { actionLabel(it, review) } ?: waitingLabel(review), Modifier.weight(1f), enabled = primary != null, compact = true) { onAction(primary) }
                SecondaryKeys(review, onAction)
            }
        } else {
            PrimaryAction("A", primary?.let { actionLabel(it, review) } ?: waitingLabel(review), enabled = primary != null, modifier = Modifier.testTag("setup-primary")) { onAction(primary) }
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                SecondaryKeys(review, onAction)
            }
        }
    }
}

@Composable
private fun SecondaryKeys(review: SetupReview, onAction: (SetupAction?) -> Unit) {
    val keys = review.keys
    listOf("B" to keys.b, "X" to keys.x, "Y" to keys.y).forEach { (key, action) ->
        if (action != null) BarAction(key, actionLabel(action, review), size = 14.sp) { onAction(action) }
    }
}

/** L'explication d'une étape : titre, deux phrases, puis la marche à suivre et ses commandes. */
@Composable
private fun StepExplanation(step: SetupStep, optional: String?, appId: String, large: Boolean) {
    // Avec des commandes, tout doit tenir sur l'écran du haut pendant qu'on tape en bas : plus serré.
    val dense = commands(step, appId) != null
    val roomy = large && !dense
    val body = if (roomy) 18.sp else if (large) 16.sp else 15.sp
    val line = if (roomy) 27.sp else 23.sp
    Column(verticalArrangement = Arrangement.spacedBy(if (roomy) 14.dp else 8.dp)) {
        BasicText(listOfNotNull("Étape ${step.ordinal + 1} sur ${SetupStep.entries.size}", optional).joinToString(" · "), style = mono(12.sp, color = Palette.textSubtle))
        BasicText(stepTitle(step), style = sans(if (roomy) 30.sp else if (large) 26.sp else 22.sp, 650, lineHeight = if (roomy) 36.sp else 30.sp))
        val text = explanation(step)
        // Serré : la première phrase seulement, la marche à suivre dit le reste.
        text.lead.take(if (dense) 1 else text.lead.size).forEachIndexed { index, sentence ->
            BasicText(sentence, style = sans(body, if (index == 0) 500 else 400, if (index == 0) Palette.text else Palette.footerText, lineHeight = line))
        }
        if (text.steps.isNotEmpty()) Column(Modifier.padding(top = if (roomy) 4.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(if (roomy) 8.dp else 4.dp)) {
            text.steps.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("${index + 1}", style = mono(if (large) 15.sp else 13.sp, 600, Palette.textSubtle), modifier = Modifier.width(16.dp))
                    BasicText(item, style = sans(if (roomy) 16.sp else if (large) 15.sp else 14.sp, color = Palette.footerText, lineHeight = if (roomy) 23.sp else 21.sp))
                }
            }
        }
        commands(step, appId)?.let { CommandBlock(it, roomy) }
        // La note d'une étape à commandes passe en bas, sous l'état : les commandes gardent la place.
        if (!dense) text.note?.let { BasicText(it, style = sans(if (large) 14.sp else 13.sp, color = Palette.textMuted, lineHeight = 20.sp)) }
    }
}

/** Le stick droit fait défiler [scroll] à la vitesse demandée, tant qu'il est poussé. */
@Composable
private fun StickScroll(scroll: androidx.compose.foundation.ScrollState, velocity: StateFlow<Float>) {
    val speed = with(LocalDensity.current) { 600.dp.toPx() }
    LaunchedEffect(scroll, velocity) {
        velocity.collectLatest { value ->
            if (value == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                scroll.scrollBy(value * speed * (now - last) / 1e9f)
                last = now
            }
        }
    }
}

/** Des commandes : des données, montrées telles quelles, à recopier ou à coller (Y les copie). */
@Composable
private fun CommandBlock(text: String, roomy: Boolean) {
    BasicText(text, style = mono(if (roomy) 14.sp else 13.sp, color = Palette.text, lineHeight = if (roomy) 21.sp else 18.sp),
        modifier = Modifier.fillMaxWidth().background(Palette.codeBackground, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp).testTag("setup-commands"))
}

/** L'état vérifié de l'étape, ligne par ligne. */
@Composable
private fun StepState(review: SetupReview, compact: Boolean) {
    val checks = review.checks
    when (review.step) {
        SetupStep.WELCOME -> {
            BasicText("Il te faut", style = sans(14.sp, color = Palette.textSubtle))
            StateLine(Mark.NONE, "Termux", "depuis GitHub", compact)
            StateLine(Mark.NONE, "Réseau", "pour le téléchargement", compact)
            StateLine(Mark.NONE, "Un compte", "Claude, ChatGPT ou OpenCode", compact)
        }
        SetupStep.TERMUX -> {
            StateLine(if (checks.termux) Mark.DONE else Mark.WAITING, "Termux", if (checks.termux) "Installé" else "Absent", compact)
            val bridge = checks.bridge == BridgeStatus.Connected
            StateLine(if (bridge) Mark.DONE else Mark.TODO, "Debian et agents", if (bridge) "Prêts" else "Vérifiés à l’étape suivante", compact)
            if (!checks.termux) Hint(explanation(SetupStep.TERMUX).note.orEmpty())
        }
        SetupStep.BRIDGE -> {
            StateLine(permissionMark(checks, SetupPermission.RUN_COMMAND, checks.runCommand), "Commandes Termux", permissionText(checks, SetupPermission.RUN_COMMAND, checks.runCommand, "Autorisées", "À autoriser"), compact)
            val (mark, value) = when (val bridge = checks.bridge) {
                BridgeStatus.Connected -> Mark.DONE to "Joignable"
                BridgeStatus.Connecting -> (if (checks.runCommand) Mark.WAITING else Mark.TODO) to "Connexion…"
                is BridgeStatus.Unreachable -> (if (bridge.hint == BridgeHint.RESTARTING) Mark.WAITING else Mark.TODO) to "Injoignable"
            }
            StateLine(mark, "Pont", value, compact)
            (checks.bridge as? BridgeStatus.Unreachable)?.takeIf { checks.runCommand }?.let { Hint(bridgeHint(it.hint)) }
        }
        SetupStep.VOICE -> {
            StateLine(permissionMark(checks, SetupPermission.MICROPHONE, checks.microphone), "Micro", permissionText(checks, SetupPermission.MICROPHONE, checks.microphone, "Autorisé", "À autoriser"), compact)
            when (val model = checks.voiceModel) {
                VoiceModel.Installed -> StateLine(Mark.DONE, "Modèle de voix", "Installé", compact)
                VoiceModel.NotNeeded -> StateLine(Mark.DONE, "Voix", "ElevenLabs", compact)
                is VoiceModel.Missing -> {
                    StateLine(Mark.TODO, "Modèle de voix", "${megabytes(model.downloadBytes)} à télécharger", compact)
                    Hint("Il faut ${gigabytes(model.freeBytes)} libres. Une seule fois.")
                }
                is VoiceModel.Downloading -> {
                    StateLine(Mark.WAITING, "Modèle de voix", model.progress?.let { "${(it * 100).toInt()} %" } ?: "Chargement…", compact)
                    ProgressLine(model.progress, Modifier.padding(start = 30.dp).testTag("voice-progress"))
                    Hint("Le téléchargement continue si tu avances.")
                }
                is VoiceModel.Failed -> {
                    StateLine(Mark.WAITING, "Modèle de voix", "Interrompu", compact)
                    Hint(when (model.failure) {
                        VoiceModelFailure.OFFLINE -> "Pas de réseau. Réessaie une fois connecté."
                        VoiceModelFailure.STORAGE -> "Espace insuffisant. Libère de la place, puis réessaie."
                        VoiceModelFailure.OTHER -> "Téléchargement interrompu. Réessaie."
                    }, Palette.waiting)
                }
            }
        }
        SetupStep.NOTIFICATIONS ->
            StateLine(permissionMark(checks, SetupPermission.NOTIFICATIONS, checks.notifications), "Notifications", permissionText(checks, SetupPermission.NOTIFICATIONS, checks.notifications, "Autorisées", "À autoriser"), compact)
        SetupStep.CONSOLE_MODE ->
            StateLine(if (checks.consoleMode) Mark.DONE else Mark.TODO, "Mode console", if (checks.consoleMode) "Activé" else "Désactivé", compact)
        SetupStep.ANDROID_ACCESS -> {
            StateLine(if (checks.androidAccess) Mark.DONE else Mark.TODO, "Reconnexion Android", if (checks.androidAccess) "Autorisée" else "À autoriser", compact)
            StateLine(if (checks.wirelessDebugging) Mark.DONE else Mark.TODO, "Débogage sans fil", if (checks.wirelessDebugging) "Activé" else "Coupé", compact)
            if (!checks.androidAccess) Hint(explanation(SetupStep.ANDROID_ACCESS).note.orEmpty())
            if (review.copied) Hint("Commandes copiées. Colle-les dans Termux.", Palette.done)
        }
        SetupStep.AGENTS -> {
            val agents = checks.agents
            when {
                checks.bridge != BridgeStatus.Connected -> Hint("Le pont doit être joignable. Reviens à l’étape 3.", Palette.waiting)
                agents == null -> Hint("Lecture des comptes…")
                agents.isEmpty() -> Hint("Aucun agent annoncé par le pont.")
                else -> agents.forEach { agent ->
                    StateLine(when (agent.connected) { true -> Mark.DONE; null -> Mark.WAITING; false -> Mark.TODO }, agent.agent.label,
                        when (agent.connected) { true -> "Connecté"; false -> "Non connecté"; null -> "État inconnu" }, compact)
                }
            }
        }
        SetupStep.FINISH -> {
            // Le bilan tient en une ligne ; seules les étapes restantes sont détaillées.
            val steps = SetupStep.entries.filter { it != SetupStep.WELCOME && it != SetupStep.FINISH }
            val left = steps.filterNot(checks::isDone)
            StateLine(if (left.isEmpty()) Mark.DONE else Mark.NONE, "Étapes prêtes", "${steps.size - left.size} sur ${steps.size}", compact)
            left.forEach { step -> StateLine(Mark.TODO, railLabel(step), if (step.optional) "Passée" else "À faire", compact = true) }
        }
    }
    if (review.copied && review.step == SetupStep.TERMUX) Hint("Commandes copiées. Colle-les dans Termux.", Palette.done)
}

@Composable
private fun StateLine(mark: Mark, label: String, value: String, compact: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 26.dp else 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusMark(mark, size = if (compact) 16.dp else 18.dp)
        BasicText(label, style = sans(if (compact) 14.sp else 16.sp, 500), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(value, maxLines = 1, style = sans(if (compact) 14.sp else 16.sp, color = when (mark) {
            Mark.DONE -> Palette.done
            Mark.WAITING -> Palette.waiting
            Mark.TODO, Mark.NONE -> Palette.textMuted
        }))
    }
}

@Composable
private fun Hint(text: String, color: Color = Palette.textMuted) {
    BasicText(text, style = sans(14.sp, color = color, lineHeight = 20.sp), modifier = Modifier.padding(start = 30.dp))
}

private fun SetupReview.counter() = listOfNotNull("Étape $position sur $count", optionalLabel(step)).joinToString(" · ")

private fun SetupReview.optionalLabel(step: SetupStep) = "facultative".takeIf { step.optional }

private fun SetupReview.mark(step: SetupStep) = when {
    checks.isDone(step) && step != SetupStep.WELCOME -> Mark.DONE
    step == SetupStep.WELCOME && this.step != SetupStep.WELCOME -> Mark.DONE
    step == this.step -> Mark.WAITING
    else -> Mark.TODO
}

private fun permissionMark(checks: SetupChecks, permission: SetupPermission, granted: Boolean) = when {
    granted -> Mark.DONE
    permission in checks.refused -> Mark.WAITING
    else -> Mark.TODO
}

private fun permissionText(checks: SetupChecks, permission: SetupPermission, granted: Boolean, yes: String, ask: String) = when {
    granted -> yes
    permission in checks.refused -> "Refusé"
    else -> ask
}

/** Ce que A affiche quand l'étape attend sans rien demander (le pont démarre). */
private fun waitingLabel(review: SetupReview) = when (review.step) {
    SetupStep.BRIDGE -> "Connexion au pont…"
    SetupStep.AGENTS -> "En attente du pont"
    else -> "En attente"
}

private fun actionLabel(action: SetupAction, review: SetupReview): String = when (action) {
    SetupAction.NEXT -> if (review.step == SetupStep.WELCOME) "Commencer" else "Continuer"
    SetupAction.BACK -> "Revenir"
    SetupAction.SKIP -> "Passer"
    SetupAction.CLOSE -> if (review.step == SetupStep.FINISH) "Aller à l’accueil" else "Reprendre plus tard"
    SetupAction.OPEN_TERMUX_RELEASES -> "Ouvrir la page de Termux"
    SetupAction.COPY_INSTALL_COMMANDS, SetupAction.COPY_PAIRING_COMMANDS -> if (review.checks.termux) "Copier et ouvrir Termux" else "Copier les commandes"
    SetupAction.GRANT_RUN_COMMAND -> "Autoriser les commandes Termux"
    SetupAction.RETRY_BRIDGE -> "Relancer le pont"
    SetupAction.GRANT_MICROPHONE -> "Autoriser le micro"
    SetupAction.DOWNLOAD_VOICE -> if (review.checks.voiceModel is VoiceModel.Failed) "Réessayer le téléchargement" else "Télécharger le modèle"
    SetupAction.GRANT_NOTIFICATIONS -> "Autoriser les notifications"
    SetupAction.OPEN_ACCESSIBILITY -> "Ouvrir Accessibilité"
    SetupAction.OPEN_APP_INFO -> "Ouvrir les infos de l’appli"
    SetupAction.OPEN_DEVELOPER_OPTIONS -> "Ouvrir les options développeur"
    SetupAction.OPEN_AGENTS -> if (review.done) "Gérer les comptes" else "Connecter les agents"
    SetupAction.CREATE_SESSION -> "Créer une session"
}

private fun railLabel(step: SetupStep) = when (step) {
    SetupStep.WELCOME -> "Bienvenue"
    SetupStep.TERMUX -> "Termux et Debian"
    SetupStep.BRIDGE -> "Pont"
    SetupStep.VOICE -> "Voix"
    SetupStep.NOTIFICATIONS -> "Notifications"
    SetupStep.CONSOLE_MODE -> "Mode console"
    SetupStep.ANDROID_ACCESS -> "Accès à Android"
    SetupStep.AGENTS -> "Comptes des agents"
    SetupStep.FINISH -> "Première session"
}

private fun stepTitle(step: SetupStep) = when (step) {
    SetupStep.WELCOME -> "Bienvenue dans ThorAI"
    SetupStep.TERMUX -> "Termux et Debian"
    SetupStep.BRIDGE -> "Le pont"
    SetupStep.VOICE -> "La voix"
    SetupStep.NOTIFICATIONS -> "Les notifications"
    SetupStep.CONSOLE_MODE -> "Le mode console"
    SetupStep.ANDROID_ACCESS -> "L’accès des agents à Android"
    SetupStep.AGENTS -> "Les comptes des agents"
    SetupStep.FINISH -> "C’est prêt"
}

private class Explanation(val lead: List<String>, val steps: List<String> = emptyList(), val note: String? = null)

/** Huit mots au plus par phrase (`docs/textes.md`). Les commandes et chemins sont des données. */
private fun explanation(step: SetupStep) = when (step) {
    SetupStep.WELCOME -> Explanation(
        listOf("ThorAI pilote Claude Code, Codex et opencode.", "Tu parles, l’agent agit, tu valides."),
        note = "Ce parcours prépare la console, étape par étape. Chaque étape se vérifie seule. X passe les étapes facultatives.",
    )
    SetupStep.TERMUX -> Explanation(
        listOf("Les agents tournent dans une Debian, sous Termux.", "Une commande installe tout, une seule fois."),
        listOf("Installe Termux depuis ses releases GitHub.", "Ouvre Termux et attends sa préparation.", "Y copie ces commandes : colle-les dans Termux."),
        note = "Sans Termux, A ouvre sa page en bas.",
    )
    SetupStep.BRIDGE -> Explanation(
        listOf("ThorAI parle aux agents par un pont local.", "Termux le démarre quand ThorAI le demande."),
        listOf("Autorise ThorAI à lancer des commandes Termux.", "ThorAI confie une clé au pont.", "Le pont répond : l’étape passe au vert."),
        note = "La clé ne quitte jamais la console.",
    )
    SetupStep.VOICE -> Explanation(
        listOf("Maintiens L2 et parle : ThorAI écrit.", "La transcription tourne sur la console, hors ligne."),
        listOf("Autorise le micro.", "Télécharge le modèle de voix, une seule fois."),
        note = "Rien ne part sans un appui sur A.",
    )
    SetupStep.NOTIFICATIONS -> Explanation(
        listOf("ThorAI te prévient quand un agent t’attend.", "Une validation, une question, une fin, un échec."),
        note = "Le reste se consulte dans l’app.",
    )
    SetupStep.CONSOLE_MODE -> Explanation(
        listOf("L2 et R2 marchent depuis toute appli.", "Les messages de l’agent passent par-dessus l’écran."),
        listOf("Ouvre Accessibilité, sur l’écran du bas.", "Choisis « ThorAI, mode console », puis active-le.", "Bloqué ? Infos de l’appli › ⋮ › Autoriser les paramètres restreints."),
        note = "ThorAI lit seulement la manette, jamais l’écran.",
    )
    SetupStep.ANDROID_ACCESS -> Explanation(
        listOf("Les agents voient et pilotent les deux écrans.", "Ils passent par le débogage sans fil."),
        listOf("En haut\u00a0: Débogage sans fil › Associer avec un code.", "Y copie les commandes, ouvre Termux en bas.", "Colle-les, puis saisis le port et le code."),
        note = "Le script doit répondre THOR_ANDROID=ok. ThorAI rallumera ensuite le débogage sans fil.",
    )
    SetupStep.AGENTS -> Explanation(
        listOf("Connecte au moins un agent pour travailler.", "Chaque compte reste dans les fichiers de l’agent."),
        listOf("Codex : un code à saisir sur ton téléphone.", "Claude : claude auth login, dans la Debian.", "opencode : ta clé OpenCode Go."),
    )
    SetupStep.FINISH -> Explanation(
        listOf("Crée ta première session avec A.", "Maintiens ensuite L2 pour parler à l’agent."),
        note = "Configurer, sur l’accueil, rouvre ce parcours.",
    )
}

private fun commands(step: SetupStep, appId: String) = when (step) {
    SetupStep.TERMUX -> SetupCommands.INSTALL
    SetupStep.ANDROID_ACCESS -> SetupCommands.pairing(appId)
    else -> null
}

private fun bridgeHint(hint: BridgeHint) = when (hint) {
    BridgeHint.START_EXTERNAL -> "Démarre le pont sur le Mac."
    BridgeHint.GRANT_RUN_COMMAND -> "Autorise les commandes Termux."
    BridgeHint.TERMUX_MISSING -> "Termux ne répond pas. Relance l’installeur dans Termux."
    BridgeHint.TOKEN_MISSING, BridgeHint.TOKEN_REFUSED -> "Clé refusée. Quitte ThorAI, puis rouvre-le."
    BridgeHint.RESTARTING -> "Démarrage du pont, quelques secondes…"
}

private fun megabytes(bytes: Long) = "${(bytes + 500_000) / 1_000_000} Mo"

private fun gigabytes(bytes: Long) = String.format(Locale.FRANCE, "%.1f Go", bytes / 1e9)
