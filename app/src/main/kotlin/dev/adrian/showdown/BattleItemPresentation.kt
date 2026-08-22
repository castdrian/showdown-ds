package dev.adrian.showdown

object BattleItemPresentation {
    private val iconPathCache = mutableMapOf<String, String?>()

    fun visibleName(item: String): String? {
        val normalized = item.trim()
        return normalized.takeUnless {
            it.isBlank() ||
                it.equals("Unknown item", true) ||
                it.equals("No item", true) ||
                it.equals("None", true)
        }
    }

    fun iconPath(item: String): String? {
        val normalized = item.trim()
        if (normalized.isBlank() || normalized.equals("Unknown item", true) || normalized.equals("No item", true) || normalized.equals("None", true)) {
            return null
        }
        if (iconPathCache.containsKey(normalized)) return iconPathCache[normalized]
        val path = ShowdownAssetPaths.itemSprite(normalized)
        iconPathCache[normalized] = path
        return path
    }
}
