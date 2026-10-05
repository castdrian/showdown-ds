package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownBattlePerspectiveTest {
    @Test
    fun acceptsEveryOfficialPlayerSideAndRejectsUnknownSides() {
        assertEquals("p1", ShowdownBattlePerspective.acceptedSide("p1"))
        assertEquals("p2", ShowdownBattlePerspective.acceptedSide("p2"))
        assertEquals("p3", ShowdownBattlePerspective.acceptedSide("p3"))
        assertEquals("p4", ShowdownBattlePerspective.acceptedSide("p4"))
        assertNull(ShowdownBattlePerspective.acceptedSide("p5"))
    }

    @Test
    fun generatedJavaScriptGuardAcceptsEveryOfficialPlayerSide() {
        assertEquals(
            "side !== 'p1' && side !== 'p2' && side !== 'p3' && side !== 'p4'",
            ShowdownBattlePerspective.javascriptGuard
        )
    }

    @Test
    fun preservesTheLocalPlayerSlotResolvedFromMultiProtocol() {
        val players = listOf("RED", "BLUE", "GREEN", "GOLD")

        players.forEachIndexed { index, username ->
            val expectedSide = "p${index + 1}"
            val session = BattleSession().apply {
                setLocalUsername(username)
                applyProtocolPacket(
                    listOf(
                        "|gametype|multi",
                        "|player|p1|RED||",
                        "|player|p2|BLUE||",
                        "|player|p3|GREEN||",
                        "|player|p4|GOLD||"
                    )
                )
            }

            assertEquals(expectedSide, session.battlePlayerSlot())
            assertEquals(expectedSide, ShowdownBattlePerspective.acceptedSide(session.battlePlayerSlot()))
        }
    }
}
