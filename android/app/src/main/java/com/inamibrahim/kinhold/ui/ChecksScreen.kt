package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.KinholdViewModel
import com.inamibrahim.kinhold.model.DuplicatePair
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Finding
import com.inamibrahim.kinhold.model.MarriageDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.TreeChecks
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

private class CheckResult(val findings: List<Finding>, val duplicates: List<DuplicatePair>)

/** Things in the tree that look wrong, and people who may be entered twice, with a way to join them. Nothing changes without a tap and a confirmation. */
@Composable
fun ChecksScreen(vm: KinholdViewModel, family: Family, marriages: List<MarriageDate>?, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    val result by produceState<CheckResult?>(null, family, marriages) {
        if (marriages != null) {
            // The bar stays up for at least a moment, so a small tree does not make it flash by.
            kotlinx.coroutines.coroutineScope {
                val minimum = async { delay(600) }
                value = withContext(Dispatchers.Default) { CheckResult(TreeChecks.run(family, marriages, LocalDate.now().year), TreeChecks.duplicates(family)) }
                minimum.await()
            }
        }
    }
    var dismissed by remember { mutableStateOf(vm.dismissedDuplicates()) }
    var joining by remember { mutableStateOf<Pair<Person, Person>?>(null) } // keep, remove
    var notice by remember { mutableStateOf<String?>(null) }

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenHeader("Tree health", "Checks", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), onBack = onBack)
            val r = result
            val dups = r?.duplicates.orEmpty().filter { listOf(it.a.uid, it.b.uid).sorted().joinToString("|") !in dismissed }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                notice?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Accent, modifier = Modifier.padding(start = 4.dp)) } }
                if (r == null) {
                    item { CheckingCard(family.size) }
                } else {
                    item { SectionTitle("Possible duplicates", Tones.Violet, Icons.Filled.Person) }
                    if (dups.isEmpty()) item { Quiet("No one looks entered twice.") }
                    items(dups, key = { "d${it.a.id}-${it.b.id}" }) { d ->
                        AppCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${d.a.name}  and  ${d.b.name}", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                                Text("${d.a.lifeDates().ifEmpty { "no dates" }}  ·  ${d.b.lifeDates().ifEmpty { "no dates" }}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                                Text("Why: ${d.why}.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                                EqualRow(2) {
                                    DialogButton("Join, keep first", { joining = d.a to d.b }, Modifier.weight(1f), kind = ButtonKind.Tonal)
                                    DialogButton("Join, keep second", { joining = d.b to d.a }, Modifier.weight(1f), kind = ButtonKind.Tonal)
                                    DialogButton("Not the same", { vm.dismissDuplicate(d.a.uid, d.b.uid); dismissed = vm.dismissedDuplicates() }, Modifier.weight(1f), kind = ButtonKind.Neutral)
                                }
                            }
                        }
                    }
                    item { SectionTitle("Things to look at", Tones.Amber, Icons.Filled.Info) }
                    if (r.findings.isEmpty()) item { Quiet("Nothing looks impossible.") }
                    items(r.findings) { f ->
                        AppCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable { f.people.firstOrNull()?.let { onOpen(it.id) } }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                ToneIcon(Icons.Filled.Info, Tones.Amber, 40.dp)
                                Text(f.message, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(start = 14.dp))
                            }
                        }
                    }
                }
                item { Column(Modifier.padding(bottom = 8.dp)) {} }
            }
        }
    }

    joining?.let { (keep, remove) ->
        AlertDialog(
            onDismissRequest = { joining = null },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Join these two entries?", color = Palette.Text) },
            text = {
                Text(
                    "${remove.name} is folded into ${keep.name} and then removed. ${keep.name} keeps their own details and gets anything they lack from the other entry: dates, places, photo, events, sources, own facts and every family link. " +
                        "A copy of your tree is kept first, so Undo this later in Backup and sharing.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton("Cancel", { joining = null }, kind = ButtonKind.Neutral)
                    DialogButton(
                        "Join them",
                        {
                            joining = null
                            vm.mergePeople(keep.id, remove.id) { res -> notice = res.fold({ "Joined ${remove.name} into ${keep.name}." }, { it.message ?: "They could not be joined." }) }
                        },
                        kind = ButtonKind.Primary,
                        icon = Icons.Filled.Check,
                    )
                }
            },
        )
    }
}

/** Shown while the checks run: a rounded bar that keeps moving, because the time the checks take is not known in advance. */
@Composable
private fun CheckingCard(people: Int) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIcon(Icons.Filled.Check, Tones.Teal, 40.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Checking your tree", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text("Looking at ${"%,d".format(people)} ${if (people == 1) "person" else "people"}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(8.dp).clip(RoundedCornerShape(50)),
                color = Palette.Accent,
                trackColor = Palette.SurfaceHigh,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, tone: androidx.compose.ui.graphics.Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ToneIcon(icon, tone, 32.dp)
        Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp))
}
