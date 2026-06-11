package com.chartmann.knightfall.data

import com.chartmann.knightfall.chess.EloCalculator
import com.chartmann.knightfall.chess.GameOutcome
import com.chartmann.knightfall.data.model.EndReasons
import com.chartmann.knightfall.data.model.GameResultValues
import com.chartmann.knightfall.data.model.GameStatusValues
import com.chartmann.knightfall.data.model.OnlineGame
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class GameRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val games get() = db.collection("games")
    private val inviteCodes get() = db.collection("inviteCodes")

    fun gameFlow(gameId: String): Flow<OnlineGame?> = callbackFlow {
        val reg = games.document(gameId).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(OnlineGame::class.java))
        }
        awaitClose { reg.remove() }
    }

    /** Creates a private game and registers its invite code. Host plays white. */
    suspend fun createInviteGame(uid: String, username: String, elo: Int, rated: Boolean): Pair<String, String> {
        val code = generateInviteCode()
        val doc = games.document()
        val game = OnlineGame(
            whiteUid = uid,
            whiteName = username,
            whiteEloStart = elo,
            status = GameStatusValues.WAITING,
            rated = rated,
            isPrivate = true,
            inviteCode = code,
            createdAt = Timestamp.now(),
        )
        doc.set(game).await()
        inviteCodes.document(code).set(
            mapOf("gameId" to doc.id, "hostUid" to uid, "createdAt" to Timestamp.now()),
        ).await()
        return doc.id to code
    }

    /** Joins a friend's game via invite code. Joiner takes the black seat. */
    suspend fun joinByInviteCode(code: String, uid: String, username: String, elo: Int): String {
        val normalized = code.trim().uppercase()
        val lookup = inviteCodes.document(normalized).get().await()
        val gameId = lookup.getString("gameId") ?: error("Invite code not found")
        claimSeat(gameId, uid, username, elo)
        inviteCodes.document(normalized).delete().await()
        return gameId
    }

    /**
     * Quick match: claim the oldest open public game, or create one and
     * wait. Firestore transactions retry on contention, and the security
     * rules only allow claiming a seat on a game still in 'waiting'.
     */
    suspend fun quickMatch(uid: String, username: String, elo: Int): String {
        // Ignore stale lobbies whose host probably left without cancelling.
        val freshCutoff = Timestamp(
            com.google.firebase.Timestamp.now().seconds - QUICK_MATCH_MAX_AGE_SECONDS, 0,
        )
        val candidates = games
            .whereEqualTo("status", GameStatusValues.WAITING)
            .whereEqualTo("isPrivate", false)
            .whereGreaterThan("createdAt", freshCutoff)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(5)
            .get().await()
            .toObjects(OnlineGame::class.java)
            .filter { it.whiteUid != uid && it.blackUid != uid }

        for (candidate in candidates) {
            try {
                claimSeat(candidate.id, uid, username, elo)
                return candidate.id
            } catch (_: Exception) {
                // someone else got it first; try the next one
            }
        }

        // No open game — create one and wait for an opponent.
        val doc = games.document()
        val game = OnlineGame(
            whiteUid = uid,
            whiteName = username,
            whiteEloStart = elo,
            status = GameStatusValues.WAITING,
            rated = true,
            isPrivate = false,
            createdAt = Timestamp.now(),
        )
        doc.set(game).await()
        return doc.id
    }

    private suspend fun claimSeat(gameId: String, uid: String, username: String, elo: Int) {
        val ref = games.document(gameId)
        db.runTransaction { tx ->
            val game = tx.get(ref).toObject(OnlineGame::class.java)
                ?: error("Game not found")
            check(game.status == GameStatusValues.WAITING) { "Game already started" }
            check(game.whiteUid != uid && game.blackUid != uid) { "Already seated" }
            if (game.blackUid == null) {
                tx.update(
                    ref,
                    mapOf(
                        "blackUid" to uid,
                        "blackName" to username,
                        "blackEloStart" to elo,
                        "status" to GameStatusValues.ACTIVE,
                        "lastMoveAt" to Timestamp.now(),
                    ),
                )
            } else {
                tx.update(
                    ref,
                    mapOf(
                        "whiteUid" to uid,
                        "whiteName" to username,
                        "whiteEloStart" to elo,
                        "status" to GameStatusValues.ACTIVE,
                        "lastMoveAt" to Timestamp.now(),
                    ),
                )
            }
        }.await()
    }

    suspend fun cancelWaitingGame(gameId: String) {
        val snap = games.document(gameId).get().await()
        val game = snap.toObject(OnlineGame::class.java) ?: return
        if (game.status == GameStatusValues.WAITING) {
            game.inviteCode?.let { inviteCodes.document(it).delete().await() }
            games.document(gameId).delete().await()
        }
    }

    suspend fun playMove(gameId: String, uci: String) {
        games.document(gameId).update(
            mapOf(
                "moves" to FieldValue.arrayUnion(uci),
                "lastMoveAt" to Timestamp.now(),
                "drawOfferBy" to null,
            ),
        ).await()
    }

    suspend fun finishGame(gameId: String, result: String, reason: String) {
        games.document(gameId).update(
            mapOf(
                "status" to GameStatusValues.FINISHED,
                "result" to result,
                "endReason" to reason,
                "finishedAt" to Timestamp.now(),
            ),
        ).await()
    }

    suspend fun resign(gameId: String, myColor: String) {
        val result = if (myColor == "white") GameResultValues.BLACK_WIN else GameResultValues.WHITE_WIN
        finishGame(gameId, result, EndReasons.RESIGNATION)
    }

    suspend fun offerDraw(gameId: String, uid: String) {
        games.document(gameId).update("drawOfferBy", uid).await()
    }

    suspend fun acceptDraw(gameId: String) {
        finishGame(gameId, GameResultValues.DRAW, EndReasons.DRAW_AGREED)
    }

    suspend fun declineDraw(gameId: String) {
        games.document(gameId).update("drawOfferBy", null).await()
    }

    /** Claim a win when the opponent has been silent past the threshold. */
    suspend fun claimAbandonment(gameId: String, myColor: String) {
        val result = if (myColor == "white") GameResultValues.WHITE_WIN else GameResultValues.BLACK_WIN
        finishGame(gameId, result, EndReasons.ABANDONED)
    }

    /**
     * Marks this player's Elo as applied on the game doc (idempotent via
     * transaction) and returns the player's new rating, or null if it was
     * already applied.
     */
    suspend fun markEloApplied(gameId: String, myColor: String): Int? {
        val ref = games.document(gameId)
        return db.runTransaction { tx ->
            val game = tx.get(ref).toObject(OnlineGame::class.java)
                ?: return@runTransaction null
            if (game.status != GameStatusValues.FINISHED || game.result == null || !game.rated) {
                return@runTransaction null
            }
            val alreadyApplied =
                if (myColor == "white") game.eloAppliedWhite else game.eloAppliedBlack
            if (alreadyApplied) return@runTransaction null

            val outcome = when (game.result) {
                GameResultValues.WHITE_WIN -> GameOutcome.WHITE_WIN
                GameResultValues.BLACK_WIN -> GameOutcome.BLACK_WIN
                else -> GameOutcome.DRAW
            }
            val (newWhite, newBlack) =
                EloCalculator.newRatings(game.whiteEloStart, game.blackEloStart, outcome)
            tx.update(ref, if (myColor == "white") "eloAppliedWhite" else "eloAppliedBlack", true)
            if (myColor == "white") newWhite else newBlack
        }.await()
    }

    suspend fun offerRematch(gameId: String, uid: String) {
        games.document(gameId).update("rematchOfferBy", uid).await()
    }

    /** Past games for the profile history screen. */
    suspend fun recentGames(uid: String, limit: Long = 25): List<OnlineGame> {
        val asWhite = games
            .whereEqualTo("whiteUid", uid)
            .orderBy("finishedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await().toObjects(OnlineGame::class.java)
        val asBlack = games
            .whereEqualTo("blackUid", uid)
            .orderBy("finishedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await().toObjects(OnlineGame::class.java)
        return (asWhite + asBlack)
            .filter { it.status == GameStatusValues.FINISHED }
            .sortedByDescending { it.finishedAt }
            .take(limit.toInt())
    }

    companion object {
        const val QUICK_MATCH_MAX_AGE_SECONDS = 15L * 60

        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateInviteCode(random: Random = Random.Default): String =
            (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
                .joinToString("")
    }
}
