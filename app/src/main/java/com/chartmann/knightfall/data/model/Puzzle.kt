package com.chartmann.knightfall.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Puzzle(
    val id: String,
    val fen: String,
    val moves: List<String>,
    val rating: Int = 1500,
    val themes: List<String> = emptyList(),
)
