package com.inamibrahim.kinhold.model

import java.text.Normalizer

/** A piece of a person's record that can differ between two copies of them. */
enum class MergeField(val label: String) {
    Given("First names"),
    Surname("Surname"),
    Sex("Sex"),
    Born("Birth date"),
    BirthPlace("Birth place"),
    Died("Death date"),
    DeathPlace("Death place"),
    Address("Address"),
    Phone("Phone number"),
    Email("Email"),
    Pin("Map pin"),
    Notes("Notes"),
    Photo("Photo"),
}

/** One field on which a person in the incoming file and the same person in your tree differ. */
class FieldChange(
    val incomingId: Int,
    val currentId: Int,
    val personName: String,
    val part: MergeField,
    val mine: String,
    val his: String,
    /** True when your side is empty (or less exact) and the file fills it in; false when both sides have a different value. */
    val isFill: Boolean,
) {
    val key: Pair<Int, MergeField> get() = incomingId to part
}

/** A person in the file who may be someone already in your tree, found by name, birth year and relatives. */
class Candidate(val incomingId: Int, val currentId: Int, val strong: Boolean, val why: String)

/** What a merge will do, once the person's choices are applied. Ids below zero are new people: `-n` is incoming person n. */
class MergeResult(
    /** Incoming person id to the id of the same person in your tree. */
    val matches: Map<Int, Int>,
    /** Changed records of people already in your tree, by their id. */
    val updates: Map<Int, PersonDraft>,
    /** People in your tree who get the file's photo: their id to the incoming person id. */
    val photoFills: Map<Int, Int>,
    /** Incoming ids of people who are not in your tree and will be added. */
    val newPeople: List<Int>,
    val links: List<Pair<Int, Int>>,
    /** The kind of each new link that is not a birth link, by the same (child, parent) references as [links]. */
    val linkKinds: Map<Pair<Int, Int>, LinkKind>,
    val partnerships: List<Pair<Int, Int>>,
    val personEvents: Map<Int, List<Event>>,
    val personSources: Map<Int, List<Source>>,
    val personFields: Map<Int, List<CustomField>>,
    val coupleEvents: Map<Pair<Int, Int>, List<Event>>,
    /** Parent links left out because that person already has two different parents. */
    val skippedParentLinks: Int,
) {
    val eventCount: Int get() = personEvents.values.sumOf { it.size } + coupleEvents.values.sumOf { it.size }
    val sourceCount: Int get() = personSources.values.sumOf { it.size }
    val fieldsAdded: Int get() = personFields.values.sumOf { it.size }
    val fieldCount: Int get() = updates.size + photoFills.size
}

/**
 * Compares a tree arriving from someone else ([incoming], from a share file, a backup or GEDCOM) with the tree on this
 * phone ([current]) and works out how to add what is missing without a second copy of anyone and without overwriting
 * anything unasked. Pure Kotlin.
 *
 * Same person: first by the permanent id both copies share. Then by name, birth year, sex and relatives: a strong match
 * (same name and birth year, or same name and a relative in common) is suggested as the same person, a weaker one is only
 * offered. The person decides on every suggestion and on every field that differs.
 */
class Merge(
    private val current: Family,
    private val currentDetails: TreeDetails,
    private val incoming: BackupData,
) {
    private val incomingById: Map<Int, Person> = incoming.people.associateBy { it.id }
    private val inParents = HashMap<Int, MutableList<Int>>()
    private val inChildren = HashMap<Int, MutableList<Int>>()
    private val inSpouses = HashMap<Int, MutableList<Int>>()

    init {
        for ((c, p) in incoming.parentLinks) {
            inParents.getOrPut(c) { mutableListOf() } += p
            inChildren.getOrPut(p) { mutableListOf() } += c
        }
        for ((a, b) in incoming.partnerships) {
            inSpouses.getOrPut(a) { mutableListOf() } += b
            inSpouses.getOrPut(b) { mutableListOf() } += a
        }
    }

    fun incomingPerson(id: Int): Person = incomingById.getValue(id)

    fun currentPerson(id: Int): Person = current.person(id)

    /** People in the file whose permanent id is also in your tree: certainly the same person. */
    val exact: Map<Int, Int> = run {
        val byUid = HashMap<String, Int>()
        for (p in current.people) if (p.uid.isNotEmpty()) byUid[p.uid] = p.id
        val out = LinkedHashMap<Int, Int>()
        for (p in incoming.people) if (p.uid.isNotEmpty()) byUid[p.uid]?.let { out[p.id] = it }
        out
    }

    /** Suggestions for the rest, best first. Each person in either tree appears at most once. */
    val candidates: List<Candidate> = findCandidates()

    private class Scored(val score: Int, val p: Person, val c: Person, val why: String)

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex("\\s+"), " ")

    private fun firstGiven(p: Person) = norm(p.given).substringBefore(' ')

    private fun findCandidates(): List<Candidate> {
        val takenCurrent = exact.values.toHashSet()
        val bySurname = HashMap<String, MutableList<Person>>()
        for (p in current.people) if (p.id !in takenCurrent) bySurname.getOrPut(norm(p.surname)) { mutableListOf() } += p
        val open = incoming.people.filter { it.id !in exact }
        var known: Map<Int, Int> = exact
        var result: List<Candidate> = emptyList()
        // A suggestion made with confidence can vouch for a neighbour, so score again a few times.
        repeat(3) {
            val scored = ArrayList<Scored>()
            for (p in open) {
                for (c in bySurname[norm(p.surname)].orEmpty()) {
                    val (score, why) = score(p, c, known) ?: continue
                    if (score >= 2) scored += Scored(score, p, c, why)
                }
            }
            val usedIn = HashSet<Int>()
            val usedCur = HashSet<Int>()
            val picked = ArrayList<Candidate>()
            for (s in scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.p.id })) {
                if (s.p.id in usedIn || s.c.id in usedCur) continue
                usedIn += s.p.id
                usedCur += s.c.id
                picked += Candidate(s.p.id, s.c.id, s.score >= STRONG, s.why)
            }
            result = picked
            known = exact + picked.filter { it.strong }.associate { it.incomingId to it.currentId }
        }
        return result
    }

    /** A score for "this incoming person is that person of yours", with the reasons in words, or null if they cannot be the same. */
    private fun score(p: Person, c: Person, known: Map<Int, Int>): Pair<Int, String>? {
        val why = ArrayList<String>()
        var score = 0
        val fullName = norm(p.given) == norm(c.given) && norm(p.surname) == norm(c.surname)
        when {
            fullName -> {
                score += 2
                why += "same name"
            }
            firstGiven(p) == firstGiven(c) && firstGiven(p).isNotEmpty() -> {
                score += 1
                why += "similar name"
            }
            else -> return null
        }
        if (p.sex != Sex.Unknown && c.sex != Sex.Unknown && p.sex != c.sex) return null
        val by = p.born
        val cy = c.born
        if (by != null && cy != null) {
            val diff = Math.abs(by - cy)
            if (diff > 1) return null
            score += if (diff == 0) 2 else 1
            why += if (diff == 0) "both born $by" else "born $by and $cy"
        }
        if (p.deathDate.year != null && c.deathDate.year != null && Math.abs(p.deathDate.year!! - c.deathDate.year!!) > 1) return null
        val shared = sharedRelatives(p.id, c.id, known)
        if (shared.isNotEmpty()) {
            score += 3
            why += "both have " + shared.take(2).joinToString(" and ") + " as " + (if (shared.size > 1) "relatives" else "a relative")
        }
        return score to why.joinToString(", ")
    }

    /** Names of relatives of the incoming person that are, by [known], the same people as relatives of your person. */
    private fun sharedRelatives(incomingId: Int, currentId: Int, known: Map<Int, Int>): List<String> {
        val out = ArrayList<String>()
        fun check(theirs: List<Int>, mine: List<Person>) {
            val mineIds = mine.map { it.id }.toSet()
            for (t in theirs) {
                val mapped = known[t] ?: continue
                if (mapped in mineIds) out += current.person(mapped).name
            }
        }
        check(inParents[incomingId].orEmpty(), current.parentsOf(currentId))
        check(inChildren[incomingId].orEmpty(), current.childrenOf(currentId))
        check(inSpouses[incomingId].orEmpty(), current.spousesOf(currentId))
        return out
    }

    /** The people matched for sure, plus the suggestions the person accepted (by incoming id). */
    fun matchesFor(accepted: Set<Int>): Map<Int, Int> {
        val out = LinkedHashMap(exact)
        for (c in candidates) if (c.incomingId in accepted) out[c.incomingId] = c.currentId
        return out
    }

    /** Every field on which a matched person differs, for the choices screen. */
    fun fieldChanges(matches: Map<Int, Int>): List<FieldChange> {
        val out = ArrayList<FieldChange>()
        for ((incomingId, currentId) in matches) {
            val his = incomingById[incomingId] ?: continue
            val mine = current.person(currentId)
            val name = mine.name
            fun text(field: MergeField, m: String, h: String) {
                val mm = m.trim()
                val hh = h.trim()
                if (hh.isEmpty() || mm.equals(hh, ignoreCase = true) || (field == MergeField.Notes && mm.contains(hh))) return
                out += FieldChange(incomingId, currentId, name, field, mm, hh, isFill = mm.isEmpty())
            }
            text(MergeField.Given, mine.given, his.given)
            text(MergeField.Surname, mine.surname, his.surname)
            if (his.sex != Sex.Unknown && his.sex != mine.sex) {
                out += FieldChange(incomingId, currentId, name, MergeField.Sex, mine.sex.label(), his.sex.label(), isFill = mine.sex == Sex.Unknown)
            }
            date(out, incomingId, currentId, name, MergeField.Born, mine.birthDate, his.birthDate)
            text(MergeField.BirthPlace, mine.birthPlace, his.birthPlace)
            date(out, incomingId, currentId, name, MergeField.Died, mine.deathDate, his.deathDate)
            text(MergeField.DeathPlace, mine.deathPlace, his.deathPlace)
            text(MergeField.Address, mine.address, his.address)
            text(MergeField.Phone, mine.phone, his.phone)
            text(MergeField.Email, mine.email, his.email)
            if (his.lat != null && his.lng != null && (mine.lat != his.lat || mine.lng != his.lng)) {
                val m = if (mine.lat != null && mine.lng != null) GeoPin(mine.lat, mine.lng).text() else ""
                out += FieldChange(incomingId, currentId, name, MergeField.Pin, m.ifEmpty { "none" }, GeoPin(his.lat, his.lng).text(), isFill = m.isEmpty())
            }
            text(MergeField.Notes, mine.notes, his.notes)
            if (!mine.hasPhoto && incoming.photos.containsKey(incomingId)) {
                out += FieldChange(incomingId, currentId, name, MergeField.Photo, "no photo", "a photo", isFill = true)
            }
        }
        return out
    }

    private fun date(out: MutableList<FieldChange>, incomingId: Int, currentId: Int, name: String, field: MergeField, mine: PartialDate, his: PartialDate) {
        if (his.isEmpty || mine == his) return
        val refinement = mine.isEmpty || (
            mine.year == his.year && (mine.month == null || mine.month == his.month) && (mine.day == null || mine.day == his.day)
            )
        out += FieldChange(incomingId, currentId, name, field, mine.text().ifEmpty { "empty" }, his.text(), isFill = refinement)
    }

    private fun Sex.label() = when (this) {
        Sex.Male -> "Male"
        Sex.Female -> "Female"
        Sex.Unknown -> "Not stated"
    }

    /**
     * What to do for these choices: [accepted] are the suggestions taken as the same person, [useHis] the fields (incoming
     * person, field) where the file's value replaces yours, and [addEvents] whether events and sources the file has and you
     * do not are added to people who are in both trees. People only in the file are always added with their own.
     */
    fun resolve(accepted: Set<Int>, useHis: Set<Pair<Int, MergeField>>, addEvents: Boolean): MergeResult {
        val matches = matchesFor(accepted)
        fun ref(incomingId: Int): Int = matches[incomingId] ?: -incomingId

        val updates = LinkedHashMap<Int, PersonDraft>()
        val photoFills = LinkedHashMap<Int, Int>()
        for (change in fieldChanges(matches)) {
            if (change.key !in useHis) continue
            val his = incomingById.getValue(change.incomingId)
            if (change.part == MergeField.Photo) {
                photoFills[change.currentId] = change.incomingId
                continue
            }
            val base = updates[change.currentId] ?: current.person(change.currentId).toDraft()
            updates[change.currentId] = apply(base, change.part, his)
        }

        val newPeople = incoming.people.filter { it.id !in matches }.map { it.id }

        // Parent links: at most two different parents per child, as the tree can only show two.
        val parentsNow = HashMap<Int, MutableSet<Int>>()
        val links = ArrayList<Pair<Int, Int>>()
        val linkKinds = HashMap<Pair<Int, Int>, LinkKind>()
        var skipped = 0
        for ((c, p) in incoming.parentLinks) {
            val rc = ref(c)
            val rp = ref(p)
            val set = parentsNow.getOrPut(rc) { if (rc > 0) current.parentsOf(rc).map { it.id }.toMutableSet() else mutableSetOf() }
            if (rp in set) continue
            if (set.size >= Family.MAX_PARENTS) {
                skipped++
                continue
            }
            set += rp
            links += rc to rp
            incoming.linkKinds[c to p]?.let { linkKinds[rc to rp] = it }
        }
        val partnerships = LinkedHashSet<Pair<Int, Int>>()
        for ((a, b) in incoming.partnerships) {
            val ra = ref(a)
            val rb = ref(b)
            if (ra > 0 && rb > 0 && current.spousesOf(ra).any { it.id == rb }) continue
            partnerships += minOf(ra, rb) to maxOf(ra, rb)
        }

        val personEvents = LinkedHashMap<Int, List<Event>>()
        val personSources = LinkedHashMap<Int, List<Source>>()
        val personFields = LinkedHashMap<Int, List<CustomField>>()
        for (p in incoming.people) {
            val r = ref(p.id)
            val isNew = r < 0
            if (!isNew && !addEvents) continue
            val mineEvents = if (isNew) emptyList() else currentDetails.events[r].orEmpty()
            val mineSources = if (isNew) emptyList() else currentDetails.sources[r].orEmpty()
            val newEvents = incoming.details.events[p.id].orEmpty().filter { e -> mineEvents.none { eventKey(it) == eventKey(e) } }
            val newSources = incoming.details.sources[p.id].orEmpty().filter { s -> mineSources.none { sourceKey(it) == sourceKey(s) } }
            if (newEvents.isNotEmpty()) personEvents[r] = newEvents
            if (newSources.isNotEmpty()) personSources[r] = newSources
            val mineFields = if (isNew) emptyList() else currentDetails.fields[r].orEmpty()
            val newFields = incoming.details.fields[p.id].orEmpty().filter { f -> mineFields.none { fieldKey(it) == fieldKey(f) } }
            if (newFields.isNotEmpty()) personFields[r] = newFields
        }
        val coupleEvents = LinkedHashMap<Pair<Int, Int>, List<Event>>()
        for ((pair, events) in incoming.details.couples) {
            val ra = ref(pair.first)
            val rb = ref(pair.second)
            val bothHere = ra > 0 && rb > 0
            if (bothHere && !addEvents) continue
            val key = minOf(ra, rb) to maxOf(ra, rb)
            val mine = if (bothHere) currentDetails.couples[key].orEmpty() else emptyList()
            val fresh = events.filter { e -> mine.none { eventKey(it) == eventKey(e) } }
            if (fresh.isNotEmpty()) coupleEvents[key] = fresh
        }
        return MergeResult(matches, updates, photoFills, newPeople, links, linkKinds, partnerships.toList(), personEvents, personSources, personFields, coupleEvents, skipped)
    }

    private fun eventKey(e: Event) = listOf(e.kind.key, e.detail.trim().lowercase(), e.date.text(), e.place.trim().lowercase())

    private fun fieldKey(f: CustomField) = listOf(f.label.trim().lowercase(), f.value.trim().lowercase())

    private fun sourceKey(s: Source) = listOf(s.title.trim().lowercase(), s.citation.trim().lowercase())

    /** [base] with one field taken from [his]. */
    private fun apply(base: PersonDraft, field: MergeField, his: Person): PersonDraft = PersonDraft(
        given = if (field == MergeField.Given) his.given else base.given,
        surname = if (field == MergeField.Surname) his.surname else base.surname,
        sex = if (field == MergeField.Sex) his.sex else base.sex,
        born = if (field == MergeField.Born) his.born else base.born,
        died = if (field == MergeField.Died) his.died else base.died,
        birthPlace = if (field == MergeField.BirthPlace) his.birthPlace else base.birthPlace,
        deathPlace = if (field == MergeField.DeathPlace) his.deathPlace else base.deathPlace,
        notes = if (field == MergeField.Notes) {
            listOf(base.notes, his.notes).filter { it.isNotBlank() }.joinToString("\n\n")
        } else {
            base.notes
        },
        birthMonth = if (field == MergeField.Born) his.birthMonth else base.birthMonth,
        birthDay = if (field == MergeField.Born) his.birthDay else base.birthDay,
        deathMonth = if (field == MergeField.Died) his.deathMonth else base.deathMonth,
        deathDay = if (field == MergeField.Died) his.deathDay else base.deathDay,
        uid = base.uid,
        address = if (field == MergeField.Address) his.address else base.address,
        phone = if (field == MergeField.Phone) his.phone else base.phone,
        email = if (field == MergeField.Email) his.email else base.email,
        lat = if (field == MergeField.Pin) his.lat else base.lat,
        lng = if (field == MergeField.Pin) his.lng else base.lng,
    )

    companion object {
        /** A score at or above this is suggested as the same person; below it, only offered. */
        const val STRONG = 4
    }
}
