package com.chartmann.knightfall

import com.chartmann.knightfall.data.BlockFilter
import com.chartmann.knightfall.data.ReportReason
import com.chartmann.knightfall.data.ReportValidation
import com.chartmann.knightfall.data.model.PublicProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationTest {

    private fun profile(uid: String) = PublicProfile(uid = uid, username = "player-$uid")

    @Test
    fun reportValidation_rejectsSignedOutReporter() {
        assertEquals(
            "You need to be signed in to report a player.",
            ReportValidation.validate(null, "abc"),
        )
        assertEquals(
            "You need to be signed in to report a player.",
            ReportValidation.validate("", "abc"),
        )
    }

    @Test
    fun reportValidation_rejectsBlankTargetAndSelfReport() {
        assertEquals("That player can't be reported.", ReportValidation.validate("me", ""))
        assertEquals("You can't report yourself.", ReportValidation.validate("me", "me"))
    }

    @Test
    fun reportValidation_acceptsOrdinaryReport() {
        assertNull(ReportValidation.validate("me", "them"))
    }

    @Test
    fun reportReasons_haveStableStoredValues() {
        assertEquals(
            listOf("username", "cheating", "harassment", "other"),
            ReportReason.entries.map { it.value },
        )
    }

    @Test
    fun blockFilter_removesBlockedPlayers() {
        val players = listOf(profile("a"), profile("b"), profile("c"))
        val filtered = BlockFilter.filterProfiles(players, listOf("b"), myUid = "a")
        assertEquals(listOf("a", "c"), filtered.map { it.uid })
    }

    @Test
    fun blockFilter_neverRemovesSelf() {
        val players = listOf(profile("a"), profile("b"))
        val filtered = BlockFilter.filterProfiles(players, listOf("a", "b"), myUid = "a")
        assertEquals(listOf("a"), filtered.map { it.uid })
    }

    @Test
    fun blockFilter_emptyBlockListIsIdentity() {
        val players = listOf(profile("a"), profile("b"))
        assertEquals(players, BlockFilter.filterProfiles(players, emptyList(), myUid = "a"))
    }

    @Test
    fun isBlockedOpponent_matchesOnlyBlocked() {
        assertTrue(BlockFilter.isBlockedOpponent("x", listOf("x", "y")))
        assertFalse(BlockFilter.isBlockedOpponent("z", listOf("x", "y")))
        assertFalse(BlockFilter.isBlockedOpponent(null, listOf("x")))
    }
}
