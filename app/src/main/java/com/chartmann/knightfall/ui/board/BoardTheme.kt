package com.chartmann.knightfall.ui.board

import androidx.compose.ui.graphics.Color
import com.chartmann.knightfall.ui.theme.BoardEmeraldDark
import com.chartmann.knightfall.ui.theme.BoardEmeraldLight
import com.chartmann.knightfall.ui.theme.BoardSlateDark
import com.chartmann.knightfall.ui.theme.BoardSlateLight
import com.chartmann.knightfall.ui.theme.BoardWalnutDark
import com.chartmann.knightfall.ui.theme.BoardWalnutLight

enum class BoardTheme(
    val id: String,
    val displayName: String,
    val lightSquare: Color,
    val darkSquare: Color,
) {
    WALNUT("walnut", "Walnut", BoardWalnutLight, BoardWalnutDark),
    SLATE("slate", "Slate", BoardSlateLight, BoardSlateDark),
    EMERALD("emerald", "Emerald", BoardEmeraldLight, BoardEmeraldDark);

    companion object {
        fun fromId(id: String): BoardTheme = entries.firstOrNull { it.id == id } ?: WALNUT
    }
}
