package com.inamibrahim.kinhold.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.PlainTooltip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

/** The screen colour with a soft accent glow fading in from the top, as on every screen of the other apps. */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, maxContentWidth: androidx.compose.ui.unit.Dp = 640.dp, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background)
            .drawBehind {
                val strength = if (Palette.dark) 1f else 0.6f
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.Accent.copy(alpha = 0.20f * strength), Color.Transparent),
                        center = Offset(size.width * 0.12f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.AccentDeep.copy(alpha = 0.16f * strength), Color.Transparent),
                        center = Offset(size.width * 0.95f, 0f),
                        radius = size.width * 0.7f,
                    ),
                )
            },
    ) {
        // On wide screens (landscape, tablets) the content stays a readable width in the middle instead of stretching edge to edge.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxContentWidth).fillMaxSize()) { content() }
        }
    }
}

/** A small accent overline above a large bold title, left-aligned, with no bar or divider. */
@Composable
fun ScreenHeader(overline: String, title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            BackButton(onBack)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                overline.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = Palette.Accent,
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Text,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A round arrow button for going back. The arrow alone says it, so it carries no text (only a description for screen readers). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPressScale()
    Box(
        modifier
            .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
            .size(46.dp)
            .shadow(3.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Palette.SurfaceHigh)
            .semantics(mergeDescendants = true) { contentDescription = "Back" }
            .clickable(interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(22.dp))
    }
}

/**
 * A round button with only an icon, for actions whose picture says enough. Press and hold shows a small label with what it does, and the
 * label is also what a screen reader says. Removing actions are tinted red.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val press = rememberPressScale()
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider(androidx.compose.material3.TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = androidx.compose.material3.rememberTooltipState(),
    ) {
        Box(
            modifier
                .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
                .size(46.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (danger) Palette.Danger.copy(alpha = if (Palette.dark) 0.22f else 0.14f) else Palette.SurfaceHigh)
                .semantics { contentDescription = description }
                .clickable(interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) Palette.Danger else Palette.Text, modifier = Modifier.size(22.dp))
        }
    }
}

/** True inside a row of equally wide buttons: their icon and label sit together in the middle instead of starting at the left. */
val LocalCenterButtons = androidx.compose.runtime.compositionLocalOf { false }

/** Buttons whose label says they remove something are tinted red, wherever they are used. */
fun isRemoving(label: String): Boolean {
    val l = label.trim().lowercase()
    return l.startsWith("delete") || l.startsWith("remove") || l.startsWith("clear") || l.startsWith("unlink")
}

/** A fully round chip: raised fill when off, the solid accent with dark text when selected. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, small: Boolean = false) {
    @Suppress("NAME_SHADOWING") val icon = icon ?: autoIcon(label)
    val removing = isRemoving(label) && !selected
    val centered = true && LocalCenterButtons.current.let { true }
    val press = rememberPressScale()
    Box(
        modifier
            .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
            .heightIn(min = if (small) 32.dp else 38.dp)
            .clip(RoundedCornerShape(50))
            .background(animateColorAsState(if (selected) Palette.Accent else if (removing) Palette.Danger.copy(alpha = if (Palette.dark) 0.22f else 0.14f) else Palette.SurfaceHigh, tween(200), label = "pill").value)
            .semantics(mergeDescendants = true) { contentDescription = label; if (selected) stateDescription = "Selected" }
            .clickable(interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 8.dp else 16.dp, vertical = if (small) 4.dp else 6.dp),
        // With an icon the content starts at the left, so the icons of buttons stacked or in a row line up; a plain label stays centred.
        contentAlignment = if (icon != null && !centered) Alignment.CenterStart else Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint by animateColorAsState(if (selected) Palette.OnAccent else if (removing) Palette.Danger else Palette.Text, tween(200), label = "pillText")
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tint, maxLines = if (small) 1 else Int.MAX_VALUE, softWrap = !small)
        }
    }
}

/** The one main action on a screen: a tall, fully round button in the solid accent. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    @Suppress("NAME_SHADOWING") val icon = icon ?: autoIcon(label)
    val press = rememberPressScale()
    Box(
        modifier
            .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Palette.Accent else Palette.SurfaceHigh)
            .clickable(enabled = enabled, interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ink = if (enabled) Palette.OnAccent else Palette.TextMuted
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink)
        }
    }
}

/**
 * The buttons at the bottom of a form, the same everywhere: Save across the full width, then Cancel and (when there is one) the
 * removing action side by side, each with its icon and its label in the middle.
 */
@Composable
fun FormActions(
    saveLabel: String,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    removeLabel: String?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(saveLabel, onSave, Modifier.fillMaxWidth(), enabled = saveEnabled, icon = androidx.compose.material.icons.Icons.Filled.Check)
        EqualRow(2) {
            DialogButton("Cancel", onCancel, Modifier.weight(1f), kind = ButtonKind.Neutral)
            if (onRemove != null && removeLabel != null) DialogButton(removeLabel, onRemove, Modifier.weight(1f), kind = ButtonKind.Danger)
        }
    }
}

/** A button that shrinks a little while it is pressed, as in the other apps. */
@Stable
class PressScale(val source: MutableInteractionSource, val scale: State<Float>)

@Composable
fun rememberPressScale(): PressScale {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    return remember(source, scale) { PressScale(source, scale) }
}

/**
 * The hero of a screen: a bold gradient card that fades from the accent into a deeper shade, with a faint light in
 * the top-left corner and a soft glow under it, as in the shared design.
 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val start = Palette.AccentDeep
    val end = lerp(Palette.AccentDeep, Color(0xFF0B1220), 0.45f)
    Box(
        modifier
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = start, spotColor = start)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset(900f, 900f)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.1f, 0f),
                        radius = size.width * 0.8f,
                    ),
                )
                drawHeroGlyph(Color.White.copy(alpha = 0.10f))
            },
    ) { content() }
}

/** The app's mark (one parent above three children) drawn large and faint in the corner of a hero card. */
private fun DrawScope.drawHeroGlyph(color: Color) {
    // Up in the top-right corner, clear of the button, and small enough to stay inside the card.
    val unit = size.height * 0.34f
    val cx = size.width - unit * 0.9f
    val cy = unit * 0.95f
    val r = unit * 0.16f
    val stroke = Stroke(width = unit * 0.045f, cap = StrokeCap.Round)
    val top = Offset(cx, cy - unit * 0.55f)
    val kids = listOf(-0.5f, 0f, 0.5f).map { Offset(cx + unit * it, cy + unit * 0.25f) }
    val busY = cy - unit * 0.12f
    drawLine(color, Offset(top.x, top.y + r), Offset(top.x, busY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(kids.first().x, busY), Offset(kids.last().x, busY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    for (k in kids) drawLine(color, Offset(k.x, busY), Offset(k.x, k.y - r), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawCircle(color, r * 1.15f, top)
    for (k in kids) drawCircle(color, r, k)
}

/** A lifted rounded surface. Everything sits on cards, with no divider lines between rows. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Palette.Surface,
        shadowElevation = 8.dp,
    ) { content() }
}

/** A square-ish button with the icon above its label, for rows of three to five actions that must all fit on screen. */
@Composable
fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, tone: Color = Palette.Accent, compact: Boolean = false) {
    val press = rememberPressScale()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
            .shadow(3.dp, shape, clip = false)
            .clip(shape)
            .background(if (selected) tone.copy(alpha = 0.28f) else Palette.SurfaceHigh)
            .semantics(mergeDescendants = true) { contentDescription = label; if (selected) stateDescription = "Selected" }
            .clickable(interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(vertical = if (compact) 6.dp else 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToneIcon(icon, tone, if (compact) 32.dp else 40.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Text, maxLines = 1, modifier = Modifier.padding(top = if (compact) 4.dp else 6.dp))
    }
}

/** An icon in a soft circle of its own colour, the way the other apps mark kinds of things (call, map, family). */
@Composable
fun ToneIcon(icon: ImageVector, tone: Color, size: androidx.compose.ui.unit.Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(tone.copy(alpha = if (Palette.dark) 0.22f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(size * 0.55f)) }
}

/** One place the floating bar can take you. */
class NavItem(val label: String, val icon: ImageVector, val selected: Boolean = false, val onClick: () -> Unit)

/** The rounded bar floating at the bottom of the main screens: every item shows its icon and its label, and the chosen one a soft pill. */
@Composable
fun FloatingNav(items: List<NavItem>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(8.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.Surface)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (item in items) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (item.selected) Palette.Accent.copy(alpha = 0.22f) else Color.Transparent)
                    .semantics(mergeDescendants = true) { contentDescription = item.label }
                    .clickable(role = Role.Tab, onClick = item.onClick)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val tint = if (item.selected) Palette.Accent else Palette.TextMuted
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** How a [DialogButton] looks: the main action, a soft accent action, a neutral one such as Cancel, or a destructive one. */
enum class ButtonKind { Primary, Tonal, Neutral, Danger }

/**
 * A real button for dialogs and for actions that used to be plain links (Done, Cancel, Clear, Delete, Undo, Remove). Rounded,
 * filled, with a ripple and a small press effect, like the buttons of the other apps.
 */
@Composable
fun DialogButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: ButtonKind = ButtonKind.Tonal, icon: ImageVector? = null, enabled: Boolean = true, small: Boolean = false) {
    @Suppress("NAME_SHADOWING") val icon = icon ?: autoIcon(label)
    @Suppress("NAME_SHADOWING") val kind = if (kind != ButtonKind.Primary && isRemoving(label)) ButtonKind.Danger else kind
    val centered = true && LocalCenterButtons.current.let { true }
    val press = rememberPressScale()
    val (fill, ink) = when (kind) {
        ButtonKind.Primary -> Palette.Accent to Palette.OnAccent
        ButtonKind.Tonal -> Palette.Accent.copy(alpha = if (Palette.dark) 0.22f else 0.16f) to (if (Palette.dark) Palette.Accent else Palette.AccentDeep)
        ButtonKind.Neutral -> Palette.SurfaceHigh to Palette.Text
        ButtonKind.Danger -> Palette.Danger.copy(alpha = if (Palette.dark) 0.22f else 0.14f) to Palette.Danger
    }
    Row(
        modifier
            .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
            .heightIn(min = if (small) 34.dp else 44.dp)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clickable(enabled = enabled, interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 20.dp, vertical = if (small) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Icon first, then the label, starting at the left so icons line up between buttons of one group.
        horizontalArrangement = if (icon != null && !centered) Arrangement.Start else Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(if (small) 16.dp else 18.dp))
            Spacer(Modifier.width(if (small) 6.dp else 8.dp))
        }
        Text(label, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

/**
 * A group of buttons laid out [perRow] to a row, where every button in a row has the same width and the last row stretches
 * to fill the space left. Give each button `Modifier.weight(1f)` inside it.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EqualRow(perRow: Int, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier.then(Modifier.fillMaxWidth()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = perRow,
        content = { androidx.compose.runtime.CompositionLocalProvider(LocalCenterButtons provides true) { content() } },
    )
}

/**
 * The buttons of a dialog. They sit side by side while they fit, and wrap onto the next line when the text is large, instead of
 * being squeezed until a label breaks letter by letter.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DialogButtons(centered: Boolean = false, content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (centered) Alignment.CenterHorizontally else Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * A fitting icon for a button that was given none, chosen from its label, so every action button carries one. Choices such as Male,
 * Female, a number of generations or a theme name have no action in their label and stay plain.
 */
fun autoIcon(label: String): ImageVector? {
    val l = label.trim().removePrefix("+").trim().lowercase()
    fun has(vararg words: String) = words.any { l.startsWith(it) || l.contains(" $it") }
    val i = androidx.compose.material.icons.Icons.Filled
    return when {
        l.isEmpty() -> null
        has("unlink") -> AppIcons.Unlink
        l == "no" || has("cancel", "close", "not now", "different person", "turn off", "turn the reminder off", "stop using", "dismiss", "not the same") -> i.Close
        has("delete", "remove", "clear") -> i.Delete
        has("skip", "continue without", "use theirs", "next", "start") -> i.ArrowForward
        has("import", "open") -> i.KeyboardArrowUp
        has("add") -> i.Add
        has("undo") -> i.Refresh
        has("send") -> i.Send
        has("share") -> i.Share
        has("save", "back up", "export") -> i.KeyboardArrowDown
        has("import", "open") -> i.KeyboardArrowUp
        has("settings") -> i.Settings
        has("how kinhold", "help") -> i.Info
        has("change", "choose", "adjust", "edit", "attach") -> i.Edit
        has("reset", "swap", "replace") -> i.Refresh
        has("take a photo") -> AppIcons.Camera
        has("turn on") -> i.Check
        has("done", "use", "keep", "confirm", "continue", "i understand", "same person", "fill", "join", "yes", "import") -> i.Check
        else -> null
    }
}
