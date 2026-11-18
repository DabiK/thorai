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
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
    submitKey: (String) -> Unit = {},
    bindKeyAction: ((() -> Unit)?) -> Unit = {},
) {
    val controls = bottom || singleScreen
    val tabs = rememberLazyListState()
    LaunchedEffect(review.selected, review.agents.size, controls) {
        if (controls && review.agents.isNotEmpty()) tabs.scrollToItem(review.selected.coerceIn(review.agents.indices))
    }
    var key by remember(review.visible, review.current?.agent) { mutableStateOf("") }
    val sendKey = { if (key.isNotBlank() && !review.busy) { val entered = key; key = ""; submitKey(entered) } }
    DisposableEffect(key, review.busy, review.current?.agent, controls) {
        if (controls) bindKeyAction(sendKey)
        onDispose { if (controls) bindKeyAction(null) }
    }
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        BasicText("Connexion des agents", style = sans(18.sp, weight = 650), modifier = Modifier.fillMaxWidth().bottomDivider().padding(horizontal = 20.dp, vertical = 12.dp))
        if (controls) LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), state = tabs, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(review.agents, key = { _, agent -> agent.agent.id }) { index, agent ->
                Column(Modifier.widthIn(min = 140.dp).border(1.dp, if (index == review.selected) Palette.text else Palette.divider, RoundedCornerShape(8.dp)).tappable { select(index) }.padding(12.dp)) {
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
                current.message?.takeIf { it != current.login.help }?.let { BasicText(it, style = sans(15.sp, lineHeight = 22.sp)) }
                if (current.phase == AuthenticationPhase.STARTING) BasicText("Préparation du code…", style = sans(15.sp, color = Palette.textMuted))
                if (current.connected != true && current.login.help.isNotEmpty()) BasicText(current.login.help, style = sans(14.sp, color = Palette.textMuted, lineHeight = 20.sp))
                if (controls && current.login.method == "api_key" && current.connected != true) {
                    BasicText(current.login.label, style = sans(14.sp, weight = 600))
                    BasicTextField(value = key, onValueChange = { if (!review.busy) key = it }, enabled = !review.busy,
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        textStyle = mono(16.sp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, Palette.divider, RoundedCornerShape(8.dp)).padding(12.dp).semantics { contentDescription = "Clé de connexion" })
                }
                if (current.connected == true) BasicText("Le compte reste enregistré par l’agent sur la console.", style = sans(14.sp, color = Palette.textMuted))
            }
            if (review.notice.isNotBlank()) BasicText(review.notice, style = sans(14.sp, color = Palette.waiting, lineHeight = 20.sp))
        }
        if (controls) Column(Modifier.fillMaxWidth().topDivider().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("← → Choisir l’agent", style = sans(11.sp, color = Palette.textSubtle))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val current = review.current
                if (!review.busy && current?.login?.method == "api_key" && current.connected != true && key.isNotBlank()) BarAction("A", "Enregistrer la clé", sendKey)
                if (!review.busy && current != null && !current.pending && (current.login.method != "api_key" || current.connected == true)) BarAction("A", when {
                    review.confirmingLogout -> "Confirmer la déconnexion"
                    current.connected == true -> "Déconnecter"
                    else -> current.login.label
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
