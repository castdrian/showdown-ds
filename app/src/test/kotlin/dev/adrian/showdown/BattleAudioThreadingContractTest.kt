package dev.adrian.showdown

import java.io.File
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
        assertTrue(source.contains("private fun loadBattleSounds()"))
        assertTrue(source.contains("audioCueHandler.post { selectMusic(requestedMusic) }"))
        assertTrue(source.contains("audioCueHandler.post { bgmPlayer?.pause() }"))
        assertTrue(source.contains("audioCueHandler.post(loopCheck)"))
        assertTrue(source.contains("audioCueHandler.post { playTransientSound(it.path, 0.60f) }"))
        assertTrue(source.contains("audioCueHandler.post { playTransientSound(file.path, volume) }"))
        assertTrue(source.contains("const val MAX_TRANSIENT_SOUND_SAMPLES = 24"))
        assertTrue(source.contains("pool.unload(soundId)"))
        assertFalse(source.contains("private val transientPlayers"))
        assertFalse(source.contains("private fun playPlayer("))
    }
}
