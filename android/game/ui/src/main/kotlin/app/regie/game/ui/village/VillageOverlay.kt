package app.regie.game.ui.village

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.regie.domain.model.SessionId
import app.regie.game.engine.village.House
import app.regie.game.engine.village.Village
import app.regie.game.engine.world.Placed
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.ResolvedSprite
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.Blink
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.SignPanel
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.GroundPainter
import app.regie.game.ui.render.WorldProjection
import app.regie.game.ui.theme.Game

/** L'état de la session de chaque maison, pour [app.regie.game.ui.render.PixelWorld] (`states`). */
fun Village.houseStates(): Map<Placed, TagState> = houses.associate { it.placed to TagState.of(it.session.status) }

/**
 * Où accrocher ce qui désigne une maison, en pixels du dessin :
 * - [bubble] : le pied de la bulle d'état, au-dessus du toit, du côté opposé à la cheminée ;
 * - [doorstep] : le milieu du bas de la tuile d'entrée, là où pointe le panneau de la maison ;
 * - [top] et [bottom] : le haut et le bas de l'image de la maison.
 */
data class HouseMark(val house: House, val bubble: ArtPoint, val doorstep: ArtPoint, val top: Int, val bottom: Int)

/** Le calcul des [HouseMark], d'après l'image de chaque maison dans l'atlas (ou son empreinte sans atlas). */
object HouseMarks {
    /** La bulle mord un peu sur le toit, pour qu'on voie à quelle maison elle appartient. */
    private const val BUBBLE_OVERLAP = 3

    /** La pointe du panneau entre un peu dans la tuile d'entrée, sous les pieds du coursier. */
    private const val DOORSTEP_INSET = 3

    /** Une maison encore en chantier (ticket 48) n'a ni bulle ni panneau : le chantier a les siens. */
    fun of(village: Village, assets: GameAssets): List<HouseMark> = village.houses.filterNot(village::isUnderConstruction).map { of(it, assets) }

    fun of(house: House, assets: GameAssets): HouseMark {
        val tile = GroundPainter.TILE
        val footX = house.footprint.left * tile + house.footprint.width * tile / 2
        val footY = (house.footprint.bottom + 1) * tile - 1
        val frame = assets.resolve(house.modelId)?.frame
        val doorstep = ArtPoint(house.entry.x * tile + tile / 2, (house.entry.y + 1) * tile - DOORSTEP_INSET)
        if (frame == null) {
            val top = house.footprint.top * tile - tile
            return HouseMark(house, ArtPoint(footX, top + BUBBLE_OVERLAP), doorstep, top, footY)
        }
        val left = footX - frame.ax
        val top = footY - frame.ay
        val chimney = assets.objectSpec(house.modelId)?.chimney
        // Du côté opposé à la cheminée, au tiers de la largeur ; au milieu sans cheminée.
        val column = when {
            chimney == null -> frame.w / 2
            chimney.x < frame.w / 2 -> frame.w * 2 / 3
            else -> frame.w / 3
        }
        // Posée sur la ligne du toit à cet endroit, pas en l'air au niveau de la cheminée.
        val sprite = assets.resolve(house.modelId)
        val roof = sprite?.atlas?.columnTop(sprite.frameId, column) ?: 0
        return HouseMark(house, ArtPoint(left + column, top + roof + BUBBLE_OVERLAP), doorstep, top, top + frame.h)
    }
}

/**
 * Ce qui se lit au-dessus du hameau, dans l'`overlay` de [app.regie.game.ui.render.PixelWorld]
 * (règle du ticket 52, reprise par le ticket 45) :
 * - **une petite bulle d'état au-dessus de chaque maison**, dessinée au pixel avec les images de l'atlas
 *   `icones` : points animés au travail, « ! » sur fond ambre qui sautille quand une validation attend,
 *   « z » au repos ou arrêtée, « ! » rouge sur fond gris en échec. Elle se tient du côté opposé à la
 *   cheminée et reste dans l'écran tant que la maison y est ;
 * - **le panneau complet** (projet, session, état) **pour la seule maison [selected]** : celle devant
 *   laquelle se tient le coursier ([app.regie.game.engine.village.nearestHouse]) ou qu'un raccourci
 *   désigne. Il pend sous sa porte, la pointe vers le seuil ; s'il n'y a pas la place en bas, il passe
 *   au-dessus du toit, pointe vers le bas. Il est toujours gardé entier dans l'écran, sa pointe restant
 *   en face de la maison.
 *
 * Aucun panneau n'est posé sur un toit ou une cheminée, ni coupé par le bord. Les positions sont lues
 * au placement et au dessin : la caméra peut suivre le coursier sans recomposer.
 */
@Composable
fun VillageOverlay(
    village: Village,
    assets: GameAssets,
    projection: WorldProjection,
    modifier: Modifier = Modifier,
    selected: SessionId? = null,
    clock: State<Long> = Game.clock,
) {
    val marks = remember(village, assets) { HouseMarks.of(village, assets) }
    val bubbles = remember(assets) { StateBubbles.of(assets) }
    val states = remember(marks) { marks.map { TagState.of(it.house.session.status) }.toTypedArray() }
    Canvas(modifier.fillMaxSize()) {
        if (bubbles == null) return@Canvas
        val scale = projection.scale
        val origin = projection.origin
        val viewWidth = size.width.toInt() / scale
        val viewHeight = size.height.toInt() / scale
        val time = clock.value
        for (i in marks.indices) {
            val mark = marks[i]
            val bubble = bubbles.of(states[i], time)
            val f = bubble.frame
            // La bulle se montre là où elle tombe ; si le toit sort par le haut, elle descend au bord
            // tant que la maison est dans l'écran.
            val left = mark.bubble.x - f.ax - origin.x
            if (left + f.w < 0 || left > viewWidth) continue
            val natural = mark.bubble.y - f.ay - origin.y
            if (natural > viewHeight - f.h || mark.bottom - origin.y < f.h) continue
            val bob = if (states[i] == TagState.WAITING && Blink.bobUp(time)) 1 else 0
            val top = maxOf(1, natural - bob)
            drawImage(
                bubbles.image(states[i], bubble),
                srcOffset = bubbles.srcOffset(states[i], bubble),
                srcSize = IntSize(f.w, f.h),
                dstOffset = IntOffset(left.coerceIn(1, maxOf(1, viewWidth - f.w - 1)) * scale, top * scale),
                dstSize = IntSize(f.w * scale, f.h * scale),
                filterQuality = FilterQuality.None,
            )
        }
    }
    val house = selected?.let { id -> marks.firstOrNull { it.house.session.id == id } } ?: return
    val metrics = Game.metrics
    Layout(
        content = {
            SignPanel(
                house.house.session.project,
                subtitle = house.house.session.title,
                state = TagState.of(house.house.session.status),
                // La bulle de la maison sautille déjà ; le panneau reste immobile pour se lire.
                bob = false,
                pointer = false,
            )
            PixelArrow(ArrowDirection.UP, rows = ARROW_ROWS)
            PixelArrow(ArrowDirection.DOWN, rows = ARROW_ROWS)
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val panel = measurables[0].measure(loose)
        val up = measurables[1].measure(loose)
        val down = measurables[2].measure(loose)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = PANEL_MARGIN * metrics.pixelPx
        layout(width, height) {
            val doorstep = projection.toScreen(house.doorstep)
            val placement = SignPlacement.place(
                anchorX = doorstep.x,
                below = doorstep.y,
                above = projection.toScreenY(house.top) - BUBBLE_CLEARANCE * metrics.pixelPx,
                panelWidth = panel.width,
                panelHeight = panel.height,
                arrowWidth = up.width,
                // La pointe touche le cadre sans qu'il la recouvre.
                arrowHeight = up.height + metrics.pixelPx,
                viewWidth = width,
                viewHeight = height,
                margin = margin,
            )
            panel.place(placement.panelX, placement.panelY)
            (if (placement.pointsUp) up else down).place(placement.arrowX, placement.arrowY)
        }
    }
}

/** Écart minimal entre le panneau et le bord de l'écran, et place laissée à la bulle au-dessus du toit, en pixels du dessin. */
private const val PANEL_MARGIN = 2
private const val BUBBLE_CLEARANCE = 14
private const val ARROW_ROWS = 3

/** Où poser le panneau d'une maison et sa pointe, en pixels de l'écran. */
data class SignPlacement(val panelX: Int, val panelY: Int, val arrowX: Int, val arrowY: Int, val pointsUp: Boolean) {
    companion object {
        /**
         * Sous le seuil [below] si le panneau y tient, sinon au-dessus de [above] (le haut du toit, bulle
         * comprise), sinon contre le bord bas. Le panneau reste entier dans la vue, à [margin] du bord ;
         * la pointe reste en face de [anchorX], dans la largeur du panneau.
         */
        fun place(
            anchorX: Int,
            below: Int,
            above: Int,
            panelWidth: Int,
            panelHeight: Int,
            arrowWidth: Int,
            arrowHeight: Int,
            viewWidth: Int,
            viewHeight: Int,
            margin: Int,
        ): SignPlacement {
            val total = panelHeight + arrowHeight
            val fitsBelow = below + total <= viewHeight - margin
            val fitsAbove = above - total >= margin
            val pointsUp = fitsBelow || !fitsAbove
            val arrowY = if (pointsUp) {
                below.coerceIn(margin, maxOf(margin, viewHeight - margin - total))
            } else {
                above - arrowHeight
            }
            val panelY = if (pointsUp) arrowY + arrowHeight else arrowY - panelHeight
            val panelX = (anchorX - panelWidth / 2).coerceIn(margin, maxOf(margin, viewWidth - margin - panelWidth))
            val inset = arrowWidth / 2
            val arrowX = (anchorX - arrowWidth / 2).coerceIn(panelX + inset, maxOf(panelX + inset, panelX + panelWidth - arrowWidth - inset))
            return SignPlacement(panelX, panelY, arrowX, arrowY, pointsUp)
        }
    }
}

/**
 * Les bulles d'état de l'atlas `icones`, préparées une fois : l'animation de travail, l'attente
 * recolorée en ambre, l'échec en rouge sur gris, le sommeil.
 */
private class StateBubbles(
    private val working: List<ResolvedSprite>,
    private val workingMs: Int,
    private val waiting: ResolvedSprite,
    private val waitingImage: ImageBitmap,
    private val failedImage: ImageBitmap,
    private val sleeping: List<ResolvedSprite>,
    private val sleepingMs: Int,
) {
    fun of(state: TagState, time: Long): ResolvedSprite = when (state) {
        TagState.WORKING -> working[((time / workingMs) % working.size).toInt()]
        TagState.WAITING, TagState.FAILED -> waiting
        TagState.IDLE -> sleeping[((time / sleepingMs) % sleeping.size).toInt()]
    }

    /** L'image où lire [sprite] : l'atlas, ou la bulle recolorée (qu'on lit alors depuis son coin). */
    fun image(state: TagState, sprite: ResolvedSprite): ImageBitmap = when (state) {
        TagState.WAITING -> waitingImage
        TagState.FAILED -> failedImage
        else -> sprite.atlas.image
    }

    fun srcOffset(state: TagState, sprite: ResolvedSprite): IntOffset = when (state) {
        TagState.WAITING, TagState.FAILED -> IntOffset.Zero
        else -> IntOffset(sprite.frame.x, sprite.frame.y)
    }

    companion object {
        private const val PAPER = 0xFFF0DDD1.toInt()
        private const val MARK = 0xFFDB6633.toInt()
        private const val AMBER = 0xFFF5B53D.toInt()
        private const val AMBER_INK = 0xFF3A2A10.toInt()
        private const val IDLE = 0xFFC9C2A8.toInt()
        private const val ERROR = 0xFFC2412D.toInt()

        /** `null` si l'atlas `icones` n'a pas les bulles : la vue n'en dessine pas. */
        fun of(assets: GameAssets): StateBubbles? {
            val working = assets.animation("bulle.travail") ?: return null
            val waiting = assets.animation("bulle.attente")?.frames?.first() ?: return null
            val sleeping = assets.animation("sommeil") ?: return null
            return StateBubbles(
                working.frames,
                working.ms,
                waiting,
                waiting.atlas.mapped(waiting.frameId, mapOf(PAPER to AMBER, MARK to AMBER_INK)),
                waiting.atlas.mapped(waiting.frameId, mapOf(PAPER to IDLE, MARK to ERROR)),
                sleeping.frames,
                sleeping.ms,
            )
        }
    }
}
