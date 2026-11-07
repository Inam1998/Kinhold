package com.inamibrahim.kinhold.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The colour that goes with a person's sex on their ring and default picture. */
fun toneFor(sex: Sex): Color = when (sex) {
    Sex.Male -> Tones.Sky
    Sex.Female -> Tones.Rose
    Sex.Unknown -> Palette.TextMuted
}

/**
 * Pictures of people, decoded once and kept for reuse. Drawing asks [get]; if the picture is not ready it starts
 * loading it in the background and returns null (the default picture is drawn meanwhile), and [version] changes when
 * it arrives so whatever read it is drawn again. A picture is reloaded when the person's photo is replaced. Only the
 * most recently used [MAX] pictures are kept, so a tree of thousands of photos never holds them all in memory.
 */
@Stable
class AvatarCache(private val scope: CoroutineScope, private val loader: suspend (Int) -> ByteArray?) {
    var version by mutableIntStateOf(0)
        private set

    private class Entry(val stamp: Long, val image: ImageBitmap)

    private val requested = HashMap<Int, Long>()
    private val images = object : LinkedHashMap<Int, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Entry>): Boolean {
            val drop = size > MAX
            if (drop) requested.remove(eldest.key)
            return drop
        }
    }

    /** The picture of [person] if there is one ready, else null (and it is being fetched). Call while drawing. */
    fun get(person: Person): ImageBitmap? {
        @Suppress("UNUSED_VARIABLE") val subscribe = version
        if (!person.hasPhoto) return null
        val entry = images[person.id]
        if (entry != null && entry.stamp == person.photoStamp) return entry.image
        if (requested[person.id] != person.photoStamp) {
            requested[person.id] = person.photoStamp
            scope.launch {
                val bytes = loader(person.id) ?: return@launch
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } ?: return@launch
                images[person.id] = Entry(person.photoStamp, bitmap)
                version++
            }
        }
        // While a replaced photo loads, the old one is better than none.
        return entry?.image
    }

    private companion object {
        const val MAX = 160
    }
}

val LocalAvatars = staticCompositionLocalOf<AvatarCache> { error("No AvatarCache provided") }

/**
 * A round picture: [image] if there is one, otherwise a default avatar (a head and shoulders in the person's colour).
 * It is drawn inside a ring of the same colour. Works in any [DrawScope], including the scaled tree canvas.
 */
fun DrawScope.drawAvatar(center: Offset, radius: Float, image: ImageBitmap?, tone: Color, ring: Float) {
    val inner = radius - ring
    val circle = Path().apply { addOval(Rect(center, inner)) }
    clipPath(circle) {
        if (image != null) {
            withTransform({
                translate(center.x - inner, center.y - inner)
                scale(2 * inner / image.width, 2 * inner / image.height, pivot = Offset.Zero)
            }) { drawImage(image) }
        } else {
            drawCircle(tone.copy(alpha = 0.20f), inner, center)
            val figure = tone.copy(alpha = 0.78f)
            drawCircle(figure, inner * 0.30f, Offset(center.x, center.y - inner * 0.20f))
            drawCircle(figure, inner * 0.64f, Offset(center.x, center.y + inner * 0.98f))
        }
    }
    drawCircle(tone, radius - ring / 2, center, style = Stroke(ring))
}

/** A round picture of [person] as ordinary screen content. */
@Composable
fun Avatar(person: Person, size: Dp, modifier: Modifier = Modifier, ring: Dp = 2.dp) {
    val avatars = LocalAvatars.current
    Canvas(modifier.size(size)) {
        drawAvatar(center, this.size.minDimension / 2f, avatars.get(person), toneFor(person.sex), ring.toPx())
    }
}

/** A round picture from JPEG [bytes] (or the default avatar if null), for a photo that is not saved yet. */
@Composable
fun AvatarPreview(bytes: ByteArray?, sex: Sex, size: Dp, modifier: Modifier = Modifier, ring: Dp = 2.dp) {
    val image = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Canvas(modifier.size(size)) {
        drawAvatar(center, this.size.minDimension / 2f, image, toneFor(sex), ring.toPx())
    }
}
