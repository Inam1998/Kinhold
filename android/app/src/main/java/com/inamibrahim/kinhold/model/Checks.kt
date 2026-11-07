package com.inamibrahim.kinhold.model

import java.text.Normalizer

/** What a check found wrong or odd. */
enum class CheckKind {
    ChildBeforeParent, ParentTooYoung, ParentDiedBeforeBirth, ParentTooOld, MarriageBeforeBirth, MarriageTooYoung, LoopInTree, NoName, OldWithoutDeath,
}

/** One thing to look at: who it is about and a plain sentence saying what looks wrong. */
class Finding(val kind: CheckKind, val people: List<Person>, val message: String)

/** Two entries that may be the same person, with how sure the match is and why. */
class DuplicatePair(val a: Person, val b: Person, val score: Int, val why: String)

/**
 * Looks for impossible or unlikely data (a child born before a parent, a marriage before someone was born) and for
 * people entered twice. It only reads the tree and never changes it: every finding is a question for the person, not a fix.
 * Pure Kotlin; the work is bounded by the people in the tree, and duplicate search compares only people with the same surname.
 */
object TreeChecks {
    private const val LIMIT = 200

    fun run(family: Family, marriages: List<MarriageDate>, thisYear: Int): List<Finding> {
        val out = ArrayList<Finding>()
        fun add(kind: CheckKind, message: String, vararg people: Person) {
            if (out.size < LIMIT) out += Finding(kind, people.toList(), message)
        }
        for (child in family.people) {
            val born = child.born
            for (parent in family.parentsOf(child.id)) {
                val pb = parent.born
                if (born != null && pb != null) {
                    val gap = born - pb
                    when {
                        gap < 0 -> add(CheckKind.ChildBeforeParent, "${child.name} (born $born) is recorded as the child of ${parent.name}, who was born later ($pb).", child, parent)
                        gap < 12 -> add(CheckKind.ParentTooYoung, "${parent.name} would have been only $gap when ${child.name} was born.", child, parent)
                        parent.sex == Sex.Female && gap > 55 -> add(CheckKind.ParentTooOld, "${parent.name} would have been $gap when ${child.name} was born.", child, parent)
                        parent.sex == Sex.Male && gap > 80 -> add(CheckKind.ParentTooOld, "${parent.name} would have been $gap when ${child.name} was born.", child, parent)
                    }
                }
                val pd = parent.died
                if (born != null && pd != null) {
                    val before = if (parent.sex == Sex.Male) pd < born - 1 else pd < born
                    if (before) add(CheckKind.ParentDiedBeforeBirth, "${parent.name} died in $pd, before ${child.name} was born ($born).", child, parent)
                }
            }
        }
        for (m in marriages) {
            val year = m.date.year ?: continue
            if (m.a !in family || m.b !in family) continue
            val a = family.person(m.a)
            val b = family.person(m.b)
            for (p in listOf(a, b)) {
                val pb = p.born ?: continue
                val other = if (p === a) b else a
                when {
                    year < pb -> add(CheckKind.MarriageBeforeBirth, "${p.name} and ${other.name} are recorded as married in $year, before ${p.name} was born ($pb).", a, b)
                    year - pb < 12 -> add(CheckKind.MarriageTooYoung, "${p.name} would have been only ${year - pb} when married in $year.", a, b)
                }
            }
        }
        findLoop(family)?.let { add(CheckKind.LoopInTree, "${it.joinToString(" and ") { p -> p.name }} are each other's ancestors, which cannot be.", *it.toTypedArray()) }
        for (p in family.people) {
            if (p.given.isBlank() && p.surname.isBlank()) add(CheckKind.NoName, "A person has no name.", p)
            val born = p.born
            if (born != null && born <= thisYear - 110 && p.deathDate.isEmpty) add(CheckKind.OldWithoutDeath, "${p.name} was born in $born and has no death date, so they would be over 110.", p)
        }
        return out
    }

    /** A few people who are their own ancestors (the parent links form a loop), or null. */
    private fun findLoop(family: Family): List<Person>? {
        val state = HashMap<Int, Int>() // 1 = being visited, 2 = done
        var found: List<Int>? = null
        fun visit(id: Int, stack: ArrayList<Int>) {
            if (found != null) return
            state[id] = 1
            stack += id
            for (p in family.parentsOf(id)) {
                when (state[p.id]) {
                    1 -> { found = stack.subList(stack.indexOf(p.id), stack.size).toList(); return }
                    null -> visit(p.id, stack)
                }
                if (found != null) return
            }
            stack.removeAt(stack.size - 1)
            state[id] = 2
        }
        for (p in family.people) {
            if (state[p.id] == null) visit(p.id, ArrayList())
            if (found != null) break
        }
        return found?.map(family::person)?.take(4)
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex("\\s+"), " ")

    /** People who may be entered twice, most likely first. Parents and children, spouses and the like are never offered. */
    fun duplicates(family: Family, limit: Int = 100): List<DuplicatePair> {
        val buckets = HashMap<String, MutableList<Person>>()
        for (p in family.people) buckets.getOrPut(norm(p.surname)) { mutableListOf() } += p
        val out = ArrayList<DuplicatePair>()
        for (list in buckets.values) {
            if (list.size < 2 || list.size > 300) continue
            for (i in list.indices) for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                score(family, a, b)?.let { (s, why) -> if (s >= 4) out += DuplicatePair(a, b, s, why) }
            }
        }
        return out.sortedWith(compareByDescending<DuplicatePair> { it.score }.thenBy { it.a.name }).take(limit)
    }

    private fun score(family: Family, a: Person, b: Person): Pair<Int, String>? {
        val aParents = family.parentsOf(a.id).map { it.id }.toSet()
        val bParents = family.parentsOf(b.id).map { it.id }.toSet()
        if (a.id in bParents || b.id in aParents) return null
        if (family.spousesOf(a.id).any { it.id == b.id }) return null
        val why = ArrayList<String>()
        var s = 0
        val full = norm(a.given) == norm(b.given)
        val loose = norm(a.given).substringBefore(' ') == norm(b.given).substringBefore(' ') && norm(a.given).isNotEmpty()
        when {
            full -> { s += 2; why += "same name" }
            loose -> { s += 1; why += "similar name" }
            else -> return null
        }
        if (a.sex != Sex.Unknown && b.sex != Sex.Unknown && a.sex != b.sex) return null
        val ay = a.born
        val by = b.born
        if (ay != null && by != null) {
            val d = Math.abs(ay - by)
            if (d > 1) return null
            s += if (d == 0) 2 else 1
            why += if (d == 0) "both born $ay" else "born $ay and $by"
        }
        val shared = aParents.intersect(bParents)
        if (shared.isNotEmpty()) { s += 2 * shared.size; why += "the same ${if (shared.size == 1) "parent" else "parents"}" }
        val sharedSpouses = family.spousesOf(a.id).map { it.id }.toSet().intersect(family.spousesOf(b.id).map { it.id }.toSet())
        if (sharedSpouses.isNotEmpty()) { s += 3; why += "the same spouse" }
        if (a.birthPlace.isNotBlank() && a.birthPlace.equals(b.birthPlace, ignoreCase = true)) { s += 1; why += "the same birth place" }
        return s to why.joinToString(", ")
    }
}
