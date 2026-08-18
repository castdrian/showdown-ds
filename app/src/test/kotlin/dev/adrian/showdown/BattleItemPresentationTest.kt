package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleItemPresentationTest {
    @Test
    fun onlyRevealedItemsGetANameAndSprite() {
        assertEquals("Leftovers", BattleItemPresentation.visibleName(" Leftovers "))
        assertEquals(
            "sprites/itemicons/leftovers.png",
            BattleItemPresentation.iconPath("Leftovers")
        )
        assertNull(BattleItemPresentation.visibleName("Unknown item"))
        assertNull(BattleItemPresentation.iconPath("No item"))
    }
}
