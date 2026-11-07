@file:OptIn(ExperimentalLayoutApi::class)

package com.inamibrahim.kinhold.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.KinholdViewModel
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.describeAge
import java.time.LocalDate

/** Save the whole tree to one file, restore from one, and undo the last restore. */
@Composable
fun BackupScreen(vm: KinholdViewModel, peopleCount: Int, scroll: androidx.compose.foundation.ScrollState, onBack: () -> Unit, onMerge: () -> Unit) {
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pending by remember { mutableStateOf<BackupData?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.saveBackup(uri) { result ->
                message = result.fold(
                    { "Backup saved: ${plural(it)}." to false },
                    { (it.message ?: "The backup could not be saved.") to true },
                )
            }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = null
            vm.readBackup(uri) { result ->
                result.fold(
                    { pending = it },
                    { message = (it.message ?: "That file could not be read.") to true },
                )
            }
        }
    }

    val mergeOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = null
            vm.startMerge(uri) { result ->
                result.fold(
                    { onMerge() },
                    { message = (it.message ?: "That file could not be read.") to true },
                )
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.setAutoFolder(uri) }
    val gedcomSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            vm.exportGedcom(uri) { result ->
                message = result.fold(
                    { "GEDCOM file saved: ${plural(it)}." to false },
                    { (it.message ?: "The GEDCOM file could not be saved.") to true },
                )
            }
        }
    }
    val openGedcom = rememberGedcomImporter(vm, peopleCount) { text, isError -> message = text to isError }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
        ) {
            ScreenHeader("Kinhold", "Backup and restore", Modifier.padding(top = 12.dp, bottom = 20.dp))

            message?.let { (text, isError) ->
                AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isError) Palette.Danger else Palette.Accent,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIcon(Icons.Filled.Refresh, Tones.Teal, 40.dp)
                        Text("Automatic backup", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text(
                        "Kinhold saves a copy by itself shortly after you change your tree, and when you leave the app. " +
                            "Copies are kept inside the app, and also in a folder you choose, which keeps your tree even if the app is uninstalled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        vm.lastAutoBackup?.let { "Last automatic copy: ${describeAge(it, System.currentTimeMillis())}" } ?: "No automatic copy yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Accent,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (vm.autoFolderUri != null) {
                        Text("Folder: ${vm.autoFolderName ?: "chosen"}", style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text(
                            "No folder chosen yet. Until you choose one, the copies are only inside the app, and uninstalling the app deletes them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tones.Amber,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    vm.autoError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger, modifier = Modifier.padding(top = 8.dp)) }
                    FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogButton(if (vm.autoFolderUri == null) "Choose a folder" else "Change folder", { folderPicker.launch(null) }, kind = ButtonKind.Primary)
                        DialogButton("Back up now", { vm.autoBackupNow() }, kind = ButtonKind.Tonal, enabled = peopleCount > 0)
                        if (vm.autoFolderUri != null) DialogButton("Stop using the folder", { vm.clearAutoFolder() }, kind = ButtonKind.Neutral)
                        DialogButton(
                            "Restore the latest copy",
                            {
                                vm.latestAutoCopy { result ->
                                    result.fold(
                                        { found -> if (found != null) pending = found.first else message = "There is no automatic copy inside the app yet." to true },
                                        { message = (it.message ?: "The automatic copy could not be read.") to true },
                                    )
                                }
                            },
                            kind = ButtonKind.Neutral,
                        )
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Lock, Tones.Mint, 40.dp); Text("Save a backup", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "Puts your whole tree, ${plural(peopleCount)}, in one file. Save it somewhere safe such as Google Drive, " +
                            "an email to yourself or your computer, so you can get your tree back on this phone or a new one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        vm.lastBackup?.let { "Last backup: ${describeAge(it, System.currentTimeMillis())}" } ?: "You have not saved a backup yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Accent,
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    )
                    PrimaryButton(
                        "Save a backup file",
                        { saver.launch("kinhold-backup-${LocalDate.now()}.json") },
                        Modifier.fillMaxWidth(),
                        enabled = peopleCount > 0,
                    )
                    Text(
                        "The file is not encrypted. Anyone who has it can read your tree, so keep it private.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Add, Tones.Sky, 40.dp); Text("Merge a shared file", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "Got a file from a relative (made with Share branch on a person)? Merging adds the people you do not have, " +
                            "skips the ones you do, and asks you about anything that differs. Nothing is replaced unless you choose it, " +
                            "and you can undo. A backup or GEDCOM file can be merged the same way.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Pill(
                        "Choose a file to merge",
                        selected = false,
                        onClick = { mergeOpener.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        )
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Refresh, Tones.Amber, 40.dp); Text("Restore from a backup", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "Replaces the tree on this phone with the one in a backup file. The file is checked first, and a copy " +
                            "of your current tree is kept so you can undo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Pill(
                        "Choose a backup file",
                        selected = false,
                        onClick = { opener.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Send, Tones.Violet, 40.dp); Text("Share with other programs", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "GEDCOM is the standard file for family trees. Use it to bring a tree here from Ancestry, MyHeritage, " +
                            "FamilySearch or another program, or to take yours there.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Pill("Import a GEDCOM file", selected = false, onClick = openGedcom, modifier = Modifier.fillMaxWidth())
                    Pill(
                        "Save as a GEDCOM file",
                        selected = false,
                        onClick = { if (peopleCount > 0) gedcomSaver.launch("kinhold-${LocalDate.now()}.ged") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "GEDCOM keeps names, sex, birth and death years, places, notes and family links. Photos and other " +
                            "details are not part of Kinhold yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            if (vm.hasSafetyCopy) {
                AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.AutoMirrored.Filled.ArrowBack, Tones.Coral, 40.dp); Text("Undo the last restore or import", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                        Text(
                            "Brings back the tree you had just before the last restore or import. The tree on this phone now becomes the saved copy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        )
                        Pill(
                            "Undo the last restore or import",
                            selected = false,
                            onClick = {
                                vm.undoRestore { result ->
                                    message = result.fold(
                                        { "Your earlier tree is back." to false },
                                        { (it.message ?: "The earlier tree could not be restored.") to true },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Pill("Back", false, onBack, Modifier.fillMaxWidth().padding(top = 20.dp))
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }

    pending?.let { data ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Replace your tree?", color = Palette.Text) },
            text = {
                Text(
                    "This backup has ${plural(data.people.size)}" +
                        (if (data.created.length >= 10) ", saved on ${data.created.take(10)}" else "") +
                        ". Your tree on this phone has ${plural(peopleCount)} and will be replaced. " +
                        "A copy of it is kept so you can undo.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton("Cancel", { pending = null }, kind = ButtonKind.Neutral)
                    DialogButton(
                        "Replace",
                        {
                            pending = null
                            vm.restore(data) { result ->
                                message = result.fold(
                                    { "Restored ${plural(data.people.size)}." to false },
                                    { (it.message ?: "The backup could not be restored.") to true },
                                )
                            }
                        },
                        kind = ButtonKind.Danger,
                    )
                }
            },
        )
    }
}

private fun plural(n: Int): String = "$n ${if (n == 1) "person" else "people"}"
