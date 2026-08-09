package dev.adrian.showdown

object BattleFeedText {
    fun wrap(value: String, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> {
        if (maxWidth <= 0f || maxLines <= 0) return emptyList()
        val words = value.replace(Regex("\\s+"), " ").trim().split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var index = 0
        while (index < words.size && lines.size < maxLines) {
            var line = ""
            while (index < words.size) {
                val candidate = if (line.isBlank()) words[index] else "$line ${words[index]}"
                if (measure(candidate) <= maxWidth) {
                    line = candidate
                    index += 1
                } else {
                    break
                }
            }
            if (line.isBlank()) {
                line = ellipsize(words[index], maxWidth, measure)
                index += 1
            }
            lines += line
        }
        if (index < words.size && lines.isNotEmpty()) {
            val remainder = words.drop(index).joinToString(" ")
            lines[lines.lastIndex] = ellipsize("${lines.last()} $remainder", maxWidth, measure)
        }
        return lines
    }

    private fun ellipsize(value: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(value) <= maxWidth) return value
        var end = value.length
        while (end > 1) {
            val candidate = "${value.take(end - 1)}…"
            if (measure(candidate) <= maxWidth) return candidate
            end -= 1
        }
        return "…"
    }
}
