package com.inamibrahim.kinhold.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small pictures for the relative buttons, drawn as vectors (the core icon set has no family icons, and a bigger icon library would
 * add size to the app). A parent is a person with an arrow up, a child a person with an arrow down, and a sibling two people side by side.
 */
object FamilyIcons {
    private val ink = SolidColor(Color.Black)

    /** A head and shoulders, as one solid shape, with its head centred at ([cx], [headY]) and its shoulders [width] wide ending at [baseY]. */
    private fun PathBuilder.person(cx: Float, headY: Float, headR: Float, width: Float, baseY: Float) {
        // head
        moveTo(cx - headR, headY)
        arcToRelative(headR, headR, 0f, true, true, 2 * headR, 0f)
        arcToRelative(headR, headR, 0f, true, true, -2 * headR, 0f)
        close()
        // shoulders: a half-round body
        val half = width / 2
        moveTo(cx - half, baseY)
        arcToRelative(half, half * 1.05f, 0f, false, true, width, 0f)
        close()
    }

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.arrow(up: Boolean) {
        val tip = if (up) 4.2f else 14.8f
        val tail = if (up) 14.8f else 4.2f
        val wing = if (up) 3.1f else -3.1f
        path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(19.6f, tail); lineTo(19.6f, tip)
            moveTo(16.7f, tip + wing); lineTo(19.6f, tip); lineTo(22.5f, tip + wing)
        }
    }

    /** A person with an arrow pointing up: someone in the generation above. */
    val Parent: ImageVector by lazy {
        build("FamilyParent") {
            path(fill = ink) { person(9f, 7f, 3.2f, 13f, 20.5f) }
            arrow(up = true)
        }
    }

    /** A person with an arrow pointing down: someone in the generation below. */
    val Child: ImageVector by lazy {
        build("FamilyChild") {
            path(fill = ink) { person(9f, 7f, 3.2f, 13f, 20.5f) }
            arrow(up = false)
        }
    }

    /** Two people side by side: someone in the same generation. */
    val Sibling: ImageVector by lazy {
        build("FamilySibling") {
            path(fill = ink) {
                person(7f, 7.5f, 3f, 11f, 20f)
                person(17f, 7.5f, 3f, 11f, 20f)
            }
        }
    }
}
