package app.regie.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** New screen, deliberately independent of the session and overview layouts. */
@Composable
fun AgentAuthenticationScreen(
    review: AgentAuthenticationReview,
    bottom: Boolean,
    singleScreen: Boolean,
    select: (Int) -> Unit,
    button: (Button) -> Unit,
    refresh: () -> Unit,
) {
    val controls = bottom || singleScreen
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        BasicText("Connexion des agents", style = sans(18.sp, weight = 650), modifier = Modifier.fillMaxWidth().bottomDivider().padding(horizontal = 20.dp, vertical = 12.dp))
        if (controls) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            review.agents.forEachIndexed { index, agent ->
                Column(Modifier.weight(1f).border(1.dp, if (index == review.selected) Palette.text else Palette.divider, RoundedCornerShape(8.dp)).tappable { select(index) }.padding(12.dp)) {
                    AgentTag(agent.agent)
                    BasicText(connectionLabel(agent), style = sans(12.sp, color = Palette.textMuted), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val current = review.current
            if (current == null) BasicText("Lecture des connexions…", style = sans(15.sp, color = Palette.textMuted))
            else {
                if (!controls) AgentTag(current.agent)
                if (!singleScreen || !current.pending) BasicText(connectionLabel(current), style = sans(20.sp, weight = 600, color = if (current.connected == true) Palette.done else Palette.text))
                current.account?.let { BasicText(it, style = sans(14.sp, color = Palette.textMuted)) }
                val url = current.url
                val code = current.code
                if (url != null && code != null) {
                    if (bottom && !singleScreen) BasicText("Scanne le QR code de l’écran du haut avec ton téléphone, puis saisis le code. Régie attend la confirmation.", style = sans(16.sp, lineHeight = 23.sp))
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        val qr = remember(url) { authenticationQr(url).asImageBitmap() }
                        Image(qr, contentDescription = "QR code de connexion", modifier = Modifier.size(if (singleScreen) 132.dp else 188.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BasicText("Ouvre cette adresse sur ton téléphone", style = sans(13.sp, color = Palette.textMuted))
                            BasicText(url, style = mono(if (singleScreen) 11.sp else 16.sp))
                            BasicText(code, style = mono(if (singleScreen) 28.sp else 40.sp, weight = 600))
                            BasicText("Saisis ce code · attends la confirmation ici", style = sans(12.sp, color = Palette.textMuted))
                        }
                    }
                }
                current.message?.let { BasicText(it, style = sans(15.sp, lineHeight = 22.sp)) }
                if (current.phase == AuthenticationPhase.STARTING) BasicText("Préparation du code…", style = sans(15.sp, color = Palette.textMuted))
                if (current.agent == Agent.CLAUDE && current.connected != true && current.phase != AuthenticationPhase.MANUAL) BasicText("Claude se connecte dans Termux. Choisis Marche à suivre pour les étapes.", style = sans(14.sp, color = Palette.textMuted, lineHeight = 20.sp))
                if (current.agent == Agent.CODEX && current.phase == AuthenticationPhase.DISCONNECTED) BasicText("Connecte ton compte ChatGPT depuis ton téléphone. Si le site le demande, active la connexion par code d’appareil dans les réglages de sécurité ChatGPT.", style = sans(15.sp, lineHeight = 22.sp))
                if (current.connected == true) BasicText("Le compte reste enregistré par l’agent sur la console.", style = sans(14.sp, color = Palette.textMuted))
            }
            if (review.notice.isNotBlank()) BasicText(review.notice, style = sans(14.sp, color = Palette.waiting, lineHeight = 20.sp))
        }
        if (controls) Column(Modifier.fillMaxWidth().topDivider().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("← → Choisir l’agent", style = sans(11.sp, color = Palette.textSubtle))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val current = review.current
                if (!review.busy && current != null && !current.pending) BarAction("A", when {
                    review.confirmingLogout -> "Confirmer la déconnexion"
                    current.connected == true -> "Déconnecter"
                    current.agent == Agent.CLAUDE -> "Marche à suivre"
                    else -> "Connecter"
                }) { button(Button.A) }
                if (review.busy) BasicText("Vérification…", style = sans(13.sp, color = Palette.textMuted))
                if (!review.busy) BarAction("B", if (review.current?.pending == true) "Annuler et fermer" else if (review.confirmingLogout) "Garder le compte" else "Fermer") { button(Button.B) }
            }
            if (!review.busy) BarAction("Y", "Actualiser", refresh)
        }
    }
}

private fun connectionLabel(agent: AgentAuthentication): String = when {
    agent.connected == true -> "Connecté"
    agent.pending -> "Connexion en cours"
    agent.connected == false -> "Non connecté"
    else -> "État inconnu"
}

/** QR contains the public verification URL only, with the default four-module quiet zone. */
internal fun authenticationQr(url: String): Bitmap {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 384, 384)
    val pixels = IntArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
