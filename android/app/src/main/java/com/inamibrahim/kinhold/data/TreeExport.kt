package com.inamibrahim.kinhold.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.tree.TreeLayout
import com.inamibrahim.kinhold.tree.TreeMetrics
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Draws the family chart around one person as a PDF page or a PNG picture, always on white with dark text so it prints
 * well whatever theme the app is in. It draws what the tree screen shows (three generations above, two below), with names
 * and years, no photos. One unit of the layout is one point in the PDF and [scale] pixels in the picture.
 */
object TreeExport {
    /** The font the chart is drawn in: the app sets it to its own (Inter), so a chart looks the same whatever font the phone uses. */
    @Volatile
    var typeface: Typeface = Typeface.DEFAULT

    private const val MARGIN = 36f
    private const val TITLE_H = 56f
    private const val FOOTER_H = 30f

    /** The page or picture size in layout units for [layout]. */
    fun sizeOf(layout: TreeLayout): Pair<Float, Float> =
        (layout.width + 2 * MARGIN) to (layout.height + 2 * MARGIN + TITLE_H + FOOTER_H)

    private fun draw(canvas: Canvas, layout: TreeLayout, title: String) {
        val (w, h) = sizeOf(layout)
        canvas.drawColor(Color.WHITE)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 24, 40); textSize = 22f; typeface = Typeface.create(this@TreeExport.typeface, Typeface.BOLD) }
        canvas.drawText(TextUtils.ellipsize(title, titlePaint, w - 2 * MARGIN, TextUtils.TruncateAt.END).toString(), MARGIN, MARGIN + 14f, titlePaint)
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 126, 140); textSize = 10f; typeface = this@TreeExport.typeface }
        canvas.drawText("Made with Kinhold, ${LocalDate.now()}", MARGIN, h - 12f, footer)

        canvas.save()
        canvas.translate(MARGIN, MARGIN + TITLE_H)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 156, 170); strokeWidth = 1.6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        var i = 0
        while (i < layout.edges.size) {
            canvas.drawLine(layout.edges[i], layout.edges[i + 1], layout.edges[i + 2], layout.edges[i + 3], line)
            i += 4
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 24, 40); textSize = 14f; typeface = Typeface.create(this@TreeExport.typeface, Typeface.BOLD) }
        val years = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 96, 110); textSize = 11.5f; typeface = this@TreeExport.typeface }
        val room = TreeMetrics.NODE_W - 20f
        for (p in layout.people) {
            val tint = when (p.person.sex) {
                Sex.Male -> Color.rgb(225, 236, 252)
                Sex.Female -> Color.rgb(252, 230, 240)
                Sex.Unknown -> Color.rgb(236, 238, 242)
            }
            val edge = when (p.person.sex) {
                Sex.Male -> Color.rgb(96, 140, 220)
                Sex.Female -> Color.rgb(214, 100, 150)
                Sex.Unknown -> Color.rgb(150, 156, 170)
            }
            val box = RectF(p.x, p.y, p.x + TreeMetrics.NODE_W, p.y + TreeMetrics.NODE_H)
            fill.color = tint
            canvas.drawRoundRect(box, 14f, 14f, fill)
            border.color = if (p.person.id == layout.focusId) Color.rgb(60, 60, 200) else edge
            border.strokeWidth = if (p.person.id == layout.focusId) 2.4f else 1.2f
            canvas.drawRoundRect(box, 14f, 14f, border)
            canvas.drawText(TextUtils.ellipsize(p.person.name, name, room, TextUtils.TruncateAt.END).toString(), p.x + 10f, p.y + 27f, name)
            val life = p.person.years()
            if (life.isNotEmpty()) canvas.drawText(life, p.x + 10f, p.y + 46f, years)
        }
        canvas.restore()
    }

    /** A single-page PDF of the chart. */
    fun pdf(layout: TreeLayout, title: String): ByteArray {
        val (w, h) = sizeOf(layout)
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt().coerceAtLeast(200), h.toInt().coerceAtLeast(200), 1).create())
            draw(page.canvas, layout, title)
            doc.finishPage(page)
            return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** A PNG picture of the chart, drawn [scale] times larger than the layout and never wider or taller than [maxSide] pixels. */
    fun png(layout: TreeLayout, title: String, scale: Float = 2f, maxSide: Int = 6000): ByteArray {
        val (w, h) = sizeOf(layout)
        val s = minOf(scale, maxSide / w, maxSide / h)
        val bitmap = Bitmap.createBitmap((w * s).toInt().coerceAtLeast(100), (h * s).toInt().coerceAtLeast(100), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(s, s)
            draw(canvas, layout, title)
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
