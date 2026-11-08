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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material.icons.filled.Delete
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
fun BackupScreen(vm: KinholdViewModel, family: com.inamibrahim.kinhold.model.Family, peopleCount: Int, scroll: androidx.compose.foundation.ScrollState, onBack: () -> Unit, onMerge: () -> Unit, onShare: (Int) -> Unit, onCleared: () -> Unit) {
    var clearing by remember { mutableStateOf(false) }
    var choosingShare by remember { mutableStateOf(false) }
    val backupFirst = rememberBackupFirst(vm, peopleCount)
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pending by remember { mutableStateOf<BackupData?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
            ScreenHeader("Kinhold", "Backup and sharing", Modifier.padding(top = 12.dp, bottom = 20.dp), onBack = onBack)

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
            AppCard(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Which one do I need?", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    GuideLine(Icons.Filled.Lock, Tones.Mint, "Keep my own tree safe", "Back up and restore, just below.")
                    GuideLine(Icons.Filled.Share, Tones.Violet, "Send some of my relatives to a family member", "Share with a relative. They open the file in their own Kinhold.")
                    GuideLine(Icons.Filled.Add, Tones.Sky, "I received a file", "Open a file from a relative. It works for Kinhold files and GEDCOM files.")
                    GuideLine(Icons.Filled.Send, Tones.Amber, "Move to or from another program", "Ancestry, MyHeritage or FamilySearch: the GEDCOM section, further down.")
                }
            }

            SectionHeader("Keep your tree safe", "Copies of your own tree, so you never lose it.")
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
                    EqualRow(2, Modifier.padding(top = 16.dp)) {
                        DialogButton(if (vm.autoFolderUri == null) "Choose a folder" else "Change folder", { folderPicker.launch(null) }, Modifier.weight(1f), kind = ButtonKind.Primary)
                        DialogButton("Back up now", { vm.autoBackupNow() }, Modifier.weight(1f), kind = ButtonKind.Tonal, enabled = peopleCount > 0)
                        if (vm.autoFolderUri != null) DialogButton("Stop using the folder", { vm.clearAutoFolder() }, Modifier.weight(1f), kind = ButtonKind.Neutral)
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
                            modifier = Modifier.weight(1f),
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
                    DialogButton(
                        "Send a copy (Drive, email, WhatsApp)",
                        {
                            val p = SendFile.prepare(context, "kinhold-backup-${LocalDate.now()}.json", "application/json", "Kinhold backup")
                            // Not counted as "backed up": Kinhold cannot tell where the share sheet sent it.
                            vm.saveBackup(p.uri, markBackedUp = false) { result ->
                                result.fold(
                                    { SendFile.send(context, p); message = "Copy of ${plural(it)} ready. Choose where to send it." to false },
                                    { message = (it.message ?: "The copy could not be made.") to true },
                                )
                            }
                        },
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        kind = ButtonKind.Tonal,
                        icon = Icons.Filled.Send,
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
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Refresh, Tones.Amber, 40.dp); Text("Restore from a backup", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "Use this to get your own tree back from a backup file, for example on a new phone. It replaces the tree on this phone with the one in the file. " +
                            "The file is checked first, and a copy of your current tree is kept so you can undo. To add someone else's file instead, use Open a file from a relative.",
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

            SectionHeader("Share with a relative", "Give a family member part of your tree, or add what they send you.")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Share, Tones.Violet, 40.dp); Text("Send part of your tree", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "Choose one person. Kinhold then makes a file with them and the relatives you pick (their parents and grandparents, their children, and so on). " +
                            "Send it by WhatsApp, email or any other app. Your relative opens it in their own Kinhold, and only the people they do not already have are added. Nothing is removed from their tree.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    DialogButton("Choose who to start from", { choosingShare = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = Icons.Filled.Share, enabled = peopleCount > 0)
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Add, Tones.Sky, 40.dp); Text("Open a file from a relative", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "Got a file from a relative? Choose it here. Kinhold tells you what kind of file it is, shows who is already in your tree and who is new, " +
                            "and asks about anything that differs. It only adds, never removes, and you can undo. It works with Kinhold share files, Kinhold backups and GEDCOM files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Pill(
                        "Choose the file",
                        selected = false,
                        onClick = { mergeOpener.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        )
                }
            }

            SectionHeader("Other family tree programs", "GEDCOM is the file every family tree program understands.")
            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Send, Tones.Violet, 40.dp); Text("Share with other programs", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        "Import is for bringing a whole tree here the first time from Ancestry, MyHeritage, FamilySearch or another program. " +
                            "If you already have people here and want to add a GEDCOM file without getting anyone twice, use Open a file from a relative instead. " +
                            "Save as a GEDCOM file lets other programs read your tree.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Pill("Import a GEDCOM file (add or replace)", selected = false, onClick = openGedcom, modifier = Modifier.fillMaxWidth())
                    Pill(
                        "Save as a GEDCOM file",
                        selected = false,
                        onClick = { if (peopleCount > 0) gedcomSaver.launch("kinhold-${LocalDate.now()}.ged") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "GEDCOM keeps names, sex, birth and death dates, places, notes, family links, events, marriages and " +
                            "source citations. Photos are not part of a GEDCOM file; a Kinhold backup keeps them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            SectionHeader("Undo and start over", "Take back the last big change, or empty the tree.")
            if (vm.hasSafetyCopy) {
                AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.AutoMirrored.Filled.ArrowBack, Tones.Coral, 40.dp); Text("Undo the last restore, import or delete", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                        Text(
                            "Brings back the tree you had just before the last restore, import or delete. The tree on this phone now becomes the saved copy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        )
                        Pill(
                            "Undo the last restore, import or delete",
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

            if (peopleCount > 0) {
                AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Delete, Tones.Rose, 40.dp); Text("Start over", style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                        Text("Removes everyone from the tree, for example a sample family you loaded to look around. A copy is kept first, so you can undo it here.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                        DialogButton("Delete the whole tree", { clearing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Danger)
                    }
                }
            }

            AdSlot(com.inamibrahim.kinhold.model.AdPlacement.Backup, Modifier.padding(top = 12.dp))
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }

    if (choosingShare) PersonPickerDialog(family, "Start from which person?", { id -> choosingShare = false; onShare(id) }, { choosingShare = false })

    if (clearing) ClearTreeDialog(peopleCount, { clearing = false; vm.clearTree { onCleared() } }, { clearing = false })

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
                DialogButtons {
                    DialogButton("Cancel", { pending = null }, kind = ButtonKind.Neutral)
                    DialogButton(
                        "Replace",
                        {
                            pending = null
                            backupFirst("Restoring this backup") {
                                vm.restore(data) { result ->
                                    message = result.fold(
                                        { "Restored ${plural(data.people.size)}." to false },
                                        { (it.message ?: "The backup could not be restored.") to true },
                                    )
                                }
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

/** A heading that groups the cards under it by what the person wants to do, with one line saying what the group is for. */
@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 22.dp, bottom = 10.dp, start = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

/** One line of the "Which one do I need?" guide: an icon, what the person wants, and where to go for it. */
@Composable
private fun GuideLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: androidx.compose.ui.graphics.Color, want: String, where: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToneIcon(icon, tone, 36.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(want, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            Text(where, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
    }
}
