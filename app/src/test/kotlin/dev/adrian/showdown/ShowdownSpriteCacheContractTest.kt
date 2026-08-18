package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownSpriteCacheContractTest {
    @Test
    fun modernArtworkResolutionKeepsAnimatedSourcesAheadOfStaticFallbacks() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val communityIndex = source.indexOf("requestSpriteCandidates(plan.communityRemoteCandidates)")
        val regularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", source.indexOf("fun requestRegularOrModernLocal()"))
        val modernHdIndex = source.indexOf("requestPokeApiModernHdSprite(request)")
        val homeIndex = source.indexOf("requestPokeApiHighResolutionSprite(request)")
        val animatedFallbackIndex = source.indexOf("requestPokeApiAnimatedSprite(request)")
        val localIndex = source.indexOf("requestSpriteCandidates(modernLocalCandidates)", source.indexOf("fun requestRegularOrModernLocal()"))

        assertTrue(communityIndex >= 0)
        assertTrue(modernHdIndex < communityIndex)
        assertTrue(localIndex > modernHdIndex)
        assertTrue(regularIndex < localIndex)
        assertTrue(animatedFallbackIndex > regularIndex)
        assertTrue(localIndex >= 0)
        assertTrue(homeIndex > animatedFallbackIndex)
        assertTrue(homeIndex > modernHdIndex)
        assertTrue(localIndex < homeIndex)
        assertTrue(source.indexOf("ShowdownAssetPaths.hdAnimatedSpriteCandidates") < source.indexOf("ShowdownAssetPaths.pokeApiAnimatedSprite"))
    }
}
