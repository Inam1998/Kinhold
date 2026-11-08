package com.inamibrahim.kinhold.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.KinholdViewModel
import com.inamibrahim.kinhold.model.GedcomResult

/**
 * Lets the person pick a GEDCOM file, checks it, and asks what to do with it before anything changes. Returns the
 * function that opens the file picker; the confirmation dialog is drawn by this call. [onMessage] gets the outcome as
 * a sentence and whether it was an error.
 */
@Composable
fun rememberGedcomImporter(
    vm: KinholdViewModel,
    currentPeople: Int,
    onMessage: (String, Boolean) -> Unit,
): () -> Unit {
    var pending by remember { mutableStateOf<GedcomResult?>(null) }
    val backupFirst = rememberBackupFirst(vm, currentPeople)
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.readGedcom(uri) { result ->
                result.fold(
                    { pending = it },
                    { onMessage(it.message ?: "That file could not be read.", true) },
                )
            }
        }
    }

    pending?.let { found ->
        val count = found.data.people.size
        fun finish(replace: Boolean) {
            pending = null
            backupFirst("Importing this file") { vm.importGedcom(found.data, replace) { result ->
                result.fold(
                    { onMessage(if (replace) "Imported $count ${if (count == 1) "person" else "people"}." else "Added $count ${if (count == 1) "person" else "people"} to your tree.", false) },
                    { onMessage(it.message ?: "The file could not be imported.", true) },
                )
            } }
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Import this family tree?", color = Palette.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (line in found.report.summary(currentPeople)) {
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                    }
                }
            },
            confirmButton = {
                DialogButtons {
                    if (currentPeople > 0) DialogButton("Add", { finish(replace = false) }, kind = ButtonKind.Tonal)
                    DialogButton(if (currentPeople > 0) "Replace" else "Import", { finish(replace = true) }, kind = ButtonKind.Primary)
                }
            },
            dismissButton = { DialogButton("Cancel", { pending = null }, kind = ButtonKind.Neutral) },
        )
    }

    return remember(opener) { { opener.launch(arrayOf("*/*")) } }
}

