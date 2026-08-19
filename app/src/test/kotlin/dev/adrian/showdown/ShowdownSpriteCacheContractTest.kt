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
        assertTrue(source.contains("movie.setTime((elapsedMillis % movie.duration().toLong().coerceAtLeast(1L)).toInt())"))
        assertTrue(source.contains("hasMultipleGifFrames(file.readBytes())"))
        assertTrue(source.contains("it.duration() > 0"))
        assertTrue(source.contains("val isAnimated get() = movie != null"))
    }

    @Test
    fun rejectsOneFrameGifArtwork() {
        assertFalse(hasMultipleGifFrames(testGif(1)))
        assertTrue(hasMultipleGifFrames(testGif(2)))
    }

    @Test
    fun keepsBattleBackdropsVisibleWhileRemoteArtworkLoads() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("R.drawable.battle_background_fallback"))
        assertTrue(source.contains("mainHandler.post { receiver(fallback) }"))
        assertTrue(source.contains("?: fallbackBackdrop"))
    }

    @Test
    fun modernRequestsStartAnAnimatedFallbackWithoutSubstitute() {
        val cacheSource = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val requestSource = cacheSource.substringAfter("fun requestPokemon").substringBefore("fun requestDexSprite")
        val deckSource = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(isGenericSpritePlaceholder("sprites/ani/substitute.gif"))
        assertTrue(isGenericSpritePlaceholder("sprites/ani-back/decoy.gif"))
        assertFalse(isGenericSpritePlaceholder("sprites/xyani-back/kilowattrel.gif"))
        assertTrue(requestSource.contains("requestPokeApiFallbackSprite(request)"))
        assertTrue(cacheSource.contains("requestPokeApiAnimatedSprite(request, receiver)"))
        assertFalse(cacheSource.contains("requestPokeApiHighResolutionSprite"))
        assertFalse(cacheSource.contains("pokeApiHighResolutionSprite"))
        assertFalse(cacheSource.contains("requestPokeApiStandardSprite"))
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
    fun modernArtworkResolutionUsesAnimatedSourcesAtEveryTier() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val animatedFallbackIndex = source.indexOf(
            "requestPokeApiAnimatedSprite(request)",
            source.indexOf("private fun requestSmallSpriteResolution")
        )
        val backIndex = source.indexOf("private fun requestBackSpriteResolution")
        val scrapedBackIndex = source.indexOf("requestScrapedBackSpriteResolution(request, highResolutionOnly = true)", backIndex)
        val scrapedBackRegularIndex = source.indexOf("requestScrapedBackSpriteResolution(request, highResolutionOnly = false)", backIndex)
        val backRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", backIndex)
        val backCommunityIndex = source.indexOf("requestSpriteCandidates(plan.communityRemoteCandidates)", backIndex)
        val frontIndex = source.indexOf("private fun requestFrontSpriteResolution")
        val frontHdIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = true)", frontIndex)
        val frontAnimatedIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = false)", frontIndex)
        val frontCommunityIndex = source.indexOf("requestSpriteCandidates(plan.communityRemoteCandidates)", frontIndex)
        val frontRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", frontIndex)
        val localIndex = source.indexOf("requestSpriteCandidates(modernLocalCandidates)")

        assertTrue(backRegularIndex >= 0)
        assertTrue(scrapedBackIndex >= 0)
        assertTrue(scrapedBackIndex < backRegularIndex)
        assertTrue(scrapedBackRegularIndex > backCommunityIndex)
        assertTrue(backCommunityIndex > scrapedBackIndex)
        assertTrue(frontHdIndex >= 0)
        assertTrue(frontAnimatedIndex > frontHdIndex)
        assertTrue(source.contains("ShowdownAssetPaths.highResolutionFrontSpriteIndexUrls(request.shiny)"))
        assertTrue(frontCommunityIndex >= 0)
        assertTrue(frontCommunityIndex < frontRegularIndex)
        assertTrue(frontRegularIndex > frontAnimatedIndex)
        assertTrue(animatedFallbackIndex > localIndex)
        assertTrue(localIndex >= 0)
        assertTrue(localIndex < animatedFallbackIndex)
        assertFalse(source.contains("requestPokeApiModernHdSprite"))
        assertFalse(source.contains("hdAnimatedSpriteCandidates"))
    }

    @Test
    fun keepsTrueAnimatedBackSpritesAheadOfMirroredFrontFallbacks() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val backResolver = source.substringAfter("private fun requestBackSpriteResolution")
            .substringBefore("private fun requestScrapedBackSpriteResolution")
        val frontResolver = source.substringAfter("private fun requestFrontSpriteResolution")
            .substringBefore("private fun requestScrapedFrontSpriteResolution")

        assertTrue(
            backResolver.indexOf("requestModernLocalSpriteResolution(request, plan)") <
                backResolver.indexOf("requestScavioAnimatedSprite(request, receiver)")
        )
        assertTrue(
            frontResolver.indexOf("requestRegularRemoteSpriteResolution(plan)") <
                frontResolver.indexOf("requestScavioAnimatedSprite(request)")
        )
        assertTrue(source.contains("asset.mirroredForPlayer()"))
    }

    @Test
    fun backArtworkResolutionTraversesPaginatedSpriteIndexes() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val backResolver = source.substringAfter("private fun requestScrapedBackSpriteResolution")
            .substringBefore("private fun requestModernLocalSpriteResolution")

        assertTrue(backResolver.contains("ShowdownSpriteIndexGroups.pageUrls(html, indexUrl)"))
        assertTrue(backResolver.contains("ShowdownAssetPaths.highResolutionBackSpriteIndexUrls(request.shiny)"))
        assertTrue(backResolver.contains("fun requestPage(pageIndex: Int, pageFile: File?)"))
        assertTrue(backResolver.contains("requestBytes(pageUrls[nextPageIndex])"))
    }

    @Test
    fun formFallbacksUseThePokeApiResourceId() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("requestPokeApiSpriteCandidates(request, { resourceNumber ->"))
        assertTrue(source.contains("pokeApiAnimatedSprite(resourceNumber, request.side, request.shiny)"))
        assertTrue(source.contains("candidates(resourceNumber)"))
    }

    private fun testGif(frameCount: Int): ByteArray {
        val header = "GIF89a".toByteArray(Charsets.US_ASCII)
        val screen = byteArrayOf(1, 0, 1, 0, 0, 0, 0)
        val frame = byteArrayOf(
            0x2c,
            0, 0, 0, 0, 1, 0, 1, 0, 0,
            0x02,
            0x02, 0x4c, 0x01, 0x00
        )
        return header + screen + ByteArray(frame.size * frameCount) { index -> frame[index % frame.size] } + byteArrayOf(0x3b)
    }
}
