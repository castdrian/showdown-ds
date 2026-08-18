package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownSpriteCacheContractTest {
    @Test
    fun decodesGifSpritesWithExplicitFrameTiming() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("Movie.decodeFile(file.path)"))
        assertTrue(source.contains("movie.setTime((elapsedMillis % maxOf(movie.duration(), 1000)).toInt())"))
        assertTrue(source.contains("val isAnimated get() = movie != null"))
    }

    @Test
    fun keepsBattleBackdropsVisibleWhileRemoteArtworkLoads() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("R.drawable.battle_background_fallback"))
        assertTrue(source.contains("mainHandler.post { receiver(fallback) }"))
        assertTrue(source.contains("?: fallbackBackdrop"))
    }

    @Test
    fun modernRequestsStartARealAnimatedFallbackWithoutSubstitute() {
        val cacheSource = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val requestSource = cacheSource.substringAfter("fun requestPokemon").substringBefore("fun requestDexSprite")
        val deckSource = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(requestSource.contains("requestPokeApiAnimatedSprite(request)"))
        assertFalse(deckSource.contains("requestPlaceholder"))
    }

    @Test
    fun hdResolutionCanReplaceFallbackButLateFallbackCannotDowngradeIt() {
        val delivered = mutableListOf<String>()
        val gate = ProgressiveAssetDelivery<String>()

        gate.deliverFallback("regular") { delivered.add(it) }
        gate.deliverResolution("hd") { delivered.add(it ?: "") }
        gate.deliverFallback("late") { delivered.add(it) }

        assertEquals(listOf("regular", "hd"), delivered)
    }

    @Test
    fun modernArtworkResolutionKeepsAnimatedSourcesAheadOfStaticFallbacks() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val modernHdIndex = source.indexOf("requestPokeApiModernHdSprite(request)")
        val animatedFallbackIndex = source.indexOf(
            "requestPokeApiAnimatedSprite(request)",
            source.indexOf("private fun requestSmallSpriteResolution")
        )
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
        val frontRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", frontIndex)
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
        assertTrue(frontRegularIndex > frontCommunityIndex)
        assertTrue(homeIndex > frontRegularIndex)
        assertTrue(modernHdIndex < backRegularIndex)
        assertTrue(homeIndex < localIndex)
        assertTrue(frontRegularIndex < homeIndex)
        assertTrue(verifiedBackCandidatesIndex < animatedFallbackIndex)
        assertTrue(animatedFallbackIndex > localIndex)
        assertTrue(localIndex >= 0)
        assertTrue(frontVerifiedIndex >= 0)
        assertTrue(homeIndex > modernHdIndex)
        assertTrue(localIndex < animatedFallbackIndex)
        assertTrue(source.indexOf("ShowdownAssetPaths.hdAnimatedSpriteCandidates") < source.indexOf("ShowdownAssetPaths.pokeApiAnimatedSprite"))
    }
}
