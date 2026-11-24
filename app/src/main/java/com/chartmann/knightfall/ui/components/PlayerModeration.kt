package com.chartmann.knightfall.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chartmann.knightfall.data.ReportReason

/**
 * "Report player" dialog with a reason picker (username, cheating,
 * harassment, other). [onReport] fires with the chosen reason.
 */
@Composable
fun ReportPlayerDialog(
    playerName: String,
    onDismiss: () -> Unit,
    onReport: (ReportReason) -> Unit,
) {
    var selected by remember { mutableStateOf(ReportReason.USERNAME) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $playerName") },
        text = {
            Column {
                Text(
                    "Why are you reporting this player? Reports are reviewed, " +
                        "and players who break the community rules can be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == reason,
                                onClick = { selected = reason },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == reason,
                            onClick = { selected = reason },
                        )
                        Text(reason.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onReport(selected) }) { Text("Report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** "Block player" confirmation dialog. */
@Composable
fun BlockPlayerDialog(
    playerName: String,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block $playerName?") },
        text = {
            Text(
                "You won't see this player on the leaderboard and you won't " +
                    "be matched against them in quick match.",
            )
        },
        confirmButton = {
            TextButton(onClick = onBlock) { Text("Block") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
