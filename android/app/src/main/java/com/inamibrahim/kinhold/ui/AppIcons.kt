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
 * Icons the core icon set does not have, drawn as small vectors so a bigger icon library does not add to the app size. Every icon here is
 * meant for exactly one meaning, so two different actions never share a picture.
 */
object AppIcons {
    private val ink = SolidColor(Color.Black)

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(block: PathBuilder.() -> Unit) =
        path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)

    private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) = path(fill = ink, pathBuilder = block)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    /** The light theme. */
    val Sun: ImageVector by lazy {
        build("Sun") {
            line {
                circle(12f, 12f, 3.6f)
                moveTo(12f, 2.5f); lineTo(12f, 5f)
                moveTo(12f, 19f); lineTo(12f, 21.5f)
                moveTo(2.5f, 12f); lineTo(5f, 12f)
                moveTo(19f, 12f); lineTo(21.5f, 12f)
                moveTo(5.3f, 5.3f); lineTo(7f, 7f)
                moveTo(17f, 17f); lineTo(18.7f, 18.7f)
                moveTo(18.7f, 5.3f); lineTo(17f, 7f)
                moveTo(7f, 17f); lineTo(5.3f, 18.7f)
            }
        }
    }

    /** The dark theme. */
    val Moon: ImageVector by lazy {
        build("Moon") {
            solid {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, false, 9f, 9f)
                curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                arcToRelative(5.389f, 5.389f, 0f, false, true, -4.4f, 2.26f)
                arcToRelative(5.403f, 5.403f, 0f, false, true, -3.14f, -9.8f)
                curveToRelative(-0.44f, -0.06f, -0.9f, -0.1f, -1.36f, -0.1f)
                close()
            }
        }
    }

    /** The theme that follows the phone: a phone outline. */
    val PhoneScreen: ImageVector by lazy {
        build("PhoneScreen") {
            line {
                moveTo(9f, 2.5f); lineTo(15f, 2.5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(17f, 19.5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(9f, 21.5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(7f, 4.5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(10.5f, 18.5f); lineTo(13.5f, 18.5f)
            }
        }
    }

    /** A birthday: a cake with a lit candle. */
    val Cake: ImageVector by lazy {
        build("Cake") {
            line {
                moveTo(4f, 20.5f); lineTo(20f, 20.5f); lineTo(20f, 12f); lineTo(4f, 12f); close()
                moveTo(4f, 16.2f); lineTo(7f, 17.6f); lineTo(10f, 16.2f); lineTo(13f, 17.6f); lineTo(16f, 16.2f); lineTo(20f, 17.6f)
                moveTo(12f, 12f); lineTo(12f, 8.5f)
            }
            solid {
                moveTo(12f, 2.8f)
                curveToRelative(1.3f, 1.4f, 1.9f, 2.4f, 1.9f, 3.3f)
                arcToRelative(1.9f, 1.9f, 0f, false, true, -3.8f, 0f)
                curveToRelative(0f, -0.9f, 0.6f, -1.9f, 1.9f, -3.3f)
                close()
            }
        }
    }

    /** A remembrance day: a single candle. */
    val Candle: ImageVector by lazy {
        build("Candle") {
            line {
                moveTo(8.5f, 10.5f); lineTo(15.5f, 10.5f); lineTo(15.5f, 21f); lineTo(8.5f, 21f); close()
            }
            solid {
                moveTo(12f, 2f)
                curveToRelative(1.5f, 1.6f, 2.2f, 2.8f, 2.2f, 3.9f)
                arcToRelative(2.2f, 2.2f, 0f, false, true, -4.4f, 0f)
                curveToRelative(0f, -1.1f, 0.7f, -2.3f, 2.2f, -3.9f)
                close()
            }
        }
    }

    /** A wedding anniversary: two linked rings. */
    val Rings: ImageVector by lazy {
        build("Rings") {
            line {
                circle(8.8f, 14f, 5f)
                circle(15.2f, 14f, 5f)
                moveTo(7f, 5.2f); lineTo(8.8f, 3f); lineTo(10.6f, 5.2f)
            }
        }
    }

    /** How two people are related: three dots joined. */
    val Network: ImageVector by lazy {
        build("Network") {
            line {
                moveTo(12f, 7.5f); lineTo(6.4f, 16.2f)
                moveTo(12f, 7.5f); lineTo(17.6f, 16.2f)
                moveTo(7.5f, 19f); lineTo(16.5f, 19f)
            }
            solid {
                circle(12f, 5f, 2.8f)
                circle(5f, 19f, 2.8f)
                circle(19f, 19f, 2.8f)
            }
        }
    }

    /** Counts and records: three bars. */
    val BarChart: ImageVector by lazy {
        build("BarChart") {
            solid {
                moveTo(4f, 20f); lineTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 20f); close()
                moveTo(10f, 20f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); close()
                moveTo(16f, 20f); lineTo(16f, 8.5f); lineTo(20f, 8.5f); lineTo(20f, 20f); close()
            }
        }
    }

    /** Kinhold Pro: a crown. */
    val Crown: ImageVector by lazy {
        build("Crown") {
            solid {
                moveTo(3f, 17.5f); lineTo(3f, 7.5f); lineTo(8f, 11.8f); lineTo(12f, 4.5f); lineTo(16f, 11.8f); lineTo(21f, 7.5f); lineTo(21f, 17.5f); close()
                moveTo(3f, 19.5f); lineTo(21f, 19.5f); lineTo(21f, 21.5f); lineTo(3f, 21.5f); close()
            }
        }
    }

    /** Keeping a copy: a tray with an arrow going in. */
    val Backup: ImageVector by lazy {
        build("Backup") {
            line {
                moveTo(4f, 14f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 14f)
                moveTo(12f, 3.5f); lineTo(12f, 15f)
                moveTo(7.5f, 10.8f); lineTo(12f, 15.3f); lineTo(16.5f, 10.8f)
            }
        }
    }

    /** Male: a circle with an arrow pointing up and right. */
    val Male: ImageVector by lazy {
        build("Male") {
            line {
                circle(9.5f, 14.5f, 5f)
                moveTo(13.2f, 10.8f); lineTo(20f, 4f)
                moveTo(14.5f, 4f); lineTo(20f, 4f); lineTo(20f, 9.5f)
            }
        }
    }

    /** Female: a circle on a small cross. */
    val Female: ImageVector by lazy {
        build("Female") {
            line {
                circle(12f, 8.5f, 5f)
                moveTo(12f, 13.5f); lineTo(12f, 21f)
                moveTo(8.5f, 17.5f); lineTo(15.5f, 17.5f)
            }
        }
    }

    /** Sex not stated: an empty circle with a dash. */
    val Unknown: ImageVector by lazy {
        build("Unknown") {
            line {
                circle(12f, 12f, 8.5f)
                moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            }
        }
    }

    /** Linking two people already in the tree: two chain halves joined. */
    val Link: ImageVector by lazy {
        build("Link") {
            line {
                moveTo(10f, 14f); lineTo(7.2f, 16.8f); arcToRelative(3.2f, 3.2f, 0f, false, true, -4.5f, -4.5f); lineTo(6f, 9f); arcToRelative(3.2f, 3.2f, 0f, false, true, 4.5f, 0f)
                moveTo(14f, 10f); lineTo(16.8f, 7.2f); arcToRelative(3.2f, 3.2f, 0f, false, true, 4.5f, 4.5f); lineTo(18f, 15f); arcToRelative(3.2f, 3.2f, 0f, false, true, -4.5f, 0f)
                moveTo(9.5f, 14.5f); lineTo(14.5f, 9.5f)
            }
        }
    }

    /** A text message: a rounded speech bubble with two lines. */
    val Chat: ImageVector by lazy {
        build("Chat") {
            line {
                moveTo(5f, 4.5f); lineTo(19f, 4.5f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); lineTo(21f, 14f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(10f, 16f); lineTo(5.5f, 20f); lineTo(5.5f, 16f); lineTo(5f, 16f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); lineTo(3f, 6.5f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
                moveTo(7.5f, 9f); lineTo(16.5f, 9f)
                moveTo(7.5f, 12f); lineTo(13f, 12f)
            }
        }
    }

    /** WhatsApp: a round speech bubble with a handset. */
    val WhatsApp: ImageVector by lazy {
        build("WhatsApp") {
            line {
                moveTo(3f, 21f); lineTo(4.4f, 16.6f)
                arcToRelative(8.7f, 8.7f, 0f, true, true, 3.2f, 3.2f); close()
            }
            solid {
                moveTo(9f, 8f); lineTo(10.6f, 8f); lineTo(11.3f, 10f); lineTo(10.4f, 10.9f)
                curveToRelative(0.5f, 1f, 1.2f, 1.7f, 2.2f, 2.2f); lineTo(13.5f, 12.2f); lineTo(15.5f, 12.9f); lineTo(15.5f, 14.5f)
                curveToRelative(-3.4f, 0.2f, -6.8f, -3.2f, -6.5f, -6.5f); close()
            }
        }
    }

    /** Choose a picture from the gallery: a picture frame with a hill and a sun. */
    val Gallery: ImageVector by lazy {
        build("Gallery") {
            line {
                moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                moveTo(3f, 16f); lineTo(9f, 10f); lineTo(14f, 15f); lineTo(17f, 12f); lineTo(21f, 16f)
            }
            solid { circle(16.5f, 8.2f, 1.6f) }
        }
    }

    /** Take a picture: a camera. */
    val Camera: ImageVector by lazy {
        build("Camera") {
            line {
                moveTo(3f, 8f); lineTo(7.5f, 8f); lineTo(9f, 5.5f); lineTo(15f, 5.5f); lineTo(16.5f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
                circle(12f, 13.5f, 3.4f)
            }
        }
    }

    /** Move and zoom a picture: the crop corners. */
    val Crop: ImageVector by lazy {
        build("Crop") {
            line {
                moveTo(7f, 2f); lineTo(7f, 17f); lineTo(22f, 17f)
                moveTo(2f, 7f); lineTo(17f, 7f); lineTo(17f, 22f)
            }
        }
    }

    /** Removing the link between two people: a broken chain. */
    val Unlink: ImageVector by lazy {
        build("Unlink") {
            line {
                moveTo(10f, 14f); lineTo(7.2f, 16.8f); arcToRelative(3.2f, 3.2f, 0f, false, true, -4.5f, -4.5f); lineTo(6f, 9f); arcToRelative(3.2f, 3.2f, 0f, false, true, 4.5f, 0f)
                moveTo(14f, 10f); lineTo(16.8f, 7.2f); arcToRelative(3.2f, 3.2f, 0f, false, true, 4.5f, 4.5f); lineTo(18f, 15f); arcToRelative(3.2f, 3.2f, 0f, false, true, -4.5f, 0f)
                moveTo(3f, 3f); lineTo(21f, 21f)
            }
        }
    }
}
