package com.chartmann.knightfall.badges

import com.chartmann.knightfall.data.model.UserProfile

object BadgeChecker {

    fun checkAll(profile: UserProfile): List<String> =
        checkGameBadges(profile) + checkPuzzleBadges(profile)

    fun checkGameBadges(profile: UserProfile): List<String> {
        val earned = profile.earnedBadges.toSet()
        val new = mutableListOf<String>()

        fun award(id: String, condition: Boolean) {
            if (condition && id !in earned) new += id
        }

        val totalGames = profile.wins + profile.losses + profile.draws
        award("first_win",    profile.wins >= 1)
        award("ten_wins",     profile.wins >= 10)
        award("fifty_wins",   profile.wins >= 50)
        award("streak_3",     profile.bestWinStreak >= 3)
        award("streak_10",    profile.bestWinStreak >= 10)
        award("first_ai_win", profile.aiWins >= 1)
        award("ai_wins_10",   profile.aiWins >= 10)
        award("ai_wins_50",   profile.aiWins >= 50)
        award("elo_1400",     profile.elo >= 1400)
        award("elo_1600",     profile.elo >= 1600)
        award("elo_1800",     profile.elo >= 1800)
        award("elo_2000",     profile.elo >= 2000)
        award("hundred_games",totalGames >= 100)
        award("comeback",     profile.wins >= 1 && profile.losses >= 10)

        return new
    }

    fun checkPuzzleBadges(profile: UserProfile): List<String> {
        val earned = profile.earnedBadges.toSet()
        val new = mutableListOf<String>()

        fun award(id: String, condition: Boolean) {
            if (condition && id !in earned) new += id
        }

        award("first_puzzle",    profile.puzzlesSolved >= 1)
        award("puzzles_10",      profile.puzzlesSolved >= 10)
        award("puzzles_50",      profile.puzzlesSolved >= 50)
        award("puzzles_100",     profile.puzzlesSolved >= 100)
        award("puzzle_streak_5", profile.bestPuzzleStreak >= 5)
        award("puzzle_streak_10",profile.bestPuzzleStreak >= 10)

        return new
    }
}
