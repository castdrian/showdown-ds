package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleAudioThreadingContractTest {
    @Test
    fun shortSoundsUseTheAudioWorkerInsteadOfCreatingUiThreadPlayers() {
        val source = File("src/main/kotlin/dev/adrian/showdown/BattleAudio.kt").readText()

        assertTrue(source.contains("private var transientSoundPool: SoundPool? = null"))
        assertTrue(source.contains("private fun initializeTransientSoundPool()"))
        assertTrue(source.contains("transientSoundPool = createSoundPool(8)"))
        assertTrue(source.contains("audioCueHandler.post(::loadBattleSounds)"))
        assertTrue(source.contains("fun setPlaybackSpeed(speed: Float)"))
        assertTrue(source.contains("battleSoundPool.play(soundId, 0.72f, 0.72f, 1, 0, battlePlaybackSpeed)"))
        assertTrue(source.contains("private fun loadBattleSounds()"))
        assertTrue(source.contains("audioCueHandler.post { selectMusic(requestedMusic) }"))
        assertTrue(source.contains("bgmPlayer?.pause()"))
        assertTrue(source.contains("bgmPlayer?.let(::scheduleMusicLoop)"))
        assertTrue(source.contains("audioCueHandler.post { playTransientSound(it.path, 0.60f) }"))
        assertTrue(source.contains("audioCueHandler.post { playTransientSound(file.path, volume) }"))
        assertTrue(source.contains("player.setOnErrorListener"))
        assertTrue(source.contains("private fun releaseMusicPlayer(player: MediaPlayer)"))
        assertTrue(source.contains("bgmFile = null"))
        assertTrue(source.contains("const val MAX_TRANSIENT_SOUND_SAMPLES = 24"))
        assertTrue(source.contains("pool.unload(soundId)"))
        assertFalse(source.contains("private val transientPlayers"))
        assertFalse(source.contains("private fun playPlayer("))
    }

    @Test
    fun musicLoopUsesOneScheduledBoundaryInsteadOfPollingThePlayer() {
        val source = File("src/main/kotlin/dev/adrian/showdown/BattleAudio.kt").readText()

        assertTrue(source.contains("private val loopBoundaryRunnable = object : Runnable"))
        assertTrue(source.contains("player.currentPosition < selectedMusic.loopEnd - MUSIC_LOOP_GUARD_MILLIS"))
        assertTrue(source.contains("audioCueHandler.postDelayed(loopBoundaryRunnable, remainingMillis)"))
        assertFalse(source.contains("audioCueHandler.postDelayed(this, 500)"))
    }

    @Test
    fun musicLoopDelayReachesTheBoundaryWithoutAnArtificialResumeGap() {
        assertEquals(4_750L, battleMusicLoopDelayMillis(95_000, 100_000, 750L, 500L))
        assertEquals(0L, battleMusicLoopDelayMillis(99_500, 100_000, 750L, 500L))
        assertEquals(0L, battleMusicLoopDelayMillis(100_500, 100_000, 750L, 500L))
    }
}
