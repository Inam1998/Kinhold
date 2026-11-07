package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex

private enum class QuickKind(val label: String) { Child("Child"), Spouse("Spouse"), Parent("Parent"), Sibling("Sibling") }

/** Splits a typed name: the last word is the surname when there are two or more words. */
internal fun splitName(text: String): Pair<String, String> {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (words.size >= 2) words.dropLast(1).joinToString(" ") to words.last() else (words.firstOrNull().orEmpty()) to ""
}

/**
 * Adds a relative to [personId] from just a name. Choose Child, Spouse, Parent or Sibling, type the name and Add; the
 * box stays open so several people can be added in a row. Sex, surname and links follow the same defaults as the full form.
 */
@Composable
fun QuickAddDialog(family: Family, personId: Int, suggest: (Relation) -> PersonDraft, onAdd: (PersonDraft, Relation) -> Unit, onClose: () -> Unit) {
    val me = family.person(personId)
    val parents = family.parentsOf(personId)
    val spouses = family.spousesInOrder(personId)
    var kind by rememberSaveable { mutableStateOf("") } // nothing is chosen until the person picks one
    var name by rememberSaveable { mutableStateOf("") }
    var otherParent by rememberSaveable { mutableStateOf(-1) } // -1 = the only or no spouse
    var added by rememberSaveable { mutableStateOf(listOf<String>()) }
    val k = QuickKind.entries.firstOrNull { it.name == kind }
    val kinds = QuickKind.entries.filter { (it != QuickKind.Parent || parents.size < Family.MAX_PARENTS) && (it != QuickKind.Sibling || parents.isNotEmpty()) }

    fun relation(): Relation? = when (k) {
        null -> null
        QuickKind.Child -> Relation.ChildOf(personId, if (spouses.size == 1) spouses[0].id else otherParent.takeIf { it >= 0 })
        QuickKind.Spouse -> Relation.SpouseOf(personId)
        QuickKind.Parent -> Relation.ParentOf(personId)
        QuickKind.Sibling -> siblingRelation(family, personId)!!
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ToneIcon(Icons.Filled.Add, Tones.Mint, 40.dp)
                Text("Quick add to ${me.name}", color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EqualRow(2) {
                    kinds.forEach { KindChip(it, it == k, Modifier.weight(1f)) { kind = it.name } }
                    // An odd count would stretch the last chip across the row; an empty half keeps it the same size as the others.
                    if (kinds.size % 2 == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
                androidx.compose.animation.AnimatedVisibility(k == QuickKind.Child && spouses.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Other parent", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    EqualRow(2) {
                        spouses.forEach { Pill(it.name, otherParent == it.id, { otherParent = it.id }, Modifier.weight(1f), icon = Icons.Filled.Person) }
                        Pill("Nobody", otherParent == -1, { otherParent = -1 }, Modifier.weight(1f), icon = Icons.Filled.Close)
                        if ((spouses.size + 1) % 2 == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToneIcon(Icons.Filled.Person, k?.tone() ?: Palette.TextMuted, 44.dp, Modifier.padding(bottom = 3.dp))
                    Field("Name", name, { name = it }, Modifier.weight(1f))
                }
                Text(
                    if (k == null) "Choose who you are adding, then type a name." else if (added.isEmpty()) "Type a name and tap Add." else "Added: " + added.joinToString(", "),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = if (added.isNotEmpty() && k != null) Palette.Accent else Palette.TextMuted,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("Done", onClose, kind = ButtonKind.Neutral)
                DialogButton(
                    "Add",
                    {
                        val rel = relation() ?: return@DialogButton
                        val base = suggest(rel)
                        val (given, surname) = splitName(name)
                        onAdd(PersonDraft(given = given, surname = surname.ifEmpty { base.surname }, sex = base.sex), rel)
                        added = added + name.trim()
                        name = ""
                    },
                    kind = ButtonKind.Primary,
                    enabled = name.isNotBlank() && k != null,
                    icon = Icons.Filled.Add,
                )
            }
        },
    )
}

/** The relation that makes a new person a sibling of [personId]: a child of the same parents (up to two), or null when no parent is recorded. */
internal fun siblingRelation(family: Family, personId: Int): Relation.ChildOf? {
    val parents = family.parentsOf(personId)
    if (parents.isEmpty()) return null
    return Relation.ChildOf(parents[0].id, parents.getOrNull(1)?.id)
}

private fun QuickKind.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    QuickKind.Child -> FamilyIcons.Child
    QuickKind.Spouse -> Icons.Filled.Favorite
    QuickKind.Parent -> FamilyIcons.Parent
    QuickKind.Sibling -> FamilyIcons.Sibling
}

private fun QuickKind.tone(): androidx.compose.ui.graphics.Color = when (this) {
    QuickKind.Child -> Tones.Mint
    QuickKind.Spouse -> Tones.Rose
    QuickKind.Parent -> Tones.Violet
    QuickKind.Sibling -> Tones.Teal
}

/** A relation choice: a tinted round icon and a label, filled with the relation's colour when chosen. */
@Composable
private fun KindChip(kind: QuickKind, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tone = kind.tone()
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.animation.animateColorAsState(if (selected) tone.copy(alpha = 0.30f) else Palette.SurfaceHigh, androidx.compose.animation.core.tween(200), label = "chip").value)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        ToneIcon(kind.icon(), tone, 30.dp)
        Text(kind.label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.padding(start = 8.dp))
    }
}
