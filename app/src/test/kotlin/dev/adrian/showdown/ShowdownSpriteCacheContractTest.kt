package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownSpriteCacheContractTest {
    @Test
    fun modernArtworkResolutionKeepsSmallAnimatedFallbacksLast() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()
        val communityIndex = source.indexOf("requestSpriteCandidates(plan.communityRemoteCandidates)")
        val regularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)")
        val modernHdIndex = source.indexOf("requestPokeApiModernHdSprite(request)")
        val homeIndex = source.indexOf("requestPokeApiHighResolutionSprite(request)")
        val animatedFallbackIndex = source.indexOf("requestPokeApiAnimatedSprite(request)")
        val regularFallbackBodyIndex = source.indexOf("fun requestRegularOrModernFallback()")
        val localFallbackIndex = source.indexOf("requestModernLocalOrSmall()", regularFallbackBodyIndex)
        val opponentBranchIndex = source.indexOf("if (request.side == BattleSpriteSide.OPPONENT)")
        val homeCallIndex = source.indexOf("requestPokeApiHighResolutionSprite(request)", opponentBranchIndex)
        val regularFallbackCallIndex = source.indexOf("requestRegularOrModernFallback()", homeCallIndex)

        assertTrue(communityIndex >= 0)
        assertTrue(modernHdIndex < communityIndex)
        assertTrue(regularIndex > modernHdIndex)
        assertTrue(opponentBranchIndex >= 0)
        assertTrue(homeCallIndex < regularFallbackCallIndex)
        assertTrue(regularFallbackBodyIndex >= 0)
        assertTrue(localFallbackIndex > regularFallbackBodyIndex)
        assertTrue(animatedFallbackIndex > regularIndex)
        assertTrue(localFallbackIndex >= 0)
        assertTrue(homeIndex > modernHdIndex)
        assertTrue(source.indexOf("ShowdownAssetPaths.hdAnimatedSpriteCandidates") < source.indexOf("ShowdownAssetPaths.pokeApiAnimatedSprite"))
    }
}
