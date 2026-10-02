package dev.adrian.showdown

object BattleFeedText {
    data class StyledRun(
        val text: String,
        val emphasized: Boolean
    )

    data class ActivityLine(
        val messageIndex: Int,
        val text: String
    )

    fun wrapShowdownMarkup(
        value: String,
        maxWidth: Float,
        maxLines: Int,
        measure: (String, Boolean) -> Float
    ): List<List<StyledRun>> {
        if (maxWidth <= 0f || maxLines <= 0) return emptyList()
        val runs = normalizeWhitespace(parseShowdownMarkup(value))
        val plainText = runs.joinToString("") { it.text }
        if (plainText.isBlank()) return emptyList()
        val lines = wrap(plainText, maxWidth, maxLines) { text ->
            maxOf(measure(text, false), measure(text, true))
        }
        var searchFrom = 0
        return lines.map { line ->
            val exactStart = plainText.indexOf(line, searchFrom)
            val hasEllipsis = exactStart < 0 && line.endsWith("…")
            val sourceText = if (hasEllipsis) line.dropLast(1) else line
            val start = if (exactStart >= 0) exactStart else plainText.indexOf(sourceText, searchFrom)
            if (start < 0) {
                listOf(StyledRun(line, false))
            } else {
                val sourceEnd = start + sourceText.length
                searchFrom = sourceEnd
                val styledLine = sliceRuns(runs, start, sourceEnd).toMutableList()
                if (hasEllipsis) appendRun(styledLine, "…", styledLine.lastOrNull()?.emphasized == true)
                styledLine
            }
        }
    }

    private fun parseShowdownMarkup(value: String): List<StyledRun> {
        val runs = mutableListOf<StyledRun>()
        val emphasis = Regex("\\*\\*(.+?)\\*\\*")
        var start = 0
        emphasis.findAll(value).forEach { match ->
            appendRun(runs, value.substring(start, match.range.first), false)
            appendRun(runs, match.groupValues[1], true)
            start = match.range.last + 1
        }
        appendRun(runs, value.substring(start), false)
        return runs
    }

    private fun normalizeWhitespace(runs: List<StyledRun>): List<StyledRun> {
        val normalized = mutableListOf<StyledRun>()
        var pendingSpace = false
        var pendingSpaceEmphasized = false
        runs.forEach { run ->
            var index = 0
            while (index < run.text.length) {
                val codePoint = run.text.codePointAt(index)
                val nextIndex = index + Character.charCount(codePoint)
                val value = run.text.substring(index, nextIndex)
                if (Character.isWhitespace(codePoint)) {
                    pendingSpace = normalized.isNotEmpty()
                    pendingSpaceEmphasized = run.emphasized
                } else {
                    if (pendingSpace) appendRun(normalized, " ", pendingSpaceEmphasized)
                    appendRun(normalized, value, run.emphasized)
                    pendingSpace = false
                }
                index = nextIndex
            }
        }
        return normalized
    }

    private fun sliceRuns(runs: List<StyledRun>, start: Int, end: Int): List<StyledRun> {
        val sliced = mutableListOf<StyledRun>()
        var offset = 0
        runs.forEach { run ->
            val runEnd = offset + run.text.length
            val sliceStart = maxOf(start, offset)
            val sliceEnd = minOf(end, runEnd)
            if (sliceStart < sliceEnd) {
                appendRun(
                    sliced,
                    run.text.substring(sliceStart - offset, sliceEnd - offset),
                    run.emphasized
                )
            }
            offset = runEnd
        }
        return sliced
    }

    private fun appendRun(runs: MutableList<StyledRun>, text: String, emphasized: Boolean) {
        if (text.isEmpty()) return
        val previous = runs.lastOrNull()
        if (previous?.emphasized == emphasized) {
            runs[runs.lastIndex] = previous.copy(text = previous.text + text)
        } else {
            runs += StyledRun(text, emphasized)
        }
    }

    fun window(entries: List<List<String>>, maxLines: Int, scrollLines: Int): List<String> {
        if (entries.isEmpty() || maxLines <= 0) return emptyList()
        val totalLines = entries.sumOf(List<String>::size)
        if (totalLines == 0) return emptyList()
        if (entries.any { it.size > maxLines }) {
            return oversizedEntryWindow(entries, maxLines, scrollLines)
        }
        val maxScroll = (totalLines - maxLines).coerceAtLeast(0)
        val requestedScroll = scrollLines.coerceIn(0, maxScroll)
        val requestedEnd = totalLines - requestedScroll
        var lineCount = 0
        var endEntry = 0
        for ((index, entry) in entries.withIndex()) {
            val nextLineCount = lineCount + entry.size
            if (nextLineCount <= requestedEnd) {
                lineCount = nextLineCount
                endEntry = index + 1
            } else break
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

    fun wrapForBattleFeed(value: String, maxWidth: Float, measure: (String) -> Float): List<String> =
        wrap(value, maxWidth, Int.MAX_VALUE, measure)

    fun activityWindow(
        messages: List<String>,
        focusedMessage: Int,
        maxLines: Int,
        maxWidth: Float,
        measure: (String) -> Float
    ): List<ActivityLine> {
        if (messages.isEmpty() || maxLines <= 0 || maxWidth <= 0f) return emptyList()
        val entries = messages.mapIndexed { index, message ->
            wrapForBattleFeed(message, maxWidth, measure)
                .ifEmpty { listOf("") }
                .map { ActivityLine(index, it) }
        }
        val flattened = entries.flatten()
        if (flattened.isEmpty()) return emptyList()
        val focusedIndex = focusedMessage.coerceIn(0, messages.lastIndex)
        val focusedLine = entries
            .take(focusedIndex)
            .sumOf { it.size } + entries[focusedIndex].lastIndex
        val maxStart = (flattened.size - maxLines).coerceAtLeast(0)
        val start = (focusedLine - maxLines + 1).coerceIn(0, maxStart)
        return flattened.subList(start, (start + maxLines).coerceAtMost(flattened.size))
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
                val word = words[index]
                if (measure(word) > maxWidth) {
                    val chunks = splitLongWord(word, maxWidth, measure)
                    val remaining = maxLines - lines.size
                    lines += chunks.take(remaining)
                    index += 1
                    if (chunks.size > remaining && index == words.size) {
                        lines.lastOrNull()?.let { lines[lines.lastIndex] = ellipsize("${it}…", maxWidth, measure) }
                    }
                    continue
                }
                line = word
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

    private fun oversizedEntryWindow(entries: List<List<String>>, maxLines: Int, scrollLines: Int): List<String> {
        val lastOversizedIndex = entries.indexOfLast { it.size > maxLines }
        val trailingLines = if (lastOversizedIndex >= 0) {
            entries.drop(lastOversizedIndex + 1).sumOf(List<String>::size)
        } else {
            0
        }
        if (lastOversizedIndex >= 0 && trailingLines > 0 && scrollLines > 0) {
            val oversized = entries[lastOversizedIndex]
            val offset = (scrollLines - 1).coerceIn(0, (oversized.size - maxLines).coerceAtLeast(0))
            val start = (oversized.size - maxLines - offset).coerceAtLeast(0)
            return oversized.subList(start, (start + maxLines).coerceAtMost(oversized.size))
        }
        val segments = entries.flatMap { entry ->
            if (entry.size > maxLines) entry.map(::listOf) else listOf(entry)
        }
        val totalLines = segments.sumOf(List<String>::size)
        val maxScroll = (totalLines - maxLines).coerceAtLeast(0)
        val requestedScroll = scrollLines.coerceIn(0, maxScroll)
        val requestedEnd = totalLines - requestedScroll
        var lineCount = 0
        var endSegment = 0
        for ((index, segment) in segments.withIndex()) {
            val nextLineCount = lineCount + segment.size
            if (nextLineCount <= requestedEnd) {
                lineCount = nextLineCount
                endSegment = index + 1
            } else break
        }
        if (endSegment == 0) endSegment = 1
        val selected = ArrayDeque<List<String>>()
        var selectedLineCount = 0
        for (index in endSegment - 1 downTo 0) {
            val segment = segments[index]
            if (selectedLineCount + segment.size > maxLines) break
            selected.addFirst(segment)
            selectedLineCount += segment.size
        }
        return selected.flatMap { it }
    }

    private fun splitLongWord(value: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val codePoints = mutableListOf<String>()
        var codePointIndex = 0
        while (codePointIndex < value.length) {
            val codePoint = value.codePointAt(codePointIndex)
            val nextIndex = codePointIndex + Character.charCount(codePoint)
            codePoints += value.substring(codePointIndex, nextIndex)
            codePointIndex = nextIndex
        }
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < codePoints.size) {
            var end = start + 1
            while (end <= codePoints.size && measure(codePoints.subList(start, end).joinToString("")) <= maxWidth) end += 1
            val chunkEnd = (end - 1).coerceAtLeast(start + 1)
            chunks += codePoints.subList(start, chunkEnd).joinToString("")
            start = chunkEnd
        }
        return chunks
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
