package com.inamibrahim.kinhold.model

/**
 * How one person is related to another, in plain English and, where there is a common family word, in Urdu and Punjabi
 * (chacha, mamu, phupho, khala and so on). [english] reads from the first person's side: "Uncle" means the second person
 * is the first person's uncle. [local] is null when there is no such word or the family tree does not say enough (for
 * example, the parent's sex is not recorded). [path] names the people between them, from first to second.
 */
class Relationship(
    val english: String,
    val local: String?,
    val path: List<Person>,
    val blood: Boolean,
    val kin: Kin? = null,
    /** Where the common ancestor sits in [path], and the others who are the same distance from both people (the other parent, the other grandparent), or -1 and none. */
    val pivot: Int = -1,
    val coPivots: List<Person> = emptyList(),
) {
    /** The people between the two, from first to second, each as a name; the common ancestor is shown with the others the same way ("Ibrahim / Atifa"). */
    fun pathNames(): List<String> = path.mapIndexed { i, p ->
        if (i == pivot && coPivots.isNotEmpty()) (listOf(p) + coPivots).joinToString(" / ") { it.name } else p.name
    }

    /** The family word in the chosen language: the Urdu and Punjabi words (which also cover in-laws), or the word for [kin] in another language; null when there is none. */
    fun wordIn(language: KinLanguage): String? = if (language == KinLanguage.UrduPunjabi) local else kin?.let { KinWords.of(language, it) }
}

/** Works out relationships from the parent and spouse links. Pure Kotlin; the cost depends on the family's size around the two people, not the whole tree. */
object Relationships {
    private class Reach(val steps: Int, val via: Int?, val viaPath: List<Int>)

    /** Everyone above [start] (parents, grandparents and so on), with how many generations up, the first parent on the way, and the way itself. */
    private fun ancestors(family: Family, start: Int): Map<Int, Reach> {
        val out = HashMap<Int, Reach>()
        out[start] = Reach(0, null, listOf(start))
        var level = listOf(start)
        var steps = 0
        while (level.isNotEmpty() && steps < 12) {
            steps++
            val next = ArrayList<Int>()
            for (id in level) for (p in family.lineageParentsOf(id)) {
                if (p.id in out) continue
                val first = out.getValue(id).via ?: p.id
                out[p.id] = Reach(steps, first, out.getValue(id).viaPath + p.id)
                next += p.id
            }
            level = next
        }
        return out
    }

    /** The relationship of [to] to [from], or null if the tree does not connect them. */
    fun between(family: Family, from: Int, to: Int): Relationship? {
        if (from !in family || to !in family) return null
        val a = family.person(from)
        val b = family.person(to)
        if (from == to) return Relationship("The same person", null, listOf(a), true)
        bloodTerm(family, from, to)?.let { return it }
        // Through marriage: the second person is the first person's spouse, a spouse's relative, or a relative's spouse.
        if (family.spousesOf(from).any { it.id == to }) {
            return Relationship(if (b.sex == Sex.Male) "Husband" else if (b.sex == Sex.Female) "Wife" else "Spouse", spouseWord(b.sex), listOf(a, b), false, if (b.sex == Sex.Male) Kin.Husband else if (b.sex == Sex.Female) Kin.Wife else null)
        }
        for (s in family.spousesOf(from)) {
            val r = bloodTerm(family, s.id, to) ?: continue
            val local = inLawWord(r, s.sex)
            return Relationship("Spouse's ${r.english.lowercase()} (in-law)", local, listOf(a, s) + r.path.drop(1), false)
        }
        for (s in family.spousesOf(to)) {
            val r = bloodTerm(family, from, s.id) ?: continue
            return Relationship("Spouse of your ${r.english.lowercase()}", spouseOfRelativeWord(r, b.sex), r.path + b, false)
        }
        return null
    }

    private fun spouseWord(sex: Sex) = when (sex) {
        Sex.Male -> "Shauhar"
        Sex.Female -> "Biwi"
        Sex.Unknown -> null
    }

    private fun inLawWord(r: Relationship, spouseSex: Sex): String? = when (r.english) {
        "Father" -> "Susar (father-in-law)"
        "Mother" -> "Saas (mother-in-law)"
        "Brother" -> if (spouseSex == Sex.Female) "Saala (wife's brother)" else "Devar or Jeth (husband's brother)"
        "Sister" -> if (spouseSex == Sex.Female) "Saali (wife's sister)" else "Nanad (husband's sister)"
        else -> null
    }

    private fun spouseOfRelativeWord(r: Relationship, sex: Sex): String? = when (r.local) {
        "Chacha", "Tau", "Chacha or Tau" -> if (sex == Sex.Female) "Chachi (or Tai)" else null
        "Mamu" -> if (sex == Sex.Female) "Mami" else null
        "Phupho (Phuppi)" -> if (sex == Sex.Male) "Phupha" else null
        "Khala" -> if (sex == Sex.Male) "Khalu" else null
        "Bhai" -> if (sex == Sex.Female) "Bhabhi" else null
        "Behan" -> if (sex == Sex.Male) "Behnoi (Bahnoi)" else null
        "Beta" -> if (sex == Sex.Female) "Bahu" else null
        "Beti" -> if (sex == Sex.Male) "Damad" else null
        else -> null
    }

    /** "Great-" in front of a word, once for each step beyond the plain word, with the word then in lower case. */
    private fun greats(n: Int, word: String) = if (n <= 0) word else "Great-".repeat(n) + word.replaceFirstChar(Char::lowercase)

    private fun ordinal(n: Int) = when (n) {
        1 -> "First"
        2 -> "Second"
        3 -> "Third"
        4 -> "Fourth"
        5 -> "Fifth"
        else -> "${n}th"
    }

    private fun bloodTerm(family: Family, from: Int, to: Int): Relationship? {
        val up = ancestors(family, from)
        val down = ancestors(family, to)
        var best: Int? = null
        var bestSum = Int.MAX_VALUE
        for ((id, r) in up) {
            val d = down[id] ?: continue
            val sum = r.steps + d.steps
            if (sum < bestSum) { bestSum = sum; best = id }
        }
        val common = best ?: return null
        val a = up.getValue(common).steps
        val b = down.getValue(common).steps
        val person = family.person(to)
        val sex = person.sex
        // The people on the way: from `from` up to the common ancestor and down to `to`.
        val pathIds = up.getValue(common).viaPath + down.getValue(common).viaPath.reversed().drop(1)
        val path = pathIds.map(family::person)

        fun gendered(m: String, f: String, n: String) = when (sex) { Sex.Male -> m; Sex.Female -> f; Sex.Unknown -> n }
        val firstUp = up.getValue(common).via?.let(family::person)            // the first person's own parent on the way up
        val firstDown = down.getValue(common).viaPath.let { if (it.size >= 2) family.person(it[1]) else null } // the second person's own parent on the way
        val english: String
        var local: String? = null
        when {
            a == 0 && b >= 1 -> {
                english = if (b == 1) gendered("Son", "Daughter", "Child") else greats(b - 2, gendered("Grandson", "Granddaughter", "Grandchild"))
                if (b == 1) local = gendered("Beta", "Beti", "Beta or Beti")
                if (b == 2) {
                    val childOfFrom = down.getValue(common).viaPath.getOrNull(down.getValue(common).viaPath.size - 2)?.let(family::person) // the first person's child on the way
                    local = when (childOfFrom?.sex) {
                        Sex.Male -> gendered("Pota", "Poti", "Pota or Poti")
                        Sex.Female -> gendered("Nawasa", "Nawasi", "Nawasa or Nawasi")
                        else -> null
                    }
                }
            }
            b == 0 && a >= 1 -> {
                english = if (a == 1) gendered("Father", "Mother", "Parent") else greats(a - 2, gendered("Grandfather", "Grandmother", "Grandparent"))
                if (a == 1) local = when (sex) { Sex.Male -> "Abbu (Walid)"; Sex.Female -> "Ammi (Walida)"; Sex.Unknown -> null }
                if (a == 2) {
                    val paternal = firstUp?.sex == Sex.Male
                    val known = firstUp?.sex != Sex.Unknown && firstUp != null && sex != Sex.Unknown
                    if (known) local = if (paternal) gendered("Dada", "Dadi", "") else gendered("Nana", "Nani", "")
                }
                if (a == 3 && sex != Sex.Unknown && firstUp != null && firstUp.sex != Sex.Unknown) {
                    local = if (firstUp.sex == Sex.Male) gendered("Pardada", "Pardadi", "") else gendered("Parnana", "Parnani", "")
                }
            }
            a == 1 && b == 1 -> {
                val shared = family.parentsOf(from).map { it.id }.toSet().intersect(family.parentsOf(to).map { it.id }.toSet())
                val half = shared.size < 2 && family.parentsOf(from).size == 2 && family.parentsOf(to).size == 2
                english = (if (half) "Half-" else "") + gendered("brother", "sister", "sibling").let { if (half) it else it.replaceFirstChar(Char::uppercase) }
                local = gendered("Bhai", "Behan", "Bhai or Behan").takeIf { sex != Sex.Unknown }
            }
            a == 1 && b >= 2 -> {
                english = greats(b - 2, gendered("Nephew", "Niece", "Nephew or niece"))
                if (b == 2) {
                    local = when (firstDown?.sex) {
                        Sex.Male -> gendered("Bhatija", "Bhatiji", "")
                        Sex.Female -> gendered("Bhanja", "Bhanji", "")
                        else -> null
                    }?.takeIf { sex != Sex.Unknown }
                }
            }
            a >= 2 && b == 1 -> {
                english = greats(a - 2, gendered("Uncle", "Aunt", "Uncle or aunt"))
                if (a == 2 && sex != Sex.Unknown && firstUp?.sex != null && firstUp.sex != Sex.Unknown) {
                    local = if (firstUp.sex == Sex.Male) {
                        if (sex == Sex.Male) {
                            val older = person.born != null && firstUp.born != null && person.born < firstUp.born
                            val younger = person.born != null && firstUp.born != null && person.born > firstUp.born
                            if (older) "Tau" else if (younger) "Chacha" else "Chacha or Tau"
                        } else {
                            "Phupho (Phuppi)"
                        }
                    } else {
                        if (sex == Sex.Male) "Mamu" else "Khala"
                    }
                }
            }
            else -> {
                val degree = minOf(a, b) - 1
                val removed = Math.abs(a - b)
                english = "${ordinal(degree)} cousin" + when (removed) { 0 -> ""; 1 -> ", once removed"; 2 -> ", twice removed"; else -> ", $removed times removed" }
                if (a == 2 && b == 2 && firstUp != null && firstDown != null && sex != Sex.Unknown && firstUp.sex != Sex.Unknown && firstDown.sex != Sex.Unknown) {
                    val family0 = when {
                        firstUp.sex == Sex.Male && firstDown.sex == Sex.Male -> "Chachazad"
                        firstUp.sex == Sex.Male && firstDown.sex == Sex.Female -> "Phupizad"
                        firstUp.sex == Sex.Female && firstDown.sex == Sex.Male -> "Mamuzad"
                        else -> "Khalazad"
                    }
                    local = "$family0 ${gendered("bhai", "behan", "")}"
                }
            }
        }
        // The same relationship as a language-neutral key, for the family words of languages other than Urdu and Punjabi. It is only set
        // when the tree says enough (the person's sex, and the sex of the parent the relationship runs through).
        val male = sex == Sex.Male
        val viaPath = down.getValue(common).viaPath
        val childOfFromSex = viaPath.getOrNull(viaPath.size - 2)?.let(family::person)?.sex
        val kin: Kin? = when {
            sex == Sex.Unknown -> null
            a == 0 && b == 1 -> if (male) Kin.Son else Kin.Daughter
            a == 0 && b == 2 -> when (childOfFromSex) {
                Sex.Male -> if (male) Kin.SonsSon else Kin.SonsDaughter
                Sex.Female -> if (male) Kin.DaughtersSon else Kin.DaughtersDaughter
                else -> null
            }
            b == 0 && a == 1 -> if (male) Kin.Father else Kin.Mother
            b == 0 && a == 2 -> when (firstUp?.sex) {
                Sex.Male -> if (male) Kin.PatGrandfather else Kin.PatGrandmother
                Sex.Female -> if (male) Kin.MatGrandfather else Kin.MatGrandmother
                else -> null
            }
            a == 1 && b == 1 -> if (male) Kin.Brother else Kin.Sister
            a == 1 && b == 2 -> when (firstDown?.sex) {
                Sex.Male -> if (male) Kin.BrothersSon else Kin.BrothersDaughter
                Sex.Female -> if (male) Kin.SistersSon else Kin.SistersDaughter
                else -> null
            }
            a == 2 && b == 1 -> when (firstUp?.sex) {
                Sex.Male -> if (!male) Kin.FathersSister else {
                    val mine = person.born
                    val dads = firstUp?.born
                    if (mine != null && dads != null && mine < dads) Kin.FathersElderBrother
                    else if (mine != null && dads != null && mine > dads) Kin.FathersYoungerBrother
                    else Kin.FathersBrother
                }
                Sex.Female -> if (male) Kin.MothersBrother else Kin.MothersSister
                else -> null
            }
            a == 2 && b == 2 -> when (firstUp?.sex to firstDown?.sex) {
                Sex.Male to Sex.Male -> if (male) Kin.CousinPatUncleSon else Kin.CousinPatUncleDaughter
                Sex.Male to Sex.Female -> if (male) Kin.CousinPatAuntSon else Kin.CousinPatAuntDaughter
                Sex.Female to Sex.Male -> if (male) Kin.CousinMatUncleSon else Kin.CousinMatUncleDaughter
                Sex.Female to Sex.Female -> if (male) Kin.CousinMatAuntSon else Kin.CousinMatAuntDaughter
                else -> null
            }
            else -> null
        }
        // Others at the same distance from both people: for brothers and sisters the other parent, for cousins the other grandparent.
        val coPivots = up.filter { (id, r) -> id != common && r.steps == a && down[id]?.steps == b }.keys.map(family::person)
        return Relationship(english, local?.takeIf { it.isNotBlank() }, path, blood = true, kin = kin, pivot = up.getValue(common).viaPath.size - 1, coPivots = coPivots)
    }
}
