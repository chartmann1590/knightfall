package com.chartmann.knightfall.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chartmann.knightfall.AppContainer
import com.chartmann.knightfall.data.model.Puzzle
import com.chartmann.knightfall.data.model.UserProfile
import com.chartmann.knightfall.ui.components.BadgeShelf
import com.chartmann.knightfall.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    container: AppContainer,
    profile: UserProfile?,
    onBack: () -> Unit,
    onStartPuzzle: (Puzzle) -> Unit,
) {
    val daily = container.puzzles.getDailyPuzzle()
    val puzzlesSolved = profile?.puzzlesSolved ?: 0
    val bestStreak = profile?.bestPuzzleStreak ?: 0
    val earnedBadges = profile?.earnedBadges ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))

            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Puzzles solved", "$puzzlesSolved", Modifier.weight(1f))
                StatCard("Best streak", "$bestStreak", Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "TODAY'S PUZZLE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            PuzzleCard(
                puzzle = daily,
                label = "Daily puzzle",
                onClick = { onStartPuzzle(daily) },
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "PRACTICE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { onStartPuzzle(container.puzzles.getRandom()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp))
                Text("Random puzzle")
            }

            if (earnedBadges.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "BADGES EARNED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                BadgeShelf(badgeIds = earnedBadges)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PuzzleCard(puzzle: Puzzle, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        puzzle.themes.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Tactics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Rating ${puzzle.rating}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gold,
                    )
                }
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                ) {
                    Text("Solve", color = MaterialTheme.colorScheme.background,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
