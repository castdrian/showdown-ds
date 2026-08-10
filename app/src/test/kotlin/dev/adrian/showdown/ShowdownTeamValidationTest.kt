package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownTeamValidationTest {
    @Test
    fun buildsOfficialValidationCommands() {
        assertEquals("/utm packed-team", ShowdownTeamValidation.setTeamCommand(" packed-team "))
        assertEquals("/vtm gen9ou", ShowdownTeamValidation.validateCommand(" gen9ou "))
    }

    @Test
    fun extractsValidationPopupText() {
        assertEquals(
            "Your team was rejected for the following reasons:\n- Species clause",
            ShowdownTeamValidation.response(listOf("|popup|Your team was rejected for the following reasons:||- Species clause"))
        )
        assertNull(ShowdownTeamValidation.response(listOf("|updateuser| Guest|0")))
    }
}
