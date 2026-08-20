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
        assertTrue(source.contains("hasDistinctMovieFrames(it)"))
        assertTrue(source.contains("it.duration() > 0"))
        assertTrue(source.contains("val isAnimated get() = movie != null"))
    }

    @Test
    fun rejectsOneFrameGifArtwork() {
        assertFalse(hasMultipleGifFrames(testGif(1)))
        assertFalse(hasMultipleGifFrames(testGif(2, identicalFrames = true)))
        assertTrue(hasMultipleGifFrames(testGif(2)))
    }

    @Test
    fun highResolutionArtworkMustRemainAnimated() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(isHighResolutionSpritePath("https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/mabosstiff.gif"))
        assertTrue(isHighResolutionSpritePath("https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/azelf.png"))
        assertFalse(isHighResolutionSpritePath("https://www.pkparaiso.com/imagenes/xy/sprites/animados/azelf.gif"))
        assertTrue(isAnimatedSpritePath("https://www.pkparaiso.com/imagenes/xy/sprites/animados/azelf.gif"))
        assertTrue(isAnimatedSpritePath("sprites/xyani/azelf.gif"))
        assertTrue(isAnimatedSpritePath("https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/FRONT/Azelf.gif"))
        assertFalse(isAnimatedSpritePath("sprites/dex/azelf.png"))
        assertTrue(requiresAnimatedSprite("https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/mabosstiff.gif", false))
        assertFalse(requiresAnimatedSprite("sprites/dex/mabosstiff.png", false))
        assertTrue(source.contains("private fun requestAnimatedSpriteCandidates"))
        assertTrue(source.contains("requiresAnimatedSprite(path, animatedOnly)"))
        assertTrue(source.contains("isAnimatedSpritePath(path) && !path.endsWith(\".gif\", ignoreCase = true)"))
        assertTrue(source.contains("if (isHighResolutionSpritePath(path)) return null"))
    }

    @Test
    fun keepsBattleBackdropsVisibleWhileRemoteArtworkLoads() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("R.drawable.battle_background_fallback"))
        assertTrue(source.contains("mainHandler.post { receiver(fallback) }"))
        assertTrue(source.contains("?: fallbackBackdrop"))
    }

    @Test
    fun modernRequestsResolveHdArtworkBeforeShowdownFallbacks() {
        val cacheSource = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val requestSource = cacheSource.substringAfter("fun requestPokemon").substringBefore("fun requestDexSprite")
        val deckSource = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(isGenericSpritePlaceholder("sprites/ani/substitute.gif"))
        assertTrue(isGenericSpritePlaceholder("sprites/ani-back/decoy.gif"))
        assertFalse(isGenericSpritePlaceholder("sprites/xyani-back/kilowattrel.gif"))
        assertTrue(requestSource.contains("requestResolutionPlan("))
        assertFalse(requestSource.contains("requestPokeApiFallbackSprite(request)"))
        assertTrue(cacheSource.contains("requestPokeApiAnimatedSprite(request)"))
        assertFalse(cacheSource.contains("requestPokeApiHighResolutionSprite"))
        assertFalse(cacheSource.contains("pokeApiHighResolutionSprite"))
        assertFalse(cacheSource.contains("requestPokeApiStandardSprite"))
        assertFalse(deckSource.contains("requestPlaceholder"))
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
        val backCommunityIndex = source.indexOf("requestAnimatedSpriteCandidates(plan.communityRemoteCandidates)", backIndex)
        val frontIndex = source.indexOf("private fun requestFrontSpriteResolution")
        val frontHdIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = true)", frontIndex)
        val frontAnimatedIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = false)", frontIndex)
        val frontCommunityIndex = source.indexOf("requestAnimatedSpriteCandidates(plan.communityRemoteCandidates)", frontIndex)
        val frontRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", frontIndex)
        val localIndex = source.indexOf("requestAnimatedSpriteCandidates(modernLocalCandidates)")

        assertTrue(backRegularIndex >= 0)
        assertTrue(scrapedBackIndex >= 0)
        assertTrue(scrapedBackIndex < backRegularIndex)
        assertTrue(backRegularIndex > scrapedBackIndex)
        assertTrue(scrapedBackRegularIndex > backRegularIndex)
        assertTrue(backCommunityIndex > scrapedBackRegularIndex)
        assertTrue(frontHdIndex >= 0)
        assertTrue(frontAnimatedIndex > frontHdIndex)
        assertTrue(source.contains("ShowdownAssetPaths.highResolutionFrontSpriteIndexUrls(request.shiny)"))
        assertTrue(frontCommunityIndex >= 0)
        assertTrue(frontRegularIndex < frontAnimatedIndex)
        assertTrue(frontCommunityIndex > frontAnimatedIndex)
        assertTrue(animatedFallbackIndex > localIndex)
        assertTrue(localIndex >= 0)
        assertTrue(localIndex < animatedFallbackIndex)
        assertFalse(source.contains("requestPokeApiModernHdSprite"))
        assertFalse(source.contains("hdAnimatedSpriteCandidates"))
    }

    @Test
    fun keepsTrueAnimatedBackSpritesWithoutMirroredFrontFallbacks() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val backResolver = source.substringAfter("private fun requestBackSpriteResolution")
            .substringBefore("private fun requestScrapedBackSpriteResolution")
        val frontResolver = source.substringAfter("private fun requestFrontSpriteResolution")
            .substringBefore("private fun requestScrapedFrontSpriteResolution")

        assertTrue(
            backResolver.indexOf("requestModernLocalSpriteResolution(request, plan)") <
                backResolver.indexOf("receiver(null)")
        )
        assertTrue(
            frontResolver.indexOf("requestRegularRemoteSpriteResolution(plan)") <
                frontResolver.indexOf("requestScavioAnimatedSprite(request)")
        )
        assertFalse(backResolver.contains("requestScavioAnimatedSprite"))
        assertFalse(source.contains("asset.mirroredForPlayer()"))
    }

    @Test
    fun neverUsesAMirroredStaticFrontSpriteAsAPlayerBackSprite() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertFalse(
            allowsStaticShowdownFallback(
                BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
            )
        )
        assertTrue(
            allowsStaticShowdownFallback(
                BattleSpriteRequest.forOpponent("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
            )
        )
        assertTrue(source.contains("allowsStaticShowdownFallback(request)"))
        assertFalse(source.contains("withMirrorWhenDrawn"))
    }

    @Test
    fun staticShowdownArtworkIsOnlyTheFinalOpponentFacingFallback() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val modernLocalIndex = source.indexOf("requestAnimatedSpriteCandidates(modernLocalCandidates)")
        val animatedIndex = source.indexOf("requestSmallSpriteResolution(request)", modernLocalIndex)
        val staticIndex = source.indexOf("requestStaticShowdownFallback(request, receiver)", animatedIndex)

        assertTrue(modernLocalIndex >= 0)
        assertTrue(animatedIndex > modernLocalIndex)
        assertTrue(staticIndex > animatedIndex)
        assertTrue(source.contains("ShowdownAssetPaths.staticDexSpriteCandidates(request.species)"))
        assertTrue(source.contains(".filter { it.startsWith(\"sprites/dex/\") }"))
        assertTrue(source.contains(".filterNot(::isHighResolutionSpritePath)"))
        assertTrue(source.contains("allowsStaticShowdownFallback(request)"))
        assertTrue(source.contains("requestAnimatedSpriteCandidates"))
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

    @Test
    fun playerBackFallbackStopsBeforeStaticFrontArtwork() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val backResolver = source.substringAfter("private fun requestBackSpriteResolution")
            .substringBefore("private fun requestScrapedBackSpriteResolution")
        val localResolver = source.substringAfter("private fun requestModernLocalSpriteResolution")
            .substringBefore("private fun requestRegularRemoteSpriteResolution")
        val animatedResolution = source.indexOf("requestModernLocalSpriteResolution(request, plan)")
        val staticFallback = source.indexOf("ShowdownAssetPaths.staticDexSpriteCandidates(request.species)")

        assertTrue(backResolver.contains("requestRegularRemoteSpriteResolution(plan)"))
        assertTrue(animatedResolution >= 0)
        assertTrue(staticFallback > animatedResolution)
        assertTrue(localResolver.contains("else if (allowsStaticShowdownFallback(request))"))
        assertTrue(localResolver.contains("receiver(null)"))
    }

    private fun testGif(frameCount: Int, identicalFrames: Boolean = false): ByteArray {
        val header = "GIF89a".toByteArray(Charsets.US_ASCII)
        val screen = byteArrayOf(1, 0, 1, 0, 0, 0, 0)
        val frame = byteArrayOf(
            0x2c,
            0, 0, 0, 0, 1, 0, 1, 0, 0,
            0x02,
            0x02, 0x4c, 0x01, 0x00
        )
        val frames = (0 until frameCount).flatMap { frameIndex ->
            frame.mapIndexed { byteIndex, value ->
                if (!identicalFrames && byteIndex == 12) (0x4c + frameIndex).toByte() else value
            }
        }.toByteArray()
        return header + screen + frames + byteArrayOf(0x3b)
    }
}
