package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Asks before everyone is removed from the tree. A copy is kept first, so it can be undone from Backup and sharing. */
@Composable
fun ClearTreeDialog(people: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Delete the whole tree?", color = Palette.Text) },
        text = {
            Text(
                "All ${"%,d".format(people)} ${if (people == 1) "person" else "people"}, with their photos, events and sources, will be removed from this phone and you can start again. " +
                    "A copy is kept first: open More, then Backup and sharing, and choose Undo the last restore, import or delete to bring it back.",
                color = Palette.TextMuted,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral)
                DialogButton("Delete everything", onConfirm, kind = ButtonKind.Danger)
            }
        },
    )
}

/** Explains what Clear does on the sample bar. A sample family is invented, so no copy is kept and this cannot be undone. */
@Composable
fun ClearSampleDialog(people: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Clear the sample family?", color = Palette.Text) },
        text = {
            Text(
                "This removes everyone in the tree (${"%,d".format(people)} ${if (people == 1) "person" else "people"}) so you can start your own. " +
                    "It includes anyone you added yourself. No copy is kept, so it cannot be undone.",
                color = Palette.TextMuted,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral)
                DialogButton("Clear it", onConfirm, kind = ButtonKind.Danger)
            }
        },
    )
}

/** Explains what Keep does on the sample bar. */
@Composable
fun KeepSampleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Keep this family?", color = Palette.Text) },
        text = {
            Text(
                "The sample people stay in your tree as ordinary entries and this bar goes away. " +
                    "You can still edit or delete people one at a time, or remove everyone later with Delete the whole tree in More, then Backup and sharing.",
                color = Palette.TextMuted,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral)
                DialogButton("Keep them", onConfirm, kind = ButtonKind.Primary)
            }
        },
    )
}
