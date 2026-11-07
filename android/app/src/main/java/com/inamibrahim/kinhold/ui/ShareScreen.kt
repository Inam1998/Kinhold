@file:OptIn(ExperimentalLayoutApi::class)

package com.inamibrahim.kinhold.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.KinholdViewModel
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Share
import com.inamibrahim.kinhold.model.ShareDirection
import com.inamibrahim.kinhold.model.ShareScope
import java.time.LocalDate

/** Choose how much of the tree around one person goes into a share file, and save it. */
@Composable
fun ShareScreen(vm: KinholdViewModel, family: Family, person: Person, onBack: () -> Unit) {
    var direction by rememberSaveable { mutableIntStateOf(ShareDirection.Both.ordinal) }
    var generations by rememberSaveable { mutableIntStateOf(0) }
    var spouses by rememberSaveable { mutableStateOf(true) }
    var excludeLiving by rememberSaveable { mutableStateOf(false) }
    var photos by rememberSaveable { mutableStateOf(true) }
    var contact by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val scope = ShareScope(ShareDirection.entries[direction], generations, spouses, excludeLiving, photos, contact)
    val count = remember(family, person.id, direction, generations, spouses, excludeLiving) {
        Share.select(family, person.id, scope, LocalDate.now().year).size
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.exportShare(uri, person.id, scope) { result ->
                message = result.fold(
                    { "Share file saved with $it ${if (it == 1) "person" else "people"}." to false },
                    { (it.message ?: "The file could not be saved.") to true },
                )
            }
        }
    }

    val chartDone = { r: Result<Unit> -> message = r.fold({ "Chart saved." to false }, { (it.message ?: "The chart could not be saved.") to true }) }
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) vm.exportChart(uri, person.id, true, chartDone) }
    val pngSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri -> if (uri != null) vm.exportChart(uri, person.id, false, chartDone) }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            ScreenHeader("Share", "Share from ${person.name}", Modifier.padding(top = 12.dp, bottom = 16.dp), onBack = onBack)
            Text(
                "Makes a file with this person and the relatives you choose. A relative can open it in their Kinhold with " +
                    "\"Merge a shared file\" and get only the people they do not have, and be asked about the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            message?.let { (text, isError) ->
                AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = if (isError) Palette.Danger else Palette.Accent, modifier = Modifier.padding(20.dp))
                }
            }

            Text("Which relatives", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
            EqualRow(3) {
                Pill("Both ways", direction == ShareDirection.Both.ordinal, { direction = ShareDirection.Both.ordinal }, Modifier.weight(1f))
                Pill("Ancestors", direction == ShareDirection.Ancestors.ordinal, { direction = ShareDirection.Ancestors.ordinal }, Modifier.weight(1f))
                Pill("Descendants", direction == ShareDirection.Descendants.ordinal, { direction = ShareDirection.Descendants.ordinal }, Modifier.weight(1f))
            }
            Text("How many generations", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp))
            EqualRow(5) {
                for (g in listOf(1, 2, 3, 4)) Pill("$g", generations == g, { generations = g }, Modifier.weight(1f))
                Pill("All", generations == 0, { generations = 0 }, Modifier.weight(1f))
            }
            Text("Also include", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp))
            EqualRow(1) {
                Pill("Spouses", spouses, { spouses = !spouses }, Modifier.weight(1f))
                Pill("Photos", photos, { photos = !photos }, Modifier.weight(1f))
                Pill("Contact details and own facts", contact, { contact = !contact }, Modifier.weight(1f))
                Pill("Leave out people who may be alive", excludeLiving, { excludeLiving = !excludeLiving }, Modifier.weight(1f))
            }
            if (excludeLiving) {
                Text(
                    "Leaves out anyone with no death date who was born in the last 110 years. People with no dates at all are kept, " +
                        "because Kinhold cannot tell. ${person.name} is always included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }

            AppCard(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("$count ${if (count == 1) "person" else "people"} will be in the file", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text(
                        "With their events and sources. The file is not encrypted, so send it only to people you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    PrimaryButton(
                        "Save the share file",
                        { saver.launch("kinhold-share-${person.name.replace(' ', '-')}-${LocalDate.now()}.json") },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIcon(Icons.Filled.Share, Tones.Violet, 36.dp)
                        Text("Print or send a picture", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text(
                        "A chart of what the tree shows around ${person.name} (three generations above, two below), as a PDF to print or a picture to send to people who do not use Kinhold. Names and years only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogButton("Save as PDF", { pdfSaver.launch("kinhold-tree-${person.name.replace(' ', '-')}.pdf") }, kind = ButtonKind.Tonal)
                        DialogButton("Save as picture", { pngSaver.launch("kinhold-tree-${person.name.replace(' ', '-')}.png") }, kind = ButtonKind.Tonal)
                    }
                }
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }
}
