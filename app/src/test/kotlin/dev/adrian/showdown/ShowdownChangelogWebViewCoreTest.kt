package dev.adrian.showdown

import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownChangelogWebViewCoreTest {
    private val changes = ShowdownChangelog.entries("v-test").first().changes

    @Test
    fun ShowdownChangelog_CurrentEntry_ExplainsTheShowdownBattleScene() {
        assertTrue(changes.any { it.contains("Showdown web client") && it.contains("upper screen") })
    }

    @Test
    fun ShowdownChangelog_CurrentEntry_ExplainsResumeAndForfeitForUnfinishedBattles() {
        assertTrue(changes.any { it.contains("Unfinished battles") && it.contains("resume") && it.contains("forfeit") })
    }

    @Test
    fun ShowdownChangelog_CurrentEntry_ExplainsTheRicherPokemonTabCards() {
        assertTrue(changes.any { it.contains("Pokémon tab") && it.contains("held item") })
    }
}
