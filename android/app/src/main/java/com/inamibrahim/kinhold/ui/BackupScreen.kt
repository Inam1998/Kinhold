package com.inamibrahim.kinhold.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
fun BackupScreen(vm: KinholdViewModel, peopleCount: Int, onBack: () -> Unit) {
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

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
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

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Save a backup", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
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
                    Text("Restore from a backup", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
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

            if (vm.hasSafetyCopy) {
                AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Undo the last restore", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                        Text(
                            "Brings back the tree you had just before the last restore. The tree on this phone now becomes the saved copy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        )
                        Pill(
                            "Undo last restore",
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
                Text(
                    "Replace",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            pending = null
                            vm.restore(data) { result ->
                                message = result.fold(
                                    { "Restored ${plural(data.people.size)}." to false },
                                    { (it.message ?: "The backup could not be restored.") to true },
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { pending = null }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            },
        )
    }
}

private fun plural(n: Int): String = "$n ${if (n == 1) "person" else "people"}"
