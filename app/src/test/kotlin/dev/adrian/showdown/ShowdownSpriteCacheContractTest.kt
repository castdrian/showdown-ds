package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownSpriteCacheContractTest {
    @Test
    fun modernArtworkResolutionKeepsSmallAnimatedFallbacksLast() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val communityIndex = source.indexOf("requestSpriteCandidates(plan.communityRemoteCandidates)")
        val regularIndex = source.indexOf("requestSpriteCandidates(plan.regularRemoteCandidates)")
        val modernFallbackIndex = source.indexOf("requestPokeApiModernSprite(request)")

        assertTrue(communityIndex >= 0)
        assertTrue(regularIndex > communityIndex)
        assertTrue(modernFallbackIndex > regularIndex)
    }
}
