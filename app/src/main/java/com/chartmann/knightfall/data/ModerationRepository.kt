package com.chartmann.knightfall.data

import com.chartmann.knightfall.data.model.PublicProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Reasons offered by the in-app "Report player" flow. [value] is stored in Firestore. */
enum class ReportReason(val value: String, val label: String) {
    USERNAME("username", "Inappropriate username"),
    CHEATING("cheating", "Cheating"),
    HARASSMENT("harassment", "Harassment"),
    OTHER("other", "Other"),
}

/** Pure validation for a report, kept separate so it is unit-testable. */
object ReportValidation {
    /** Returns an error message, or null when the report may be filed. */
    fun validate(reporterUid: String?, reportedUid: String): String? = when {
        reporterUid.isNullOrBlank() -> "You need to be signed in to report a player."
        reportedUid.isBlank() -> "That player can't be reported."
        reporterUid == reportedUid -> "You can't report yourself."
        else -> null
    }
}

/** Pure blocked-player filtering, kept separate so it is unit-testable. */
object BlockFilter {
    /** Removes blocked players (never the signed-in player themselves). */
    fun filterProfiles(
        profiles: List<PublicProfile>,
        blockedUids: Collection<String>,
        myUid: String?,
    ): List<PublicProfile> {
        if (blockedUids.isEmpty()) return profiles
        val blocked = blockedUids.toSet()
        return profiles.filter { it.uid !in blocked || it.uid == myUid }
    }

    /** True when [uid] is a player the signed-in user should not be matched with. */
    fun isBlockedOpponent(uid: String?, blockedUids: Collection<String>): Boolean =
        uid != null && uid in blockedUids.toSet()
}

/**
 * Player moderation: reports and blocks.
 *
 * Reports are written to the `reports` collection (create-only for clients;
 * reviewed by the developer in the Firebase console). Blocks live on the
 * blocker's own user doc (`blockedUids`) and are applied client-side to the
 * leaderboard and to quick-match opponent selection.
 */
class ModerationRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    suspend fun reportPlayer(
        reporterUid: String,
        reportedUid: String,
        reportedName: String,
        reason: ReportReason,
        context: String,
    ) {
        ReportValidation.validate(reporterUid, reportedUid)?.let {
            throw IllegalArgumentException(it)
        }
        db.collection("reports").add(
            mapOf(
                "reporterUid" to reporterUid,
                "reportedUid" to reportedUid,
                "reportedName" to reportedName,
                "reason" to reason.value,
                "context" to context,
                "createdAt" to Timestamp.now(),
                "status" to "open",
            ),
        ).await()
    }

    suspend fun blockPlayer(uid: String, targetUid: String) {
        require(targetUid.isNotBlank() && targetUid != uid) { "You can't block yourself." }
        db.collection("users").document(uid)
            .update("blockedUids", FieldValue.arrayUnion(targetUid)).await()
    }

    suspend fun unblockPlayer(uid: String, targetUid: String) {
        db.collection("users").document(uid)
            .update("blockedUids", FieldValue.arrayRemove(targetUid)).await()
    }
}
