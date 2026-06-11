package com.chartmann.knightfall.badges

import com.chartmann.knightfall.data.model.Badge

object BadgeRegistry {

    val all: List<Badge> = listOf(
        Badge("first_win",       "First Blood",        "Win your first online game",           "⚔️"),
        Badge("ten_wins",        "Veteran",            "Win 10 online games",                  "🎖️"),
        Badge("fifty_wins",      "Knight Errant",      "Win 50 online games",                  "🏇"),
        Badge("streak_3",        "On a Roll",          "Achieve a 3-game win streak",          "🔥"),
        Badge("streak_10",       "Unstoppable",        "Achieve a 10-game win streak",         "💫"),
        Badge("first_ai_win",    "Machine Slayer",     "Beat the AI for the first time",       "🤖"),
        Badge("ai_wins_10",      "Engine Killer",      "Beat the AI 10 times",                 "⚡"),
        Badge("ai_wins_50",      "Grandmaster's Bane", "Beat the AI 50 times",                 "🏆"),
        Badge("elo_1400",        "Rising Star",        "Reach 1400 Elo",                       "⭐"),
        Badge("elo_1600",        "Expert",             "Reach 1600 Elo",                       "🌟"),
        Badge("elo_1800",        "Master Candidate",   "Reach 1800 Elo",                       "💎"),
        Badge("elo_2000",        "Master",             "Reach 2000 Elo",                       "👑"),
        Badge("first_puzzle",    "Tactician",          "Solve your first puzzle",              "🧩"),
        Badge("puzzles_10",      "Puzzle Hunter",      "Solve 10 puzzles",                     "🔍"),
        Badge("puzzles_50",      "Puzzle Addict",      "Solve 50 puzzles",                     "🎯"),
        Badge("puzzles_100",     "Tactical Wizard",    "Solve 100 puzzles",                    "🪄"),
        Badge("puzzle_streak_5", "Sharp Eye",          "Solve 5 puzzles in a row correctly",   "👁️"),
        Badge("puzzle_streak_10","Laser Focus",        "Solve 10 puzzles in a row correctly",  "🎯"),
        Badge("hundred_games",   "Battle Hardened",    "Play 100 games total",                 "🛡️"),
        Badge("comeback",        "The Comeback Kid",   "Win after suffering 10 losses",        "💪"),
    )

    private val byId = all.associateBy { it.id }

    fun getById(id: String): Badge? = byId[id]
}
