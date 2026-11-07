package com.inamibrahim.kinhold.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import java.time.LocalDate

private class BackupRequest(val what: String, val proceed: () -> Unit)

/**
 * A gate to put in front of anything that changes the whole tree (merge, import, restore). Returns a function to call
 * with a short description ("Merging this file") and what to do next. With people in the tree it first offers to save a
 * backup file: Save a backup (and carry on once it is saved), Continue without one, or Cancel. With an empty tree there
 * is nothing to lose and it carries on at once. The dialog is drawn by this call.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun rememberBackupFirst(vm: KinholdViewModel, peopleCount: Int): (what: String, proceed: () -> Unit) -> Unit {
    var request by remember { mutableStateOf<BackupRequest?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val req = request
        if (uri != null && req != null) {
            vm.saveBackup(uri) { result ->
                result.fold(
                    {
                        request = null
                        problem = null
                        req.proceed()
                    },
                    { problem = it.message ?: "The backup could not be saved, so nothing was changed." },
                )
            }
        }
    }

    request?.let { req ->
        AlertDialog(
            onDismissRequest = { request = null; problem = null },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Save a backup first?", color = Palette.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${req.what} changes your tree. A backup file is a copy of the whole tree as it is now. " +
                            "If anything goes wrong, you can restore it from Backup and sharing.",
                        color = Palette.TextMuted,
                    )
                    Text("Kinhold also keeps a copy of the tree from just before, which Undo uses, but a saved file is safer.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger) }
                }
            },
            confirmButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton("Cancel", { request = null; problem = null }, kind = ButtonKind.Neutral)
                    DialogButton("Continue without", { request = null; problem = null; req.proceed() }, kind = ButtonKind.Tonal)
                    DialogButton("Save a backup", { problem = null; saver.launch("kinhold-backup-${LocalDate.now()}.json") }, kind = ButtonKind.Primary)
                }
            },
        )
    }

    return remember(peopleCount) {
        { what: String, proceed: () -> Unit -> if (peopleCount == 0) proceed() else request = BackupRequest(what, proceed) }
    }
}
