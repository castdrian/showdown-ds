package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFeedMessageIdentityTest {
    @Test
    fun ignoresShowdownPunctuationAndWhitespaceDifferences() {
        assertTrue(BattleFeedMessageIdentity.matches(" Pikachu restored HP!  ", "Pikachu recovered health."))
    }

    @Test
    fun keepsDifferentBattleEventsDistinct() {
        assertFalse(BattleFeedMessageIdentity.matches("Pikachu used Thunderbolt!", "Pikachu used Tackle!"))
    }
}
