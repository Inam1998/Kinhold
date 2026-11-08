package com.inamibrahim.kinhold.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.Sex
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** One page of the sheet: two partners (or one parent alone), the marriage if one is recorded, and their children. */
class SheetFamily(val first: Person, val second: Person?, val marriage: Event?, val children: List<Person>)

/**
 * A family group sheet: for one person, a page for each partner with the two of them, the marriage, and their children with
 * dates and places, the way genealogists print and keep them. Pure drawing code over what is already in memory.
 */
object FamilySheet {
    private const val W = 595
    private const val H = 842
    private const val M = 44f
    private const val ROW = 26f

    /** The families to put on the sheet for [person]: one per partner (children they had together), then one for children with no partner recorded. */
    fun familiesOf(family: Family, person: Person, details: PersonDetails): List<SheetFamily> {
        val out = ArrayList<SheetFamily>()
        val children = family.childrenOf(person.id)
        val placed = HashSet<Int>()
        for (spouse in family.spousesInOrder(person.id)) {
            val together = children.filter { c -> family.parentsOf(c.id).any { it.id == spouse.id } }
            placed += together.map { it.id }
            val marriage = details.coupleEvents[spouse.id].orEmpty().firstOrNull { it.kind == EventKind.Marriage }
            out += SheetFamily(person, spouse, marriage, together)
        }
        val rest = children.filter { it.id !in placed }
        if (rest.isNotEmpty() || out.isEmpty()) out += SheetFamily(person, null, null, rest)
        return out
    }

    /** Husband first, then wife; when the sex is not recorded the person the sheet is for comes first. */
    private fun ordered(f: SheetFamily): Pair<Person, Person?> {
        val b = f.second ?: return f.first to null
        return if (f.first.sex == Sex.Female && b.sex == Sex.Male) b to f.first else f.first to b
    }

    private fun life(date: String, place: String): String = listOf(date, place).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "not recorded" }

    fun pdf(title: String, families: List<SheetFamily>, typeface: Typeface = Typeface.DEFAULT): ByteArray {
        val ink = Color.rgb(20, 24, 40)
        val muted = Color.rgb(100, 106, 120)
        val bold = Typeface.create(typeface, Typeface.BOLD)
        val h1 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 20f; this.typeface = bold }
        val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 10.5f; this.typeface = bold; letterSpacing = 0.08f }
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 14f; this.typeface = bold }
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11.5f; this.typeface = typeface }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 9.5f; this.typeface = typeface }
        val rule = Paint().apply { color = Color.rgb(200, 204, 214); strokeWidth = 1f }

        val doc = PdfDocument()
        var pageNo = 0
        try {
            fun startPage(): Pair<PdfDocument.Page, Canvas> {
                pageNo++
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(TextUtils.ellipsize(title, h1, W - 2 * M, TextUtils.TruncateAt.END).toString(), M, M + 14f, h1)
                page.canvas.drawText("Made with Kinhold, ${LocalDate.now()}", M, H - 24f, small)
                return page to page.canvas
            }

            fun person(c: Canvas, role: String, p: Person?, y0: Float): Float {
                var y = y0
                c.drawText(role, M, y, h2)
                y += 18f
                if (p == null) {
                    c.drawText("not recorded", M, y, text.apply { color = muted })
                    text.color = ink
                    return y + 24f
                }
                c.drawText(TextUtils.ellipsize(p.name, name, W - 2 * M, TextUtils.TruncateAt.END).toString(), M, y, name)
                y += 18f
                c.drawText("Born: " + life(p.birthDate.text(), p.birthPlace), M, y, text)
                y += 15f
                c.drawText("Died: " + life(p.deathDate.text(), p.deathPlace).let { if (p.deathDate.isEmpty && p.deathPlace.isBlank()) "" else it }.ifEmpty { "not recorded" }, M, y, text)
                return y + 24f
            }

            for (family in families) {
                val (a, b) = ordered(family)
                var (page, canvas) = startPage()
                var y = M + 52f
                y = person(canvas, if (b != null || a.sex == Sex.Male) "HUSBAND" else "PARENT", a, y)
                if (b != null) y = person(canvas, "WIFE", b, y)
                canvas.drawLine(M, y - 10f, W - M, y - 10f, rule)
                canvas.drawText("MARRIAGE", M, y + 6f, h2)
                val m = family.marriage
                canvas.drawText(
                    if (m != null) life(m.date.text(), m.place) else if (b != null) "not recorded" else "no partner recorded",
                    M, y + 24f, text,
                )
                y += 52f
                canvas.drawLine(M, y - 12f, W - M, y - 12f, rule)
                canvas.drawText("CHILDREN", M, y + 4f, h2)
                y += 22f
                fun header(c: Canvas, at: Float) {
                    c.drawText("Name", M + 22f, at, small)
                    c.drawText("Born", M + 270f, at, small)
                    c.drawText("Died", M + 400f, at, small)
                }
                header(canvas, y)
                y += 12f
                if (family.children.isEmpty()) canvas.drawText("No children recorded.", M, y + 14f, text)
                family.children.forEachIndexed { i, child ->
                    if (y + ROW > H - 56f) {
                        doc.finishPage(page)
                        val next = startPage()
                        page = next.first
                        canvas = next.second
                        y = M + 56f
                        header(canvas, y)
                        y += 12f
                    }
                    canvas.drawText("${i + 1}", M, y + 15f, small)
                    canvas.drawText(TextUtils.ellipsize(child.name, text, 236f, TextUtils.TruncateAt.END).toString(), M + 22f, y + 15f, text)
                    canvas.drawText(TextUtils.ellipsize(life(child.birthDate.text(), child.birthPlace).let { if (child.birthDate.isEmpty && child.birthPlace.isBlank()) "" else it }, text, 124f, TextUtils.TruncateAt.END).toString(), M + 270f, y + 15f, text)
                    canvas.drawText(TextUtils.ellipsize(child.deathDate.text(), text, 100f, TextUtils.TruncateAt.END).toString(), M + 400f, y + 15f, text)
                    canvas.drawLine(M, y + ROW - 5f, W - M, y + ROW - 5f, rule)
                    y += ROW
                }
                doc.finishPage(page)
            }
            return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        } finally {
            doc.close()
        }
    }
}
