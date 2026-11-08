package com.inamibrahim.kinhold.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/** A person's photo ready to store: the picture itself and a small square copy for the tree. Both are JPEG. */
class PreparedPhoto(val main: ByteArray, val thumb: ByteArray)

/** What to do with a person's photo when saving the form. */
sealed interface PhotoChange {
    /** Leave the photo as it is. */
    data object Keep : PhotoChange

    data class Set(val photo: PreparedPhoto) : PhotoChange

    data object Remove : PhotoChange
}

/**
 * Turns a picture the person chose into what Kinhold stores. The original stays where it was; Kinhold keeps its own
 * smaller copy (at most [MAIN_LONG_SIDE] pixels on the long side) so a tree of hundreds of photos stays small. Photos
 * taken sideways are turned upright, transparent pictures are put on white, and big pictures are read at a reduced
 * size so even a 50-megapixel photo does not run a weak phone out of memory.
 */
object PhotoCodec {
    const val MAIN_LONG_SIDE = 720
    const val THUMB_SIZE = 256
    private const val QUALITY = 82

    /** Reads, upright-turns, shrinks and compresses the picture at [uri]. Throws [IOException] if it is not a picture. */
    fun prepare(resolver: ContentResolver, uri: Uri): PreparedPhoto {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(resolver, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("That file is not a picture Kinhold can read.")

        // Read at no more than twice the final size, then scale down exactly, which is cheap and keeps the quality.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAIN_LONG_SIDE * 2) sample *= 2
        val decoded = open(resolver, uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IOException("That picture could not be read.")
        val degrees = runCatching { open(resolver, uri).use { rotationOf(it) } }.getOrDefault(0)
        val main = upright(decoded, degrees)
        if (main !== decoded) decoded.recycle()
        return finish(main)
    }

    /**
     * The square the person chose in the crop step: [left], [top] and [side] are in pixels of the picture in [mainJpeg].
     * The result is a square picture of at most [MAIN_LONG_SIDE] pixels and its thumbnail, so the face sits in the circle the same way everywhere.
     */
    fun cropped(mainJpeg: ByteArray, left: Float, top: Float, side: Float): PreparedPhoto {
        val source = BitmapFactory.decodeByteArray(mainJpeg, 0, mainJpeg.size) ?: throw IOException("A picture could not be read.")
        val s = side.coerceIn(1f, min(source.width, source.height).toFloat())
        val l = left.coerceIn(0f, source.width - s)
        val t = top.coerceIn(0f, source.height - s)
        val out = min(MAIN_LONG_SIDE, s.toInt().coerceAtLeast(1))
        val target = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply { postTranslate(-l, -t); postScale(out / s, out / s) }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return finish(target)
    }

    /** The square thumbnail for a stored photo, made again when a backup is restored. */
    fun thumbOf(mainJpeg: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(mainJpeg, 0, mainJpeg.size) ?: throw IOException("A stored photo could not be read.")
        return square(bitmap).also { bitmap.recycle() }
    }

    private fun finish(main: Bitmap): PreparedPhoto {
        val out = ByteArrayOutputStream()
        main.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        val thumb = square(main)
        main.recycle()
        return PreparedPhoto(out.toByteArray(), thumb)
    }

    /** A [THUMB_SIZE] square from the picture. Portraits are cropped towards the top, where faces usually are. */
    private fun square(source: Bitmap): ByteArray {
        val side = min(source.width, source.height)
        val left = (source.width - side) / 2
        val top = if (source.height > source.width) ((source.height - side) * 0.2f).toInt() else 0
        val target = Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            postTranslate(-left.toFloat(), -top.toFloat())
            postScale(THUMB_SIZE.toFloat() / side, THUMB_SIZE.toFloat() / side)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        target.recycle()
        return out.toByteArray()
    }

    /** [source] turned by [degrees] and scaled so its long side is at most [MAIN_LONG_SIDE], on a white background. */
    private fun upright(source: Bitmap, degrees: Int): Bitmap {
        val turned = degrees == 90 || degrees == 270
        val width = if (turned) source.height else source.width
        val height = if (turned) source.width else source.height
        val scale = min(1f, MAIN_LONG_SIDE.toFloat() / max(width, height))
        val outW = max(1, (width * scale).toInt())
        val outH = max(1, (height * scale).toInt())
        val target = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(degrees.toFloat())
            postScale(scale, scale)
            postTranslate(outW / 2f, outH / 2f)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return target
    }

    private fun rotationOf(input: InputStream): Int = when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun open(resolver: ContentResolver, uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw IOException("Could not open that picture.")
}
