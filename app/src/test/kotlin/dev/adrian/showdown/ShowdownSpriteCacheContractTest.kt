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
        assertTrue(source.contains("private fun animatedFrameAt(elapsedMillis: Long)"))
        assertTrue(source.contains("source.setTime(frameTime.toInt())"))
        assertTrue(source.contains("frame.eraseColor(0)"))
        assertFalse(source.contains("hasMultipleGifFrames(file.readBytes())"))
        assertTrue(source.contains("hasAnimatedGifFrameBudget(file, MAX_ANIMATED_FRAME_COUNT)"))
        assertTrue(source.contains("hasDistinctMovieFrames(it)"))
        assertTrue(source.contains("it.duration() > 0"))
        assertTrue(source.contains("val isAnimated get() = movie != null || streamingGif?.isAnimated == true"))
        assertTrue(source.contains("private fun decodeMovie(file: File): SpriteAsset?"))
        assertTrue(source.contains("private fun decodeStreamedHdGif(file: File, canvasSize: Pair<Int, Int>): SpriteAsset?"))
        assertTrue(source.contains("if (fitsMovieBudget)"))
        assertFalse(source.contains("ImageDecoder"))
        assertTrue(source.contains("private fun decodeMovie(file: File): SpriteAsset?"))
    }

    @Test
    fun normalAnimatedSourcesPreferTheBoundedStreamingDecoder() {
        val cacheSource = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val gifSource = File("src/main/kotlin/dev/adrian/showdown/ShowdownStreamingGif.kt").readText()

        assertTrue(cacheSource.contains("return decodeStreamedGif(file) ?: decodeMovie(file)"))
        assertTrue(cacheSource.contains("private fun decodeStreamedGif(file: File): SpriteAsset?"))
        assertTrue(cacheSource.contains("if (memoryConstrained && plan.usesModernAnimatedFallback)"))
        assertTrue(cacheSource.contains("private fun requestConstrainedSpriteResolution("))
        assertTrue(cacheSource.contains("requestAnimatedSpriteCandidates(plan.preferredRemoteCandidates) { hdAsset ->"))
        val constrainedIndex = cacheSource.indexOf("private fun requestConstrainedSpriteResolution")
        val constrainedRegularIndex = cacheSource.indexOf(
            "requestAnimatedSpriteCandidates(plan.regularRemoteCandidates)",
            constrainedIndex
        )
        val constrainedCommunityIndex = cacheSource.indexOf(
            "requestAnimatedSpriteCandidates(plan.communityRemoteCandidates)",
            constrainedIndex
        )
        val constrainedLocalIndex = cacheSource.indexOf(
            "requestModernLocalSpriteResolution(request, plan)",
            constrainedIndex
        )
        assertTrue(constrainedIndex >= 0)
        assertTrue(constrainedRegularIndex > constrainedIndex)
        assertTrue(constrainedCommunityIndex > constrainedRegularIndex)
        assertTrue(constrainedLocalIndex > constrainedCommunityIndex)
        assertTrue(cacheSource.contains("if (asset != null || !request.backFacing)"))
        assertTrue(cacheSource.contains("requestStaticShowdownBackFallback(request, receiver)"))
        assertTrue(cacheSource.contains("totalMem < 2L * 1024L * 1024L * 1024L"))
        assertTrue(gifSource.contains("val canvasPixels = IntArray(outputWidth * outputHeight)"))
        assertFalse(gifSource.contains("val sourceBitmap"))
    }

    @Test
    fun constrainedPlayerBackSpritesKeepTheIndexedHdResolutionOrder() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val constrainedResolver = source.substringAfter("private fun requestConstrainedSpriteResolution")
            .substringBefore("private fun requestBackSpriteResolution")

        assertTrue(constrainedResolver.contains("if (request.backFacing)"))
        assertTrue(constrainedResolver.contains("requestBackSpriteResolution(request, plan, receiver)"))
        assertTrue(
            constrainedResolver.indexOf("requestBackSpriteResolution(request, plan, receiver)") <
                constrainedResolver.indexOf("requestAnimatedSpriteCandidates(plan.preferredRemoteCandidates)")
        )
    }

    @Test
    fun rejectsOneFrameGifArtwork() {
        assertFalse(hasMultipleGifFrames(testGif(1)))
        assertFalse(hasMultipleGifFrames(testGif(2, identicalFrames = true)))
        assertTrue(hasMultipleGifFrames(testGif(2)))
    }

    @Test
    fun validatesGifFramesFromDiskWithoutReadingTheWholeFileIntoMemory() {
        val file = File.createTempFile("showdown-sprite", ".gif")
        try {
            file.writeBytes(testGif(2))
            assertTrue(hasMultipleGifFrames(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsAnimatedSourcesWithTooManyFrames() {
        val file = File.createTempFile("showdown-sprite", ".gif")
        try {
            file.writeBytes(testGif(3))
            assertTrue(hasAnimatedGifFrameBudget(file, 3))
            assertFalse(hasAnimatedGifFrameBudget(file, 2))
        } finally {
            file.delete()
        }
    }

    @Test
    fun boundsAnimatedFramesWithoutChangingTheirAspectRatio() {
        assertEquals(512 to 256, boundedAnimatedFrameSize(1024, 512, 512))
        assertEquals(256 to 512, boundedAnimatedFrameSize(512, 1024, 512))
        assertEquals(320 to 240, boundedAnimatedFrameSize(320, 240, 512))
        assertEquals(1 to 1, boundedAnimatedFrameSize(0, 240, 512))
    }

    @Test
    fun rejectsAnimatedSourcesThatWouldCreateOversizedNativeMovies() {
        assertEquals(1024 to 512, gifCanvasSize(gifWithCanvas(1024, 512)))
        assertTrue(animatedGifFitsDecodeBudget(576, 576, 576, 576L * 576L))
        assertFalse(animatedGifFitsDecodeBudget(577, 512, 576, 576L * 576L))
        assertFalse(animatedGifFitsDecodeBudget(576, 577, 576, 576L * 576L))
        assertFalse(animatedGifFitsDecodeBudget(576, 576, 576, 576L * 576L - 1L))
    }

    @Test
    fun rejectsAnimatedSourcesOutsideTheSingleFrameBudget() {
        assertFalse(animatedGifFitsDecodeBudget(791, 831, 576, 576L * 576L))
        assertTrue(animatedGifFitsDecodeBudget(576, 576, 576, 576L * 576L))
    }

    @Test
    fun highResolutionArtworkMustRemainAnimated() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(isHighResolutionSpritePath("https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/mabosstiff.gif"))
        assertTrue(isHighResolutionSpritePath("https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/azelf.png"))
        assertTrue(isHighResolutionSpritePath("https://www.pkparaiso.com/imagenes/xy/sprites/animados/azelf.gif"))
        assertTrue(isHighResolutionSpritePath("https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados-espalda/azelf.gif"))
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
    fun dexFallbackCanResolveShinyTeamArtwork() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("fun requestDexSprite(species: String, shiny: Boolean"))
        assertTrue(source.contains("ShowdownAssetPaths.dexSpriteResolutionPlan(species, shiny)"))
        assertTrue(source.contains("fun requestStaticDexSprite(species: String"))
        assertTrue(source.contains("fun requestStaticDexSprite(species: String, shiny: Boolean"))
        assertTrue(source.contains("ShowdownAssetPaths.staticDexSpriteCandidates(species, shiny)"))
    }

    @Test
    fun staticTeamArtworkUsesPokeApiBeforeShowdownFallback() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val staticResolver = source.substringAfter("fun requestStaticDexSprite(species: String, shiny: Boolean")
            .substringBefore("fun requestTrainer")

        assertTrue(staticResolver.contains("requestPokeApiStaticSprite(species, shiny)"))
        assertTrue(source.contains("private fun requestPokeApiStaticSprite("))
        assertTrue(source.contains("requestPokeApiSpriteCandidates(species, animatedOnly = false"))
        assertTrue(source.contains("if (shiny) {"))
        assertTrue(source.contains("pokemon/shiny/${'$'}resourceNumber.png"))
        assertTrue(source.contains("pokemon/${'$'}resourceNumber.png"))
        assertTrue(staticResolver.indexOf("requestPokeApiStaticSprite") < staticResolver.indexOf("ShowdownAssetPaths.staticDexSpriteCandidates"))
    }

    @Test
    fun memoizesResolvedPokemonAssetsAcrossDisplayRefreshes() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val requestSource = source.substringAfter("fun requestPokemon").substringBefore("fun requestDexSprite")

        assertTrue(source.contains("private val resolvedPokemonCache = LruCache<BattleSpriteRequest, WeakReference<SpriteAsset>>(16)"))
        assertTrue(requestSource.contains("resolvedPokemonCache.get(request)"))
        assertTrue(requestSource.contains("resolvedPokemonCache.put(request, WeakReference(asset))"))
        assertTrue(source.contains("resolvedPokemonCache.evictAll()"))
    }

    @Test
    fun boundsDecodedSpriteMemoryByPixelCost() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("object : LruCache<String, SpriteAsset>(SPRITE_MEMORY_CACHE_BYTES)"))
        assertTrue(source.contains("override fun sizeOf(key: String, value: SpriteAsset) = value.estimatedMemoryBytes()"))
        assertTrue(source.contains("val pixels = width.toLong() * height.toLong() * 4L"))
        assertTrue(source.contains("const val SPRITE_MEMORY_CACHE_BYTES = 12 * 1024 * 1024"))
        assertTrue(source.contains("fun clearMemory()"))
        assertTrue(source.contains("fun stopAnimation()"))
        assertTrue(source.contains("override fun entryRemoved("))
    }

    @Test
    fun resourceDownloadsStreamToDiskWithinTheFileBudget() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("private fun downloadTo(file: File, path: String): Boolean"))
        assertTrue(source.contains("FileOutputStream(temporary)"))
        assertTrue(source.contains("if (total > MAX_FILE_BYTES)"))
        assertTrue(source.contains("const val MAX_FILE_BYTES = 16 * 1024 * 1024"))
        assertFalse(source.contains("ByteArrayOutputStream"))
    }

    @Test
    fun usesManuallyPacedAnimationDecoding() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertTrue(source.contains("private fun animatedFrameAt(elapsedMillis: Long)"))
        assertTrue(source.contains("private const val ANIMATED_FRAME_INTERVAL_MILLIS = 48L"))
        assertTrue(source.contains("const val MAX_ANIMATED_FRAME_DIMENSION = 512"))
        assertTrue(source.contains("const val MAX_ANIMATED_SOURCE_DIMENSION = 576"))
        assertTrue(source.contains("const val MAX_ANIMATED_SOURCE_PIXELS = 576L * 576L"))
        assertTrue(source.contains("const val MAX_ANIMATED_FILE_BYTES = 6L * 1024L * 1024L"))
        assertTrue(source.contains("const val MAX_ANIMATED_FRAME_COUNT = 24"))
        assertTrue(source.contains("const val MAX_STREAMED_HD_SOURCE_DIMENSION = 1024"))
        assertTrue(source.contains("const val MAX_STREAMED_HD_SOURCE_PIXELS = 1024L * 1024L"))
        assertTrue(source.contains("const val MAX_STREAMED_HD_FILE_BYTES = 8L * 1024L * 1024L"))
        assertTrue(source.contains("const val MAX_STREAMED_HD_FRAME_COUNT = 96"))
        assertTrue(source.contains("val sourcePixels = if (movie != null)"))
        assertTrue(source.contains("MOVIE_MEMORY_MULTIPLIER"))
        assertTrue(source.contains("boundedAnimatedFrameSize"))
        assertTrue(source.contains("private fun decodeMovie(file: File): SpriteAsset?"))
        assertTrue(source.contains("ShowdownStreamingGif.fromFile("))
        assertTrue(source.contains("streamingGif?.frameAt"))
        assertTrue(source.contains("Executors.newFixedThreadPool(2)"))
        assertTrue(source.contains("Executors.newSingleThreadExecutor()"))
    }

    @Test
    fun modernArtworkResolutionUsesAnimatedSourcesAtEveryTier() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val animatedFallbackIndex = source.indexOf(
            "requestPokeApiAnimatedSprite(request)",
            source.indexOf("private fun requestSmallSpriteResolution")
        )
        val backIndex = source.indexOf("private fun requestBackSpriteResolution")
        val backPreferredIndex = source.indexOf("requestAnimatedSpriteCandidates(plan.preferredRemoteCandidates, callback)", backIndex)
        val scrapedBackIndex = source.indexOf("requestScrapedBackSpriteResolution(request, highResolutionOnly = true, receiver = callback)", backIndex)
        val scrapedBackRegularIndex = source.indexOf("requestScrapedBackSpriteResolution(request, highResolutionOnly = false, receiver = callback)", backIndex)
        val backRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan, callback)", backIndex)
        val backCommunityIndex = source.indexOf("requestAnimatedSpriteCandidates(plan.communityRemoteCandidates, callback)", backIndex)
        val backLocalIndex = source.indexOf("requestModernLocalSpriteResolution(request, plan, callback)", backIndex)
        val frontIndex = source.indexOf("private fun requestFrontSpriteResolution")
        val frontHdIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = true)", frontIndex)
        val scavioIndex = source.indexOf("requestScavioAnimatedSprite(request)", frontIndex)
        val frontAnimatedIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = false)", frontIndex)
        val frontCommunityIndex = source.indexOf("requestAnimatedSpriteCandidates(plan.communityRemoteCandidates)", frontIndex)
        val frontRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", frontIndex)
        val localIndex = source.indexOf("requestAnimatedSpriteCandidates(modernLocalCandidates)")

        assertTrue(backPreferredIndex >= 0)
        assertTrue(scrapedBackIndex >= 0)
        assertTrue(backCommunityIndex >= 0)
        assertTrue(backRegularIndex >= 0)
        assertTrue(scrapedBackRegularIndex >= 0)
        assertTrue(backLocalIndex >= 0)
        assertTrue(backPreferredIndex < scrapedBackIndex)
        assertTrue(scrapedBackIndex < backRegularIndex)
        assertTrue(backRegularIndex < scrapedBackRegularIndex)
        assertTrue(scrapedBackRegularIndex < backCommunityIndex)
        assertTrue(scrapedBackRegularIndex < backLocalIndex)
        assertTrue(backCommunityIndex < backLocalIndex)
        assertTrue(frontHdIndex >= 0)
        assertTrue(frontHdIndex < scavioIndex)
        assertTrue(scavioIndex < frontRegularIndex)
        assertTrue(frontAnimatedIndex > frontHdIndex)
        assertTrue(frontAnimatedIndex > scavioIndex)
        assertTrue(source.contains("ShowdownAssetPaths.highResolutionFrontSpriteIndexUrls(request.shiny)"))
        assertTrue(source.contains("if (request.shiny)"))
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

        assertTrue(backResolver.contains("requestModernLocalSpriteResolution(request, plan, callback)"))
        assertFalse(backResolver.contains("requestModernLocalSpriteResolution(request, plan, ::deliverAnimated)"))
        assertTrue(
            frontResolver.indexOf("requestScavioAnimatedSprite(request)") <
                frontResolver.indexOf("requestRegularRemoteSpriteResolution(plan)")
        )
        assertFalse(backResolver.contains("requestScavioAnimatedSprite"))
        assertTrue(backResolver.contains("requestStaticShowdownBackFallback(request, receiver)"))
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
        assertTrue(source.contains("ShowdownAssetPaths.staticDexSpriteCandidates(request.species, request.shiny)"))
        assertTrue(source.contains("it.startsWith(\"sprites/dex/\") || it.startsWith(\"sprites/dex-shiny/\")"))
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

        assertTrue(source.contains("requestPokeApiSpriteCandidates(request.species, animatedOnly = true"))
        assertTrue(source.contains("pokeApiAnimatedSprite(resourceNumber, request.side, request.shiny)"))
        assertTrue(source.contains("candidates(resourceNumber)"))
        assertTrue(source.contains("requestSpriteCandidates(candidates(resourceNumber), animatedOnly)"))
    }

    @Test
    fun playerBackFallbackStopsBeforeStaticFrontArtwork() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val backResolver = source.substringAfter("private fun requestBackSpriteResolution")
            .substringBefore("private fun requestScrapedBackSpriteResolution")
        val localResolver = source.substringAfter("private fun requestModernLocalSpriteResolution")
            .substringBefore("private fun requestRegularRemoteSpriteResolution")
        val animatedResolution = source.indexOf("requestModernLocalSpriteResolution(request, plan, callback)")
        val staticFallback = source.indexOf("ShowdownAssetPaths.staticDexSpriteCandidates(request.species, request.shiny)")

        assertTrue(backResolver.contains("requestRegularRemoteSpriteResolution(plan, callback)"))
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

    private fun gifWithCanvas(width: Int, height: Int): ByteArray {
        val header = "GIF89a".toByteArray(Charsets.US_ASCII)
        val screen = byteArrayOf(
            (width and 0xff).toByte(),
            ((width shr 8) and 0xff).toByte(),
            (height and 0xff).toByte(),
            ((height shr 8) and 0xff).toByte(),
            0,
            0,
            0
        )
        return header + screen
    }
}
