package com.chartmann.knightfall.data

import com.chartmann.knightfall.data.model.PublicProfile
import com.chartmann.knightfall.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun publicDoc(uid: String) = db.collection("publicProfiles").document(uid)

    suspend fun getProfile(uid: String): UserProfile? =
        userDoc(uid).get().await().toObject(UserProfile::class.java)

    fun profileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = userDoc(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(UserProfile::class.java))
        }
        awaitClose { reg.remove() }
    }

    suspend fun createProfile(uid: String, username: String, avatarColor: String) {
        val profile = UserProfile(
            username = username,
            avatarColor = avatarColor,
            createdAt = Timestamp.now(),
        )
        userDoc(uid).set(profile).await()
    }

    suspend fun updateUsername(uid: String, username: String) {
        userDoc(uid).update("username", username).await()
        val profile = getProfile(uid)
        if (profile?.isPublic == true) syncPublicProfile(uid)
    }

    /**
     * Applies a finished game to the local player's own stats. Rules allow
     * each player to write only their own documents, so each client applies
     * its own side after observing the game end.
     */
    suspend fun applyGameResult(
        uid: String,
        newElo: Int,
        won: Boolean,
        drew: Boolean,
    ) {
        db.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val profile = snap.toObject(UserProfile::class.java) ?: return@runTransaction
            val streak = if (won) profile.currentWinStreak + 1 else 0
            tx.update(
                userDoc(uid),
                mapOf(
                    "elo" to newElo,
                    "wins" to profile.wins + (if (won) 1 else 0),
                    "losses" to profile.losses + (if (!won && !drew) 1 else 0),
                    "draws" to profile.draws + (if (drew) 1 else 0),
                    "currentWinStreak" to streak,
                    "bestWinStreak" to maxOf(profile.bestWinStreak, streak),
                ),
            )
        }.await()
        val profile = getProfile(uid)
        if (profile?.isPublic == true) syncPublicProfile(uid)
    }

    suspend fun applyAiGameResult(uid: String, newElo: Int, won: Boolean, drew: Boolean) {
        db.runTransaction { tx ->
            val snap = tx.get(userDoc(uid))
            val profile = snap.toObject(UserProfile::class.java) ?: return@runTransaction
            val streak = if (won) profile.currentWinStreak + 1 else 0
            tx.update(
                userDoc(uid),
                mapOf(
                    "elo" to newElo,
                    "aiWins" to profile.aiWins + (if (won) 1 else 0),
                    "currentWinStreak" to streak,
                    "bestWinStreak" to maxOf(profile.bestWinStreak, streak),
                ),
            )
        }.await()
        val profile = getProfile(uid)
        if (profile?.isPublic == true) syncPublicProfile(uid)
    }

    /** Turns the public leaderboard profile on or off. */
    suspend fun setPublic(uid: String, public: Boolean) {
        userDoc(uid).update("isPublic", public).await()
        if (public) syncPublicProfile(uid) else publicDoc(uid).delete().await()
    }

    suspend fun syncPublicProfile(uid: String) {
        val p = getProfile(uid) ?: return
        if (!p.isPublic) return
        val mirror = PublicProfile(
            username = p.username,
            avatarColor = p.avatarColor,
            elo = p.elo,
            wins = p.wins,
            losses = p.losses,
            draws = p.draws,
            bestWinStreak = p.bestWinStreak,
            updatedAt = Timestamp.now(),
        )
        publicDoc(uid).set(mirror, SetOptions.merge()).await()
    }

    suspend fun leaderboard(limit: Long = 100): List<PublicProfile> =
        db.collection("publicProfiles")
            .orderBy("elo", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()
            .toObjects(PublicProfile::class.java)

    suspend fun deleteAllUserData(uid: String) {
        publicDoc(uid).delete().await()
        userDoc(uid).delete().await()
    }
}
