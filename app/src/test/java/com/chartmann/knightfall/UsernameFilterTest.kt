package com.chartmann.knightfall

import com.chartmann.knightfall.data.UsernameFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameFilterTest {

    @Test
    fun cleanNamesPass() {
        assertTrue(UsernameFilter.isAcceptable("KnightRider"))
        assertTrue(UsernameFilter.isAcceptable("quiet pawn"))
        assertTrue(UsernameFilter.isAcceptable("Eloise"))
        assertNull(UsernameFilter.violation("CastleKing42"))
    }

    @Test
    fun profanityIsBlocked() {
        assertFalse(UsernameFilter.isAcceptable("fuckyou"))
        assertFalse(UsernameFilter.isAcceptable("xX_bitch_Xx"))
        assertNotNull(UsernameFilter.violation("shitstorm"))
    }

    @Test
    fun leetspeakIsBlocked() {
        assertFalse(UsernameFilter.isAcceptable("fuck3r"))
        assertFalse(UsernameFilter.isAcceptable("b1tch"))
        assertFalse(UsernameFilter.isAcceptable("5lut"))
    }

    @Test
    fun separatorsDoNotBypass() {
        assertFalse(UsernameFilter.isAcceptable("f.u.c.k"))
        assertFalse(UsernameFilter.isAcceptable("n i g g e r"))
    }

    @Test
    fun impersonationIsBlocked() {
        assertFalse(UsernameFilter.isAcceptable("KnightfallAdmin"))
        assertFalse(UsernameFilter.isAcceptable("official_mod"))
    }
}
