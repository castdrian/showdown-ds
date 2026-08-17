package dev.adrian.showdown

internal fun showdownCacheExtension(path: String): String {
    val filename = path.substringAfterLast('/').substringBefore('?')
    return filename.substringAfterLast('.', "")
        .takeIf { candidate ->
            candidate.length in 1..8 && candidate.all(Char::isLetterOrDigit)
        }
        ?: "bin"
}
