package com.inamibrahim.kinhold.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.PhotoCodec
import com.inamibrahim.kinhold.data.PreparedPhoto
import kotlin.math.max
import kotlin.math.min

/** The share of the window the circle covers; the rest is margin so the circle edge is never cut by the dialog. */
private const val CIRCLE = 0.92f

/**
 * Shows a picked photo behind a round window so the person can drag and zoom it until the face sits where they want it.
 * "Use this" keeps only the square under the circle as the profile photo. The picture shown is the already shrunk copy
 * (at most 720 pixels), so this costs little memory even on a weak phone.
 */
@Composable
fun PhotoCropDialog(raw: PreparedPhoto, onDone: (PreparedPhoto) -> Unit, onCancel: () -> Unit) {
    val bitmap = remember(raw) { BitmapFactory.decodeByteArray(raw.main, 0, raw.main.size) }
    if (bitmap == null) {
        onCancel()
        return
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var side by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    // Offset of the picture's centre from the window's centre, in window pixels. Starts a little towards the top, where faces usually are.
    var offset by remember { mutableStateOf(Offset.Zero) }
    var started by remember { mutableStateOf(false) }

    fun scaleFor(z: Float, s: Float) = s / min(bitmap.width, bitmap.height) * z
    fun clamp(o: Offset, z: Float, s: Float): Offset {
        val k = scaleFor(z, s)
        val mx = max(0f, (bitmap.width * k - s) / 2f)
        val my = max(0f, (bitmap.height * k - s) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Fit the photo", color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val z = (zoom * gestureZoom).coerceIn(1f, 5f)
                                zoom = z
                                offset = clamp(offset + pan, z, size.width.toFloat())
                            }
                        },
                ) {
                    val s = size.width
                    side = s
                    if (!started && s > 0f) {
                        started = true
                        offset = clamp(Offset(0f, max(0f, (bitmap.height * scaleFor(1f, s) - s) / 2f) * 0.6f), 1f, s)
                    }
                    val k = scaleFor(zoom, s)
                    val o = clamp(offset, zoom, s)
                    val w = bitmap.width * k
                    val h = bitmap.height * k
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset((s / 2f + o.x - w / 2f).toInt(), (s / 2f + o.y - h / 2f).toInt()),
                        dstSize = IntSize(w.toInt() + 1, h.toInt() + 1),
                    )
                    // Dim everything outside the circle, then outline it.
                    val m = s * (1f - CIRCLE) / 2f
                    val circle = Path().apply { addOval(Rect(Offset(m, m), Size(s * CIRCLE, s * CIRCLE))) }
                    clipPath(circle, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
                    drawPath(circle, Color.White.copy(alpha = 0.9f), style = Stroke(2.dp.toPx()))
                }
                Slider(
                    value = zoom,
                    onValueChange = { z ->
                        zoom = z
                        if (side > 0f) offset = clamp(offset, z, side)
                    },
                    valueRange = 1f..5f,
                )
                Text(
                    "Drag to move, pinch or use the slider to zoom. Only the part inside the circle is kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        },
        confirmButton = {
            DialogButtons {
                DialogButton("Cancel", onCancel, kind = ButtonKind.Neutral)
                DialogButton(
                    "Use this",
                    {
                        val s = side.coerceAtLeast(1f)
                        val k = scaleFor(zoom, s)
                        val o = clamp(offset, zoom, s)
                        // The window centre sits at this point of the picture; the circle spans CIRCLE of the window.
                        val cx = bitmap.width / 2f - o.x / k
                        val cy = bitmap.height / 2f - o.y / k
                        val crop = s * CIRCLE / k
                        onDone(PhotoCodec.cropped(raw.main, cx - crop / 2f, cy - crop / 2f, crop))
                    },
                    kind = ButtonKind.Primary,
                )
            }
        },
    )
}
