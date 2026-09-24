package dev.adrian.showdown

object ShowdownTeamEditorValues {
    const val INVALID_NUMBER = -1

    fun optionalInt(value: String, default: Int): Int = value.trim().let { text ->
        if (text.isBlank()) default else text.toIntOrNull() ?: INVALID_NUMBER
    }

    fun statValues(values: List<String>, default: Int): List<Int> = (0 until 6).map { index ->
        optionalInt(values.getOrNull(index).orEmpty(), default)
    }
}
