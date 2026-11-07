package com.inamibrahim.kinhold.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.tree.PlacedPerson
import com.inamibrahim.kinhold.tree.TreeLayout
import com.inamibrahim.kinhold.tree.TreeMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where the tree is on screen. [scale] is the zoom (1 = 100%, one tree unit per dp) and [offset] is where the top-left
 * of the tree sits in pixels. Both are Compose state read only while drawing, so a pinch or drag redraws the canvas
 * without recomposing anything.
 */
@Stable
class TreeViewState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var density = 1f
    var minScale = 0.05f
    var flingJob: Job? = null
    private var bounds = Size.Zero

    /** The person the view was last framed on, so coming back to the tree keeps the zoom and position the person left it at. */
    var framedFocus = -1

    fun adopt(layout: TreeLayout) {
        bounds = Size(layout.width, layout.height)
    }

    /** Screen pixels for one tree unit. */
    val pxPerUnit: Float get() = scale * density

    private class Frame(val minScale: Float, val scale: Float, val offset: Offset)

    /** The zoom and position that frame [layout] with its focused person in the middle, or null while the canvas has no size. */
    private fun frameFor(layout: TreeLayout): Frame? {
        if (size.width == 0 || size.height == 0) return null
        val w = (layout.width + 2 * MARGIN) * density
        val h = (layout.height + 2 * MARGIN) * density
        val fit = min(size.width / w, size.height / h)
        val newScale = fit.coerceIn(READABLE, 1f)
        val s = newScale * density
        // Centred on the focused person across; down, the whole view is centred if it fits, otherwise the focus sits
        // a little above the middle so the rows below it get the room.
        val contentHeight = layout.height * s
        val y = if (contentHeight + 2 * MARGIN * density <= size.height) {
            (size.height - contentHeight) / 2f
        } else {
            size.height * 0.4f - layout.focusY * s
        }
        return Frame((fit * 0.8f).coerceIn(0.05f, READABLE), newScale, clamp(Offset(size.width / 2f - layout.focusX * s, y), s, Size(layout.width, layout.height)))
    }

    /** The layout the view was last framed on, so a tap on someone in it can slide from where they were drawn. */
    private var lastLayout: TreeLayout? = null
    private var slideTarget: Frame? = null

    /** Frames the tree with the focused person in the middle of the screen, never smaller than readable. */
    fun show(layout: TreeLayout) {
        val f = frameFor(layout) ?: return
        slideJob?.cancel()
        framedSize = size
        userMoved = false
        stopFling()
        bounds = Size(layout.width, layout.height)
        minScale = f.minScale
        scale = f.scale
        offset = f.offset
        lastLayout = layout
        slideTarget = null
    }

    /**
     * Called as soon as a new layout exists, before it is first drawn. If the new focus was on screen in the old layout,
     * the new tree is placed so that this person has not moved; [slideOrShow] then glides the view to the centre. Without
     * this the first frame of the new tree would be drawn in the old place.
     */
    private var jumping = false

    fun prepareSlide(layout: TreeLayout) {
        slideJob?.cancel()
        slideTarget = null
        jumping = false
        val old = lastLayout ?: return
        if (size.width == 0 || framedFocus == -1 || layout.focusId == framedFocus) return
        val target = frameFor(layout) ?: return
        val before = old.people.firstOrNull { it.person.id == layout.focusId }
        val after = layout.people.firstOrNull { it.person.id == layout.focusId }
        bounds = Size(layout.width, layout.height)
        if (before == null || after == null) {
            // A far jump (Find, Me): the new person was not on screen, so there is nothing to slide from. The new tree
            // starts a little lower, already at its final zoom, and rises into place while it fades in.
            jumping = true
            scale = target.scale
            offset = Offset(target.offset.x, target.offset.y + 56f * density)
            slideTarget = target
            return
        }
        val s = pxPerUnit
        val onScreen = offset + Offset(before.x, before.y) * s
        offset = onScreen - Offset(after.x, after.y) * s
        slideTarget = target
    }

    private var slideJob: Job? = null

    /** The canvas size the view was last framed for, and whether the person has moved the view since. A change of size (for example the card growing) re-frames the view unless it was moved. */
    var framedSize = IntSize.Zero
    var userMoved = false

    /** Frames the view again for the current canvas size, gliding there. */
    fun reframe(layout: TreeLayout, scope: kotlinx.coroutines.CoroutineScope) {
        slideTarget = frameFor(layout) ?: return
        slideOrShow(layout, scope)
    }

    /**
     * Glides to the framing of [layout] if [prepareSlide] set one up, and otherwise frames it at once. The glide runs in
     * [scope] (not in an effect that restarts), so it is never cut short half way.
     */
    fun slideOrShow(layout: TreeLayout, scope: kotlinx.coroutines.CoroutineScope) {
        slideJob?.cancel()
        framedSize = size
        userMoved = false
        // The target is worked out again here: the canvas may have changed size since prepareSlide, for example when the card grew.
        val target = if (slideTarget != null) frameFor(layout) else null
        if (target == null) {
            show(layout)
            return
        }
        slideTarget = null
        stopFling()
        lastLayout = layout
        minScale = target.minScale
        val s0 = scale
        val o0 = offset
        slideJob = scope.launch {
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(if (jumping) 260 else 340, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { p, _ ->
                scale = s0 + (target.scale - s0) * p
                offset = Offset(o0.x + (target.offset.x - o0.x) * p, o0.y + (target.offset.y - o0.y) * p)
            }
            scale = target.scale
            offset = target.offset
        }
    }

    fun stopFling() {
        flingJob?.cancel()
        flingJob = null
    }

    /** Pinch and drag: the point under [centroid] stays under the fingers. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(minScale, MAX_SCALE)
        val z = newScale / scale
        scale = newScale
        offset = clamp((offset - centroid) * z + centroid + pan)
        userMoved = true
    }

    /** Keeps gliding after the finger lifts, slowing down like a real surface. */
    suspend fun fling(velocity: Offset) {
        var last = Offset.Zero
        AnimationState(Offset.VectorConverter, Offset.Zero, AnimationVector2D(velocity.x, velocity.y))
            .animateDecay(exponentialDecay<Offset>(frictionMultiplier = 1.3f, absVelocityThreshold = 50f)) {
                val delta = value - last
                last = value
                offset = clamp(offset + delta)
            }
    }

    /** Keeps some of the tree on screen so it can never be dragged out of sight. */
    private fun clamp(o: Offset, s: Float = pxPerUnit, b: Size = bounds): Offset {
        val keep = 96f
        val loX = min(keep - b.width * s, size.width - keep)
        val hiX = max(keep - b.width * s, size.width - keep)
        val loY = min(keep - b.height * s, size.height - keep)
        val hiY = max(keep - b.height * s, size.height - keep)
        return Offset(o.x.coerceIn(loX, hiX), o.y.coerceIn(loY, hiY))
    }

    companion object {
        const val MAX_SCALE = 2.5f
        const val READABLE = 0.7f
        const val MARGIN = 24f
    }
}

private const val CARD_RADIUS = 16f
private const val AVATAR_RADIUS = 22f
private const val AVATAR_CENTER = 12f + AVATAR_RADIUS
private const val TEXT_LEFT = 68f

private val NameStyle = TextStyle(fontFamily = AppFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
private val YearsStyle = TextStyle(fontFamily = AppFont, fontSize = 11.sp, fontWeight = FontWeight.Normal)
private val BadgeStyle = TextStyle(fontFamily = AppFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)

/** Text is measured once in tree units (1 px = 1 unit) and drawn on the scaled canvas, so zooming never re-measures. */
private val UnitDensity = Density(1f, 1f)

/**
 * The zoomable tree around one person. Tapping someone else calls [onFocus]. Only boxes that are on screen are drawn,
 * and detail is dropped when they are too small to read.
 */
@Composable
fun TreeCanvas(
    layout: TreeLayout,
    state: TreeViewState,
    onFocus: (PlacedPerson) -> Unit,
    modifier: Modifier = Modifier,
) {
    state.density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val avatars = LocalAvatars.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    // The tree fades in when the focus moves, which is a change of the layer's opacity only, so it costs no redraw.
    val fade = remember { Animatable(1f) }
    LaunchedEffect(layout) {
        fade.snapTo(0.15f)
        fade.animateTo(1f, tween(240))
    }
    val names = remember(layout) { HashMap<Int, TextLayoutResult>() }
    val years = remember(layout) { HashMap<Int, TextLayoutResult>() }
    val badges = remember(layout) { HashMap<String, TextLayoutResult>() }

    Canvas(
        modifier
            .fillMaxSize()
            // Cards pan beyond the canvas, so they must be cut at its edge. The layer is also what keeps a pan or
            // pinch from redrawing the header and background gradients behind it on every frame.
            .clipToBounds()
            .graphicsLayer { alpha = fade.value }
            .onSizeChanged { state.size = it }
            .pointerInput(Unit) {
                detectTreeGestures(
                    onDown = { state.stopFling() },
                    onTransform = { centroid, pan, zoom -> state.transform(centroid, pan, zoom) },
                    onFling = { velocity -> state.flingJob = scope.launch { state.fling(velocity) } },
                )
            }
            .pointerInput(layout) {
                detectTapGestures { tap ->
                    val s = state.pxPerUnit
                    val wx = (tap.x - state.offset.x) / s
                    val wy = (tap.y - state.offset.y) / s
                    layout.people.firstOrNull {
                        wx >= it.x && wx <= it.x + TreeMetrics.NODE_W && wy >= it.y && wy <= it.y + TreeMetrics.NODE_H
                    }?.let(onFocus)
                }
            },
    ) {
        val s = state.pxPerUnit
        val off = state.offset
        val left = -off.x / s
        val top = -off.y / s
        val right = (size.width - off.x) / s
        val bottom = (size.height - off.y) / s
        val nodeScreenW = TreeMetrics.NODE_W * s

        val lineColor = Palette.TextMuted.copy(alpha = 0.5f)
        val cardColor = Palette.Surface
        val focusColor = Palette.SurfaceHigh
        val textColor = Palette.Text
        val mutedColor = Palette.TextMuted
        val accent = Palette.Accent
        val badgeColor = Palette.AccentDeep

        withTransform({
            translate(left = off.x, top = off.y)
            scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
        }) {
            val stroke = max(1.6f, 1f / s)
            val e = layout.edges
            var i = 0
            while (i < e.size) {
                val x1 = e[i]
                val y1 = e[i + 1]
                val x2 = e[i + 2]
                val y2 = e[i + 3]
                if (max(x1, x2) >= left && min(x1, x2) <= right && max(y1, y2) >= top && min(y1, y2) <= bottom) {
                    drawLine(lineColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke, cap = StrokeCap.Round)
                }
                i += 4
            }

            val radius = CornerRadius(CARD_RADIUS)
            val boxSize = Size(TreeMetrics.NODE_W, TreeMetrics.NODE_H)
            val shadow = Color.Black.copy(alpha = if (Palette.dark) 0.35f else 0.12f)
            for (p in layout.people) {
                if (p.x > right || p.x + TreeMetrics.NODE_W < left || p.y > bottom || p.y + TreeMetrics.NODE_H < top) continue
                val tone = toneFor(p.person.sex)
                val topLeft = Offset(p.x, p.y)
                if (nodeScreenW < 34f) {
                    // Too small for a card: a coloured block still shows where the family is wide or narrow.
                    drawRoundRect(tone.copy(alpha = 0.85f), topLeft, boxSize, radius)
                    continue
                }
                val isFocus = p.person.id == layout.focusId
                // A soft shadow under every card, and a glow around the focused one.
                drawRoundRect(shadow, topLeft + Offset(0f, 3f), boxSize, radius)
                if (isFocus) {
                    drawRoundRect(accent.copy(alpha = 0.22f), topLeft - Offset(3f, 3f), Size(boxSize.width + 6f, boxSize.height + 6f), CornerRadius(CARD_RADIUS + 3f), style = Stroke(width = 6f))
                }
                drawRoundRect(if (isFocus) focusColor else cardColor, topLeft, boxSize, radius)
                if (isFocus) drawRoundRect(accent, topLeft, boxSize, radius, style = Stroke(width = 2.5f))
                if (nodeScreenW >= 60f) {
                    drawAvatar(Offset(p.x + AVATAR_CENTER, p.y + TreeMetrics.NODE_H / 2), AVATAR_RADIUS, avatars.get(p.person), tone, 2.5f)
                }
                if (nodeScreenW >= 110f) {
                    val name = names.getOrPut(p.person.id) { measurer.measureLine(p.person.name, NameStyle) }
                    drawText(name, textColor, Offset(p.x + TEXT_LEFT, p.y + 15f))
                    if (nodeScreenW >= 140f && p.person.years().isNotEmpty()) {
                        val y = years.getOrPut(p.person.id) { measurer.measureLine(p.person.years(), YearsStyle) }
                        drawText(y, mutedColor, Offset(p.x + TEXT_LEFT, p.y + 36f))
                    }
                }
                if (p.moreBelow > 0) {
                    // A small pill on the bottom edge: this many children are one tap away.
                    val label = badges.getOrPut("+${p.moreBelow}") { measurer.measureLine("+${p.moreBelow}", BadgeStyle, wide = true) }
                    val w = label.size.width + 14f
                    val pillTop = Offset(p.x + TreeMetrics.NODE_W / 2 - w / 2, p.y + TreeMetrics.NODE_H - 8f)
                    drawRoundRect(badgeColor, pillTop, Size(w, 17f), CornerRadius(8.5f))
                    drawText(label, Color.White, Offset(pillTop.x + 7f, pillTop.y + 3f))
                }
                if (p.person.isMe && nodeScreenW >= 60f) {
                    // A small "YOU" pill on the top edge marks the owner of this tree.
                    val label = badges.getOrPut("YOU") { measurer.measureLine("YOU", BadgeStyle, wide = true) }
                    val w = label.size.width + 14f
                    val pt = Offset(p.x + TreeMetrics.NODE_W - w - 10f, p.y - 8f)
                    drawRoundRect(accent, pt, Size(w, 17f), CornerRadius(8.5f))
                    drawText(label, Palette.OnAccent, Offset(pt.x + 7f, pt.y + 3f))
                }
                if (p.moreAbove) {
                    // A little arrow above the card: more ancestors are one tap away.
                    val cx = p.x + TreeMetrics.NODE_W / 2
                    drawLine(accent, Offset(cx - 7f, p.y - 4f), Offset(cx, p.y - 11f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                    drawLine(accent, Offset(cx + 7f, p.y - 4f), Offset(cx, p.y - 11f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/**
 * Pan and pinch like `detectTransformGestures`, plus the speed of a one-finger drag when the finger lifts, so the
 * tree can glide on. A tap never reaches here as a gesture: nothing is consumed until the touch slop is passed.
 */
private suspend fun PointerInputScope.detectTreeGestures(
    onDown: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onFling: (velocity: Offset) -> Unit,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastSlop = false
        var multiTouch = false
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker()
        awaitFirstDown(requireUnconsumed = false)
        onDown()
        do {
            val event = awaitPointerEvent()
            val cancelled = event.changes.fastAny { it.isConsumed }
            if (!cancelled) {
                if (event.changes.count { it.pressed } > 1) multiTouch = true
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    if (abs(1 - zoom) * centroidSize > slop || pan.getDistance() > slop) pastSlop = true
                }
                if (pastSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) onTransform(centroid, panChange, zoomChange)
                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                    event.changes.firstOrNull { it.pressed }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                }
            }
        } while (!cancelled && event.changes.fastAny { it.pressed })
        if (pastSlop && !multiTouch) {
            val v = tracker.calculateVelocity()
            onFling(Offset(v.x, v.y))
        }
    }
}

private fun TextMeasurer.measureLine(text: String, style: TextStyle, wide: Boolean = false): TextLayoutResult = measure(
    text = text,
    style = style,
    overflow = TextOverflow.Ellipsis,
    maxLines = 1,
    constraints = Constraints(maxWidth = if (wide) 200 else (TreeMetrics.NODE_W - TEXT_LEFT - 10f).toInt()),
    density = UnitDensity,
)
