package com.chartmann.knightfall.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

fun formatClock(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSecs / 60, totalSecs % 60)
}
import com.chartmann.knightfall.ui.board.pieceDrawable
import com.chartmann.knightfall.ui.theme.Gold
import com.chartmann.knightfall.ui.theme.WinGreen
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side

/** Avatar dot + name + rating + captured pieces for one player. */
@Composable
fun PlayerBar(
    name: String,
    subtitle: String?,
    isTurn: Boolean,
    capturedPieces: List<PieceType>,
    capturedSide: Side,
    materialAdvantage: Int,
    timeMs: Long? = null,
    avatarColor: Color = Gold,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isTurn) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = if (isTurn) 1f else 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CapturedRow(capturedPieces, capturedSide, materialAdvantage)
            if (timeMs != null) {
                Spacer(Modifier.width(8.dp))
                val lowTime = timeMs in 1..29_999
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (lowTime) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = formatClock(timeMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (lowTime) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (isTurn) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(WinGreen),
                )
            }
        }
    }
}

@Composable
private fun CapturedRow(pieces: List<PieceType>, side: Side, advantage: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (type in pieces.take(8)) {
            val piece = Piece.make(if (side == Side.WHITE) Side.BLACK else Side.WHITE, type)
            pieceDrawable(piece)?.let { res ->
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (advantage > 0) {
            Text(
                text = "+$advantage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
fun PromotionPicker(
    side: Side,
    onPick: (PieceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promote to") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (type in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                    val piece = Piece.make(side, type)
                    pieceDrawable(piece)?.let { res ->
                        Image(
                            painter = painterResource(res),
                            contentDescription = type.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp)
                                .clickableNoIndication { onPick(type) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun GameOverCard(
    title: String,
    subtitle: String,
    eloDelta: Int?,
    onRematch: (() -> Unit)?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (eloDelta != null) {
                Spacer(Modifier.height(8.dp))
                val sign = if (eloDelta >= 0) "+" else ""
                Text(
                    text = "Rating $sign$eloDelta",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (eloDelta >= 0) WinGreen else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRematch != null) {
                    Button(onClick = onRematch) { Text("Rematch") }
                }
                OutlinedButton(onClick = onExit) { Text("Back to home") }
            }
        }
    }
}

@Composable
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    )
