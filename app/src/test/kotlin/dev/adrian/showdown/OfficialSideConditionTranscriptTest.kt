package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialSideConditionTranscriptTest {
    @Test
    fun formatsOfficialSideConditionAnnouncementsForScreensAndPledges() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }

        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|-sidestart|p1: ADRIAN|move: Crafty Shield",
                "|-sidestart|p2: OPPONENT|move: Lucky Chant",
                "|-sideend|p2: OPPONENT|move: Lucky Chant",
                "|-sidestart|p2: OPPONENT|move: Fire Pledge",
                "|-sideend|p2: OPPONENT|move: Fire Pledge",
                "|-sidestart|p1: ADRIAN|move: Grass Pledge",
                "|-sideend|p1: ADRIAN|move: Grass Pledge",
                "|-sidestart|p1: ADRIAN|move: Water Pledge",
                "|-sideend|p1: ADRIAN|move: Water Pledge"
            )
        )

        assertEquals(
            listOf(
                "  Crafty Shield protected your team!",
                "  Lucky Chant shielded the opposing team from critical hits!",
                "  The opposing team's Lucky Chant wore off!",
                "  A sea of fire enveloped the opposing team!",
                "  The sea of fire around the opposing team disappeared!",
                "  A swamp enveloped your team!",
                "  The swamp around your team disappeared!",
                "  A rainbow appeared in the sky on your team's side!",
                "  The rainbow on your team's side disappeared!"
            ),
            session.battleLog().takeLast(9)
        )
    }
}
