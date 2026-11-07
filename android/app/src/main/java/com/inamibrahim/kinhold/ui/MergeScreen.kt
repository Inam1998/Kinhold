package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.KinholdViewModel
import com.inamibrahim.kinhold.data.MergeSession
import com.inamibrahim.kinhold.model.Candidate
import com.inamibrahim.kinhold.model.FieldChange
import com.inamibrahim.kinhold.model.MergeResult

private fun people(n: Int) = "$n ${if (n == 1) "person" else "people"}"

/**
 * The review of a file being merged into the tree: who is the same person, which fields to fill or replace, and what will be
 * added. Nothing changes until Merge is pressed. Strong matches start as "same person" and the rest as "different person";
 * fields your tree lacks start as "use theirs" and fields that conflict start as "keep mine".
 */
@Composable
fun MergeScreen(vm: KinholdViewModel, session: MergeSession, onDone: () -> Unit) {
    val plan = session.plan
    var accepted by remember(session) { mutableStateOf(plan.candidates.filter { it.strong }.map { it.incomingId }.toSet()) }
    var overrides by remember(session) { mutableStateOf<Map<Pair<Int, com.inamibrahim.kinhold.model.MergeField>, Boolean>>(emptyMap()) }
    var addEvents by remember(session) { mutableStateOf(true) }
    var done by remember(session) { mutableStateOf<MergeResult?>(null) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var working by remember(session) { mutableStateOf(false) }

    val matches = remember(session, accepted) { plan.matchesFor(accepted) }
    val changes = remember(session, accepted) { plan.fieldChanges(matches) }
    val useHis = remember(changes, overrides) { changes.filter { overrides[it.key] ?: it.isFill }.map { it.key }.toSet() }
    val result = remember(session, accepted, useHis, addEvents) { plan.resolve(accepted, useHis, addEvents) }

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenHeader("Merge", "Merge a shared file", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp))
            val finished = done
            if (finished != null) {
                DoneCard(vm, finished) { vm.cancelMerge(); onDone() }
                return@Column
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("The file has ${people(session.data.people.size)}.", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text("${people(matches.size)} are already in your tree.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                            Text("${people(result.newPeople.size)} will be added as new.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                            Text(
                                "Nothing in your tree is replaced unless you choose it below. A copy of your tree is kept, so you can undo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextMuted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (plan.candidates.isNotEmpty()) {
                    item { SectionLabel("Is this the same person?") }
                    items(plan.candidates, key = { "c${it.incomingId}" }) { c ->
                        CandidateRow(plan.incomingPerson(c.incomingId).name, plan.currentPerson(c.currentId).name, c, c.incomingId in accepted) { same ->
                            accepted = if (same) accepted + c.incomingId else accepted - c.incomingId
                        }
                    }
                }
                if (changes.isNotEmpty()) {
                    item {
                        SectionLabel("People in both: what differs")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                            Pill("Fill all my empty fields", false, { overrides = overrides + changes.filter { it.isFill }.associate { it.key to true } })
                            Pill("Keep everything of mine", false, { overrides = overrides + changes.associate { it.key to false } })
                        }
                    }
                    items(changes, key = { "f${it.incomingId}-${it.part.name}" }) { ch ->
                        ChangeRow(ch, ch.key in useHis) { his -> overrides = overrides + (ch.key to his) }
                    }
                }
                item {
                    SectionLabel("Events and sources")
                    Pill(
                        if (addEvents) "Add events and sources from the file: on" else "Add events and sources from the file: off",
                        addEvents,
                        { addEvents = !addEvents },
                    )
                    Text(
                        "For people in both trees, only the ones you do not already have are added. New people always come with theirs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
                item {
                    Text(
                        "This will add ${people(result.newPeople.size)}, ${count(result.links.size, "family link")}, ${count(result.eventCount, "event")} and ${count(result.sourceCount, "source")}, " +
                            "and change ${count(result.fieldCount, "field")}." +
                            if (result.skippedParentLinks > 0) " ${result.skippedParentLinks} parent links are skipped because that person already has two parents." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Accent,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                }
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    if (working) "Merging…" else "Merge into my tree",
                    {
                        working = true
                        error = null
                        vm.applyMerge(session, accepted, useHis, addEvents) { r ->
                            working = false
                            r.fold({ done = it }, { error = it.message ?: "The merge could not be done. Your tree is unchanged." })
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !working,
                )
                Pill("Cancel", false, { vm.cancelMerge(); onDone() }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DoneCard(vm: KinholdViewModel, r: MergeResult, onDone: () -> Unit) {
    var undone by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (undone) "Merge undone. Your earlier tree is back." else "Merged.", style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
                if (!undone) {
                    Text(
                        "Added ${people(r.newPeople.size)}, ${count(r.links.size, "family link")}, ${count(r.eventCount, "event")} and ${count(r.sourceCount, "source")}. " +
                            "Changed ${count(r.fieldCount, "field")} on people you already had.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                    )
                    if (r.skippedParentLinks > 0) {
                        Text("${r.skippedParentLinks} parent links were skipped because that person already has two parents.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                }
            }
        }
        if (!undone) Pill("Undo this merge", false, { vm.undoRestore { undone = it.isSuccess } }, Modifier.fillMaxWidth().padding(top = 12.dp))
        PrimaryButton("Done", onDone, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
}

@Composable
private fun CandidateRow(his: String, mine: String, c: Candidate, same: Boolean, onChoose: (Boolean) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$his  =  $mine?", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text((if (c.strong) "Likely the same: " else "Possibly the same: ") + c.why + ".", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Same person", same, { onChoose(true) })
                Pill("Different person", !same, { onChoose(false) })
            }
        }
    }
}

@Composable
private fun ChangeRow(ch: FieldChange, useHis: Boolean, onChoose: (Boolean) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${ch.personName}: ${ch.part.label}", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(if (ch.isFill) "Yours is empty or less exact." else "The two trees disagree.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Keep mine: ${ch.mine.short()}", !useHis, { onChoose(false) })
                Pill("Use theirs: ${ch.his.short()}", useHis, { onChoose(true) })
            }
        }
    }
}

private fun String.short(): String = replace('\n', ' ').let { if (it.length > 24) it.take(23) + "…" else it }

private fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
