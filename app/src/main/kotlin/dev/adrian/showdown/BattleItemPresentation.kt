package dev.adrian.showdown

object BattleItemPresentation {
    fun visibleName(item: String): String? {
        val normalized = item.trim()
        return normalized.takeUnless {
            it.isBlank() ||
                it.equals("Unknown item", true) ||
                it.equals("No item", true) ||
                it.equals("None", true)
        }
    }

    fun iconPath(item: String): String? = visibleName(item)?.let(ShowdownAssetPaths::itemSprite)
}
