package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownHostCooldownTest {
    private var now = 1_000L
    private val cooldown = ShowdownHostCooldown(cooldownMillis = 60_000L, clock = { now })

    @Test
    fun unknownHostIsNotCoolingDown() {
        assertFalse(cooldown.isCoolingDown("www.pkparaiso.com"))
    }

    @Test
    fun unreachableHostIsSkippedUntilCooldownExpires() {
        cooldown.recordUnreachable("www.pkparaiso.com")

        assertTrue(cooldown.isCoolingDown("www.pkparaiso.com"))
        now += 59_999L
        assertTrue(cooldown.isCoolingDown("www.pkparaiso.com"))
        now += 1L
        assertFalse(cooldown.isCoolingDown("www.pkparaiso.com"))
    }

    @Test
    fun cooldownIsPerHost() {
        cooldown.recordUnreachable("www.pkparaiso.com")

        assertFalse(cooldown.isCoolingDown("raw.githubusercontent.com"))
    }

    @Test
    fun successfulContactClearsTheCooldown() {
        cooldown.recordUnreachable("www.pkparaiso.com")
        cooldown.recordReachable("www.pkparaiso.com")

        assertFalse(cooldown.isCoolingDown("www.pkparaiso.com"))
    }

    @Test
    fun showdownOwnHostIsNeverPutOnCooldown() {
        cooldown.recordUnreachable("play.pokemonshowdown.com")

        assertFalse(cooldown.isCoolingDown("play.pokemonshowdown.com"))
    }
}
