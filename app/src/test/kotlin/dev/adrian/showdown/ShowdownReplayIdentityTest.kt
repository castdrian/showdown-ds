package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownReplayIdentityTest {
    @Test
    fun formatsOpponentNamesWithShowdownPerspective() {
        val session = BattleSession().apply { setLocalUsername("DoerreKong") }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong||",
                "|player|p2|froonk53||",
                "|move|p2a: Rotom|Hex|p1a: Pikachu"
            )
        )

        assertEquals("The opposing Rotom used Hex!", session.battleLog().last())
        session.applyProtocolPacket(listOf("|faint|p2a: Rotom"))
        assertEquals("The opposing Rotom fainted!", session.battleLog().last())
    }

    @Test
    fun formatsNamesFromTheP2Perspective() {
        val session = BattleSession().apply { setLocalUsername("froonk53") }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong||",
                "|player|p2|froonk53||",
                "|move|p2a: Rotom|Hex|p1a: Pikachu"
            )
        )

        assertEquals("Rotom used Hex!", session.battleLog().last())
        session.applyProtocolPacket(listOf("|move|p1a: Pikachu|Tackle|p2a: Rotom"))
        assertEquals("The opposing Pikachu used Tackle!", session.battleLog().last())
    }

    @Test
    fun formatsSpectatorNamesWithTrainerNames() {
        val session = BattleSession().apply { setSpectatorMode(true) }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|Red||",
                "|player|p2|Blue||",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|move|p2a: Eevee|Tackle|p1a: Pikachu"
            )
        )

        assertTrue(session.battleLog().contains("Red sent out Pikachu!"))
        assertTrue(session.battleLog().contains("Blue sent out Eevee!"))
        assertEquals("Blue's Eevee used Tackle!", session.battleLog().last())
    }

    @Test
    fun usesEachTrainersNameForMultiBattleSwitches() {
        val session = BattleSession().apply { setLocalUsername("Red") }
        session.applyProtocolPacket(
            listOf(
                "|gametype|multi",
                "|player|p1|Red||",
                "|player|p2|Blue||",
                "|player|p3|Green||",
                "|player|p4|Yellow||",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|switch|p3a: AllyChu|Pikachu, L50|100/100",
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|switch|p4a: Pidgey|Pidgey, L50|100/100"
            )
        )

        assertTrue(session.battleLog().contains("Go! Pikachu!"))
        assertTrue(session.battleLog().contains("Green sent out AllyChu (Pikachu)!"))
        assertTrue(session.battleLog().contains("Blue sent out Eevee!"))
        assertTrue(session.battleLog().contains("Yellow sent out Pidgey!"))
    }

    @Test
    fun keepsOfficialReplayNicknameAlignedWithDisplayedSpecies() {
        val session = BattleSession().apply { setLocalUsername("DoerreKong") }
        session.setReplayMode(true)
        val replayExcerpt = listOf(
            "|player|p1|DoerreKong|156|1510",
            "|player|p2|froonk53|101|1189",
            "|gametype|doubles",
            "|switch|p1a: Dragapult|Dragapult, F|100/100",
            "|switch|p1b: Solgaleo|Solgaleo|100/100",
            "|switch|p2a: Grimmsnarl|Grimmsnarl, M, shiny|100/100",
            "|switch|p2b: Rotom|Rotom-Wash, shiny|100/100",
            "|move|p2b: Rotom|Hex|p1a: Dragapult"
        )
        BattlePlaybackTiming.chunks(replayExcerpt).forEach(session::applyProtocolPacket)

        val rotom = session.opponentActiveCombatants().single { it.slot == "p2b" }
        val spriteRequest = BattleSpriteRequests.active(
            session.opponentActiveCombatants(),
            BattleSpriteSide.OPPONENT,
            BattleSession.SpriteStyle.MODERN_3D
        ).single { it.slot == "p2b" }.request

        assertEquals("Rotom", rotom.name)
        assertEquals("Rotom-Wash", rotom.species)
        assertEquals("Rotom-Wash", spriteRequest.species)
        assertEquals(BattleSpriteSide.OPPONENT, spriteRequest.side)
        assertEquals(
            "froonk53 sent out Rotom (Rotom-Wash)!",
            session.battleLog().first { it.contains("sent out Rotom") }
        )
        assertTrue(session.battleLog().contains("The opposing Rotom used Hex!"))
        assertEquals("The opposing Rotom used Hex!", session.latestMoveEvent)
    }

    @Test
    fun updatesPlayerReplayRosterNameWhenSwitchRevealsNickname() {
        val session = BattleSession().apply {
            setLocalUsername("DoerreKong")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong|156|1510",
                "|player|p2|froonk53|101|1189",
                "|poke|p1|Rotom-Wash|",
                "|switch|p1a: Rotom|Rotom-Wash, L50|100/100"
            )
        )

        assertEquals(listOf("Rotom"), session.team())
        assertEquals("Rotom", session.playerPartyDetails().single().name)
        assertEquals("Rotom-Wash", session.playerPartyDetails().single().species)
        assertEquals("Rotom", session.playerActiveCombatants().single().name)
    }

    @Test
    fun disambiguatesSharedReplayNicknamesBySpecies() {
        val session = BattleSession().apply {
            setLocalUsername("DoerreKong")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong|156|1510",
                "|player|p2|froonk53|101|1189",
                "|poke|p1|Pikachu|",
                "|poke|p1|Raichu|",
                "|poke|p2|Pikachu|",
                "|poke|p2|Raichu|",
                "|switch|p1a: Sparky|Pikachu, L50|100/100",
                "|switch|p1a: Sparky|Raichu, L50|100/100",
                "|switch|p2a: Sparky|Pikachu, L50|100/100",
                "|switch|p2a: Sparky|Raichu, L50|100/100",
                "|move|p2a: Sparky|Tackle|p1a: Sparky"
            )
        )
        val opponentSpriteRequest = BattleSpriteRequests.active(
            session.opponentActiveCombatants(),
            BattleSpriteSide.OPPONENT,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request

        assertEquals(listOf("Pikachu", "Raichu"), session.playerPartyDetails().map { it.species })
        assertEquals(listOf("Sparky", "Sparky"), session.team())
        assertEquals("Raichu", session.playerActiveCombatants().single().species)
        assertEquals(listOf("Pikachu", "Raichu"), session.opponentPartyDetails().map { it.species })
        assertEquals(listOf("Sparky", "Sparky"), session.opponentPartyDetails().map { it.name })
        assertEquals("Raichu", opponentSpriteRequest.species)
        assertTrue(session.battleLog().contains("The opposing Sparky used Tackle!"))
    }

    @Test
    fun resolvesNicknamesWhenTeamPreviewMasksThePokemonForme() {
        val transcript = listOf(
            "|player|p1|DoerreKong|156|1510",
            "|player|p2|froonk53|101|1189",
            "|poke|p1|Arceus-*|",
            "|poke|p2|Arceus-*|",
            "|switch|p1a: Sparky|Arceus-Fire, L50|100/100",
            "|switch|p2a: Fuego|Arceus-Water, L50|100/100",
            "|move|p1a: Sparky|Judgment|p2a: Fuego",
            "|move|p2a: Fuego|Judgment|p1a: Sparky"
        )
        fun replayFor(username: String) = BattleSession().apply {
            setLocalUsername(username)
            setReplayMode(true)
            applyProtocolPacket(transcript)
        }
        val playerOnePerspective = replayFor("DoerreKong")
        val playerTwoPerspective = replayFor("froonk53")

        val playerSpriteRequest = BattleSpriteRequests.active(
            playerOnePerspective.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request
        val opponentSpriteRequest = BattleSpriteRequests.active(
            playerOnePerspective.opponentActiveCombatants(),
            BattleSpriteSide.OPPONENT,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request
        val playerTwoSpriteRequest = BattleSpriteRequests.active(
            playerTwoPerspective.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request

        assertEquals(listOf("Sparky"), playerOnePerspective.team())
        assertEquals("Arceus-Fire", playerOnePerspective.playerPartyDetails().single().species)
        assertEquals("Fuego", playerOnePerspective.opponentPartyDetails().single().name)
        assertEquals("Arceus-Water", playerOnePerspective.opponentPartyDetails().single().species)
        assertEquals("Arceus-Fire", playerSpriteRequest.species)
        assertEquals("Arceus-Water", opponentSpriteRequest.species)
        assertTrue(playerOnePerspective.battleLog().contains("Go! Sparky (Arceus-Fire)!"))
        assertTrue(playerOnePerspective.battleLog().contains("froonk53 sent out Fuego (Arceus-Water)!"))
        assertTrue(playerOnePerspective.battleLog().contains("Sparky used Judgment!"))
        assertTrue(playerOnePerspective.battleLog().contains("The opposing Fuego used Judgment!"))
        assertEquals(listOf("Fuego"), playerTwoPerspective.team())
        assertEquals("Arceus-Water", playerTwoPerspective.playerPartyDetails().single().species)
        assertEquals("Sparky", playerTwoPerspective.opponentPartyDetails().single().name)
        assertEquals("Arceus-Fire", playerTwoPerspective.opponentPartyDetails().single().species)
        assertEquals("Arceus-Water", playerTwoSpriteRequest.species)
        assertTrue(playerTwoPerspective.battleLog().contains("DoerreKong sent out Sparky (Arceus-Fire)!"))
        assertTrue(playerTwoPerspective.battleLog().contains("Go! Fuego (Arceus-Water)!"))
        assertTrue(playerTwoPerspective.battleLog().contains("The opposing Sparky used Judgment!"))
        assertTrue(playerTwoPerspective.battleLog().contains("Fuego used Judgment!"))
    }

    @Test
    fun keepsReplayLogActorsWithTheirSpritesAfterAnAllySwap() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|gametype|doubles",
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|poke|p1|Pikachu|",
                "|poke|p1|Charizard|",
                "|poke|p2|Eevee|",
                "|switch|p1a: Sparky|Pikachu, L50|100/100",
                "|switch|p1b: Blaze|Charizard, L50|100/100",
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|swap|p1a: Sparky|1",
                "|move|p1b: Sparky|Thunderbolt|p2a: Eevee"
            )
        )

        val movedPokemon = session.playerActiveCombatants().single { it.slot == "p1b" }
        val movedSpriteRequest = BattleSpriteRequests.active(
            session.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            BattleSession.SpriteStyle.MODERN_3D
        ).single { it.slot == "p1b" }.request

        assertEquals("Sparky", movedPokemon.name)
        assertEquals("Pikachu", movedPokemon.species)
        assertEquals("Pikachu", movedSpriteRequest.species)
        assertTrue(session.battleLog().contains("Sparky used Thunderbolt!"))
    }

    @Test
    fun keepsReplayNicknameAndSpriteSpeciesAlignedAfterPermanentFormChange() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|poke|p1|Charizard|",
                "|switch|p1a: Blaze|Charizard, L50|100/100",
                "|switch|p2a: Snorlax|Snorlax, L50|100/100",
                "|detailschange|p1a: Blaze|Charizard-Mega-X, L50|100/100",
                "|move|p1a: Blaze|Flamethrower|p2a: Snorlax"
            )
        )

        val active = session.playerActiveCombatants().single()
        val spriteRequest = BattleSpriteRequests.active(
            session.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request

        assertEquals(listOf("Blaze"), session.team())
        assertEquals("Blaze", active.name)
        assertEquals("Charizard-Mega-X", active.species)
        assertEquals("Charizard-Mega-X", spriteRequest.species)
        assertTrue(session.battleLog().contains("Blaze transformed!"))
        assertTrue(session.battleLog().contains("Blaze used Flamethrower!"))
    }

    @Test
    fun keepsProtocolPokemonNameStableWhenSpeciesChangesForme() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|switch|p1a: Charizard|Charizard, L50, M|153/153",
                "|switch|p2a: Blastoise|Blastoise, L50|150/150"
            )
        )
        session.applyProtocolPacket(listOf("|detailschange|p1a: Charizard|Charizard-Mega-X, L50, M|153/153"))

        val activeAfterFormChange = session.playerActiveCombatants().single()
        val spriteRequestAfterFormChange = BattleSpriteRequests.active(
            session.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request
        assertEquals("Charizard", activeAfterFormChange.name)
        assertEquals("Charizard-Mega-X", activeAfterFormChange.species)
        assertEquals("Charizard-Mega-X", spriteRequestAfterFormChange.species)

        session.applyProtocolPacket(listOf("|move|p1a: Charizard|Flamethrower|p2a: Blastoise"))
        session.applyProtocolPacket(listOf("|switch|p1a: Charizard|Venusaur, L50|150/150"))

        assertTrue(session.battleLog().contains("Charizard used Flamethrower!"))
        assertTrue(session.battleLog().contains("Charizard, come back!"))
    }
}
