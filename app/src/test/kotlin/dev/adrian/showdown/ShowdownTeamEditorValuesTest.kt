package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownTeamEditorValuesTest {
    @Test
    fun blankValuesUseShowdownDefaults() {
        assertEquals(100, ShowdownTeamEditorValues.optionalInt("", 100))
        assertEquals(listOf(0, 252, 0, 0, 0, 4), ShowdownTeamEditorValues.statValues(listOf("", "252", "", "", "", "4"), 0))
    }

    @Test
    fun malformedValuesRemainInvalidForTeamValidation() {
        assertEquals(ShowdownTeamEditorValues.INVALID_NUMBER, ShowdownTeamEditorValues.optionalInt("abc", 255))
        assertEquals(
            listOf(ShowdownTeamEditorValues.INVALID_NUMBER, 31, 31, 31, 31, 31),
            ShowdownTeamEditorValues.statValues(listOf("abc"), 31)
        )
    }
}
