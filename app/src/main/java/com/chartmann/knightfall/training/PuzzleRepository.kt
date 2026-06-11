package com.chartmann.knightfall.training

import android.content.Context
import com.chartmann.knightfall.data.model.Puzzle
import kotlinx.serialization.json.Json
import java.time.LocalDate

class PuzzleRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val puzzles: List<Puzzle> by lazy {
        val text = context.assets.open("puzzles.json").bufferedReader().readText()
        json.decodeFromString<List<Puzzle>>(text)
    }

    fun getAll(): List<Puzzle> = puzzles

    fun getById(id: String): Puzzle? = puzzles.firstOrNull { it.id == id }

    fun getDailyPuzzle(): Puzzle {
        val index = (LocalDate.now().toEpochDay() % puzzles.size).toInt()
        return puzzles[index]
    }

    fun getRandom(): Puzzle = puzzles.random()
}
