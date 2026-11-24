package com.chartmann.knightfall.data

/**
 * Minimal objectionable-content filter for player names.
 *
 * Player names are the one piece of user-generated content other players
 * can see (public profiles + the leaderboard), so names are screened
 * before a profile is created or renamed. The check is deliberately
 * simple: the name is normalised (lower-cased, leetspeak folded,
 * separators stripped) and rejected if it contains a blocked term or
 * impersonates staff/the app. Substring matching can false-positive on
 * innocent names that contain a blocked string; that is the accepted
 * trade-off for a client-side filter, and the user can simply pick a
 * different name.
 */
object UsernameFilter {

    private val blockedTerms = listOf(
        // Profanity / sexual terms
        "fuck", "shit", "bitch", "bastard", "cunt", "dick", "cock",
        "pussy", "whore", "slut", "porn", "sex", "nude", "penis",
        "vagina", "boob", "jizz", "cum", "rapist", "pedo",
        // Slurs / hate terms
        "nigger", "nigga", "faggot", "fag", "retard", "spic", "kike",
        "chink", "wetback", "tranny", "nazi", "hitler",
        // Impersonation
        "admin", "moderator", "official", "support", "knightfall",
        "google", "firebase",
    )

    private fun normalize(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw.lowercase()) {
            val mapped = when (ch) {
                '0' -> 'o'
                '1', '!' -> 'i'
                '3' -> 'e'
                '4', '@' -> 'a'
                '5', '$' -> 's'
                '7' -> 't'
                else -> ch
            }
            if (mapped.isLetterOrDigit()) sb.append(mapped)
        }
        return sb.toString()
    }

    /** Returns a user-facing rejection reason, or null if the name is fine. */
    fun violation(rawName: String): String? {
        val name = normalize(rawName)
        if (name.isEmpty()) return "Please pick a player name."
        val hit = blockedTerms.firstOrNull { name.contains(it) } ?: return null
        return if (hit == "admin" || hit == "moderator" || hit == "official" ||
            hit == "support" || hit == "knightfall" || hit == "google" || hit == "firebase"
        ) {
            "That name looks like it impersonates the Knightfall team. Please pick a different name."
        } else {
            "That player name isn't allowed. Please pick a different name."
        }
    }

    fun isAcceptable(rawName: String): Boolean = violation(rawName) == null
}