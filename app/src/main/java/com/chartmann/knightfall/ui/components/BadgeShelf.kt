package com.chartmann.knightfall.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chartmann.knightfall.badges.BadgeRegistry
import com.chartmann.knightfall.ui.theme.Gold

@Composable
fun BadgeShelf(
    badgeIds: List<String>,
    modifier: Modifier = Modifier,
) {
    if (badgeIds.isEmpty()) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (id in badgeIds) {
            val badge = BadgeRegistry.getById(id) ?: continue
            BadgeChip(emoji = badge.emoji, name = badge.name)
        }
    }
}

@Composable
private fun BadgeChip(emoji: String, name: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(emoji, style = MaterialTheme.typography.bodyMedium)
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
