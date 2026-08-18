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
        val modernHdIndex = source.indexOf("requestPokeApiModernHdSprite(request)")
        val homeIndex = source.indexOf("requestPokeApiHighResolutionSprite(request)")
        val animatedFallbackIndex = source.indexOf("requestPokeApiAnimatedSprite(request)")
        val localIndex = source.indexOf("plan.fallbackCandidates.filter(::isModernLocalCandidate)")

        assertTrue(communityIndex >= 0)
        assertTrue(modernHdIndex > communityIndex)
        assertTrue(regularIndex > modernHdIndex)
        assertTrue(homeIndex > regularIndex)
        assertTrue(animatedFallbackIndex > regularIndex)
        assertTrue(localIndex >= 0)
        assertTrue(localIndex < animatedFallbackIndex)
        assertTrue(source.indexOf("ShowdownAssetPaths.hdAnimatedSpriteCandidates") < source.indexOf("ShowdownAssetPaths.pokeApiAnimatedSprite"))
    }
}
