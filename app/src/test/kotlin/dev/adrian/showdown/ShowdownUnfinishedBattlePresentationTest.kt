package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownUnfinishedBattlePresentationTest {
    private val entry = ShowdownUnfinishedBattles.Entry(
        roomId = "battle-gen9randombattle-2",
        format = "[Gen 9] Random Battle",
        title = "CaptureAlpha vs. StallBot"
    )

    @Test
    fun UnfinishedBattlePresentation_Label_ShowsTheOpponentTitleThenTheFormat() {
        val label = ShowdownUnfinishedBattlePresentation.label(entry)

        assertEquals("CaptureAlpha vs. StallBot\n[Gen 9] Random Battle", label)
    }

    @Test
    fun UnfinishedBattlePresentation_Message_NamesTheBattleAndWhatForfeitingMeans() {
        val message = ShowdownUnfinishedBattlePresentation.actionsMessage(entry)

        assertEquals(
            "CaptureAlpha vs. StallBot\n[Gen 9] Random Battle\n\nResume to rejoin it, or forfeit to concede.",
            message
        )
    }
}
