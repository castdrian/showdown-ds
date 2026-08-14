package dev.adrian.showdown

object BattleFeedText {
    fun window(entries: List<List<String>>, maxLines: Int, scrollLines: Int): List<String> {
        if (entries.isEmpty() || maxLines <= 0) return emptyList()
        val totalLines = entries.sumOf(List<String>::size)
        if (totalLines == 0) return emptyList()
        val maxScroll = (totalLines - maxLines).coerceAtLeast(0)
        val requestedScroll = scrollLines.coerceIn(0, maxScroll)
        val requestedEnd = totalLines - requestedScroll
        var lineCount = 0
        var endEntry = 0
        entries.forEachIndexed { index, entry ->
            val nextLineCount = lineCount + entry.size
            if (nextLineCount <= requestedEnd) {
                lineCount = nextLineCount
                endEntry = index + 1
            }
        }
        if (endEntry == 0) endEntry = 1
        val selected = ArrayDeque<List<String>>()
        var selectedLineCount = 0
        for (index in endEntry - 1 downTo 0) {
            val entry = entries[index]
            if (selectedLineCount + entry.size > maxLines) {
                if (selected.isEmpty()) selected.addFirst(entry.takeLast(maxLines))
                break
            }
            selected.addFirst(entry)
            selectedLineCount += entry.size
        }
        return selected.flatMap { it }
    }

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
