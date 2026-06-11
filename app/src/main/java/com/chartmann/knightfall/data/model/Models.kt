package com.chartmann.knightfall.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/** Private profile stored at users/{uid}. */
data class UserProfile(
    @DocumentId val uid: String = "",
    val username: String = "",
    val avatarColor: String = "gold",
    val elo: Int = 1200,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val bestWinStreak: Int = 0,
    val currentWinStreak: Int = 0,
    val aiWins: Int = 0,
    val isPublic: Boolean = false,
    val createdAt: Timestamp? = null,
)

/** Public mirror stored at publicProfiles/{uid} for opted-in players. */
data class PublicProfile(
    @DocumentId val uid: String = "",
    val username: String = "",
    val avatarColor: String = "gold",
    val elo: Int = 1200,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val bestWinStreak: Int = 0,
    val updatedAt: Timestamp? = null,
)

object GameStatusValues {
    const val WAITING = "waiting"
    const val ACTIVE = "active"
    const val FINISHED = "finished"
}

object GameResultValues {
    const val WHITE_WIN = "white"
    const val BLACK_WIN = "black"
    const val DRAW = "draw"
}

object EndReasons {
    const val CHECKMATE = "checkmate"
    const val RESIGNATION = "resignation"
    const val DRAW_AGREED = "draw_agreed"
    const val STALEMATE = "stalemate"
    const val INSUFFICIENT_MATERIAL = "insufficient_material"
    const val REPETITION = "repetition"
    const val FIFTY_MOVES = "fifty_moves"
    const val ABANDONED = "abandoned"
}

/** Online game stored at games/{gameId}. */
data class OnlineGame(
    @DocumentId val id: String = "",
    val whiteUid: String? = null,
    val blackUid: String? = null,
    val whiteName: String = "",
    val blackName: String = "",
    val whiteEloStart: Int = 1200,
    val blackEloStart: Int = 1200,
    val moves: List<String> = emptyList(),
    val status: String = GameStatusValues.WAITING,
    val result: String? = null,
    val endReason: String? = null,
    val rated: Boolean = true,
    val isPrivate: Boolean = false,
    val inviteCode: String? = null,
    val drawOfferBy: String? = null,
    val rematchOfferBy: String? = null,
    val eloAppliedWhite: Boolean = false,
    val eloAppliedBlack: Boolean = false,
    val createdAt: Timestamp? = null,
    val lastMoveAt: Timestamp? = null,
    val finishedAt: Timestamp? = null,
) {
    fun seatOf(uid: String): String? = when (uid) {
        whiteUid -> "white"
        blackUid -> "black"
        else -> null
    }

    fun opponentName(uid: String): String =
        if (uid == whiteUid) blackName else whiteName
}

/** Invite code lookup doc at inviteCodes/{code}. */
data class InviteCode(
    @DocumentId val code: String = "",
    val gameId: String = "",
    val hostUid: String = "",
    val createdAt: Timestamp? = null,
)
