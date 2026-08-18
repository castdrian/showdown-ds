package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownSpriteCacheContractTest {
    @Test
    fun modernArtworkResolutionKeepsAnimatedSourcesAheadOfStaticFallbacks() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val modernHdIndex = source.indexOf("requestPokeApiModernHdSprite(request)")
        val animatedFallbackIndex = source.indexOf("requestPokeApiAnimatedSprite(request)")
        val frontVerifiedIndex = source.indexOf("requestSpriteCandidates(plan.verifiedRemoteCandidates)", animatedFallbackIndex)
        val backIndex = source.indexOf("private fun requestBackSpriteResolution")
        val scrapedBackIndex = source.indexOf("requestScrapedBackSpriteResolution(request)", backIndex)
        val backRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", backIndex)
        val verifiedBackIndex = source.indexOf("private fun requestVerifiedBackThenCommunitySpriteResolution")
        val verifiedBackCandidatesIndex = source.indexOf("requestSpriteCandidates(plan.verifiedRemoteCandidates)", verifiedBackIndex)
        val backCommunityIndex = source.indexOf("requestCommunityThenModernLocalSpriteResolution(request, plan, receiver)", verifiedBackIndex)
        val frontIndex = source.indexOf("private fun requestFrontSpriteResolution")
        val frontCommunityIndex = source.indexOf("requestSpriteCandidates(plan.communityRemoteCandidates)", frontIndex)
        val homeIndex = source.indexOf("requestPokeApiHighResolutionSprite(request)", frontIndex)
        val frontRegularIndex = source.indexOf("requestRegularOrModernLocalSpriteResolution(request, plan, receiver)", frontIndex)
        val localIndex = source.indexOf("requestSpriteCandidates(modernLocalCandidates)")

        assertTrue(backRegularIndex >= 0)
        assertTrue(scrapedBackIndex >= 0)
        assertTrue(scrapedBackIndex < backRegularIndex)
        assertTrue(verifiedBackIndex > backRegularIndex)
        assertTrue(verifiedBackCandidatesIndex >= 0)
        assertTrue(backCommunityIndex >= 0)
        assertTrue(verifiedBackCandidatesIndex < backCommunityIndex)
        assertTrue(backRegularIndex < backCommunityIndex)
        assertTrue(frontCommunityIndex >= 0)
        assertTrue(homeIndex > frontCommunityIndex)
        assertTrue(frontRegularIndex > homeIndex)
        assertTrue(modernHdIndex < backRegularIndex)
        assertTrue(frontRegularIndex < localIndex)
        assertTrue(verifiedBackCandidatesIndex < animatedFallbackIndex)
        assertTrue(animatedFallbackIndex > localIndex)
        assertTrue(localIndex >= 0)
        assertTrue(frontVerifiedIndex > homeIndex)
        assertTrue(homeIndex > modernHdIndex)
        assertTrue(localIndex < animatedFallbackIndex)
        assertTrue(source.indexOf("ShowdownAssetPaths.hdAnimatedSpriteCandidates") < source.indexOf("ShowdownAssetPaths.pokeApiAnimatedSprite"))
    }
}
