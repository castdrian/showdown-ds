package dev.adrian.showdown

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class BattleAudio(
    private val context: Context,
    private val resourceCache: ShowdownSpriteCache,
    session: BattleSession
) {
    private data class PendingBattleCue(val cue: BattleAudioCue, val queuedAtMillis: Long)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioCueThread = HandlerThread("showdown-audio").also { it.start() }
    private val audioCueHandler = Handler(audioCueThread.looper)
    private val battleSoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val battleSoundIds = mutableMapOf<BattleAudioCue, Int>()
    private val loadedBattleSoundIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val pendingBattleCues = ArrayDeque<PendingBattleCue>()
    private val cuePlaybackQueue = BattleAudioCuePlaybackQueue()
    private var cuePlaybackGeneration = 0L
    private var notificationFile: File? = null
    private var bgmFile: File? = null
    private var bgmPlayer: MediaPlayer? = null
    private var bgmPrepared = false
    private val soundEffectsEnabled = AtomicBoolean(true)
    private var musicEnabled = false
    private val released = AtomicBoolean(false)
    private val transientPlayers = mutableSetOf<MediaPlayer>()
    private val previewRunnables = mutableSetOf<Runnable>()
    private var selectedMusic = MUSIC[session.showdownMusicIndex()]
    private val loopCheck = object : Runnable {
        override fun run() {
            val player = bgmPlayer ?: return
            if (musicEnabled && player.isPlaying) {
                if (player.currentPosition >= selectedMusic.loopEnd - 750) player.seekTo(selectedMusic.loopStart)
                mainHandler.postDelayed(this, 500)
            }
        }
    }

    init {
        battleSoundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                audioCueHandler.post {
                    loadedBattleSoundIds += sampleId
                    flushPendingBattleCues()
                }
            }
        }
        BattleAudioCue.values().forEach { cue ->
            runCatching {
                context.assets.openFd("move-sfx/${cue.assetName}.mp3").use { asset ->
                    battleSoundIds[cue] = battleSoundPool.load(asset.fileDescriptor, asset.startOffset, asset.length, 1)
                }
            }
        }
        resourceCache.requestAudio("audio/notification.wav") { notificationFile = it }
        requestMusic(selectedMusic)
    }

    fun updateOptions(session: BattleSession) {
        val requestedMusic = MUSIC[session.showdownMusicIndex()]
        if (requestedMusic != selectedMusic) selectMusic(requestedMusic)
        val effectsEnabled = session.soundEffectsEnabled
        soundEffectsEnabled.set(effectsEnabled)
        if (!effectsEnabled) audioCueHandler.post(::clearPendingBattleCues)
        musicEnabled = session.musicEnabled
        if (musicEnabled) {
            startMusicIfReady()
            if (bgmPrepared && bgmPlayer?.isPlaying == false) {
                bgmPlayer?.start()
                mainHandler.post(loopCheck)
            }
        } else {
            bgmPlayer?.pause()
        }
    }

    fun pauseMusic() {
        bgmPlayer?.pause()
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(loopCheck)
        previewRunnables.toList().forEach(mainHandler::removeCallbacks)
        previewRunnables.clear()
        bgmPlayer?.release()
        bgmPlayer = null
        audioCueHandler.removeCallbacksAndMessages(null)
        audioCueHandler.post {
            pendingBattleCues.clear()
            battleSoundPool.release()
            audioCueThread.quitSafely()
        }
        transientPlayers.toList().forEach(::finishPlayer)
    }

    fun playNavigation() = playNotification(0.35f)

    fun playConfirm() = playNotification(0.55f)

    fun playCancel() = playNotification(0.25f)

    fun playCuePreview(onComplete: () -> Unit) {
        previewRunnables.toList().forEach(mainHandler::removeCallbacks)
        previewRunnables.clear()
        if (!soundEffectsEnabled.get()) {
            onComplete()
            return
        }
        resetBattleCues()
        val cues = BattleAudioCue.values().toList()
        BattleAudioPreviewTiming.startOffsets(cues).forEachIndexed { index, delayMillis ->
            postPreview(delayMillis) { playBattleCue(cues[index]) }
        }
        postPreview(BattleAudioPreviewTiming.completionDelay(cues), onComplete)
    }

    fun playBattleCue(cue: BattleAudioCue) {
        audioCueHandler.post {
            if (released.get()) return@post
            if (!soundEffectsEnabled.get()) {
                clearPendingBattleCues()
                return@post
            }
            if (pendingBattleCues.size < MAX_PENDING_BATTLE_CUES) {
                pendingBattleCues.addLast(PendingBattleCue(cue, SystemClock.elapsedRealtime()))
            }
            flushPendingBattleCues()
        }
    }

    fun resetBattleCues() {
        audioCueHandler.postAtFrontOfQueue {
            if (!released.get()) clearPendingBattleCues()
        }
    }

    private fun flushPendingBattleCues() {
        if (released.get() || !soundEffectsEnabled.get()) {
            clearPendingBattleCues()
            return
        }
        val nowMillis = SystemClock.elapsedRealtime()
        while (pendingBattleCues.isNotEmpty()) {
            val pending = pendingBattleCues.first()
            if (nowMillis - pending.queuedAtMillis > MAX_PENDING_BATTLE_CUE_AGE_MILLIS) {
                pendingBattleCues.removeFirst()
                continue
            }
            val soundId = battleSoundIds[pending.cue]
            if (soundId == null || soundId !in loadedBattleSoundIds) return
            pendingBattleCues.removeFirst()
            val playback = cuePlaybackQueue.enqueue(pending.cue, nowMillis)
            val generation = cuePlaybackGeneration
            audioCueHandler.postDelayed({
                if (generation != cuePlaybackGeneration || released.get() || !soundEffectsEnabled.get()) return@postDelayed
                playBattleCueNow(soundId)
            }, playback.delayMillis)
        }
    }

    private fun clearPendingBattleCues() {
        pendingBattleCues.clear()
        cuePlaybackGeneration += 1
        cuePlaybackQueue.reset(SystemClock.elapsedRealtime())
    }

    private fun playBattleCueNow(soundId: Int) {
        if (released.get() || !soundEffectsEnabled.get()) return
        battleSoundPool.play(soundId, 0.72f, 0.72f, 1, 0, 1f)
    }

    fun playCry(species: String) {
        if (!soundEffectsEnabled.get()) return
        resourceCache.requestAudio("audio/cries/${resourceId(species)}.mp3") { file ->
            file?.let { playFile(it, 0.60f) }
        }
    }

    private fun startMusicIfReady() {
        if (!musicEnabled || bgmPlayer != null) return
        val file = bgmFile ?: return
        bgmPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(file.path)
            setVolume(0.32f, 0.32f)
            setOnPreparedListener {
                bgmPrepared = true
                if (!musicEnabled) return@setOnPreparedListener
                seekTo(selectedMusic.loopStart)
                start()
                mainHandler.post(loopCheck)
            }
            setOnCompletionListener {
                if (musicEnabled) {
                    seekTo(selectedMusic.loopStart)
                    start()
                }
            }
            prepareAsync()
        }
    }

    private fun selectMusic(music: Music) {
        mainHandler.removeCallbacks(loopCheck)
        bgmPlayer?.release()
        bgmPlayer = null
        bgmPrepared = false
        bgmFile = null
        selectedMusic = music
        requestMusic(music)
    }

    private fun requestMusic(music: Music) {
        resourceCache.requestAudio(music.path) { file ->
            if (music == selectedMusic) {
                bgmFile = file
                startMusicIfReady()
            }
        }
    }

    private fun playNotification(volume: Float) {
        if (soundEffectsEnabled.get()) notificationFile?.let { playFile(it, volume) }
    }

    private fun postPreview(delayMillis: Long, action: () -> Unit) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            previewRunnables.remove(runnable)
            if (!released.get()) action()
        }
        previewRunnables += runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun playFile(file: File, volume: Float) {
        playPlayer(volume) { player -> player.setDataSource(file.path) }
    }

    private fun playPlayer(volume: Float, configure: (MediaPlayer) -> Unit) {
        if (released.get()) return
        val player = MediaPlayer()
        transientPlayers += player
        runCatching {
            configure(player)
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            player.setVolume(volume, volume)
            player.setOnPreparedListener {
                if (released.get() || it !in transientPlayers) finishPlayer(it) else it.start()
            }
            player.setOnCompletionListener(::finishPlayer)
            player.setOnErrorListener { failed, _, _ -> finishPlayer(failed); true }
            player.prepareAsync()
        }.onFailure { finishPlayer(player) }
    }

    private fun finishPlayer(player: MediaPlayer) {
        transientPlayers.remove(player)
        runCatching { player.release() }
    }

    private fun resourceId(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private companion object {
        data class Music(val path: String, val loopStart: Int, val loopEnd: Int)

        val MUSIC = arrayOf(
            Music("audio/dpp-trainer.mp3", 13440, 96959),
            Music("audio/dpp-rival.mp3", 13888, 66352),
            Music("audio/hgss-johto-trainer.mp3", 23731, 125086),
            Music("audio/hgss-kanto-trainer.mp3", 13003, 94656),
            Music("audio/bw-trainer.mp3", 14629, 110109),
            Music("audio/bw-rival.mp3", 19180, 57373),
            Music("audio/bw-subway-trainer.mp3", 15503, 110984),
            Music("audio/bw2-kanto-gym-leader.mp3", 14626, 58986),
            Music("audio/bw2-rival.mp3", 7152, 68708),
            Music("audio/xy-trainer.mp3", 7802, 82469),
            Music("audio/xy-rival.mp3", 7802, 58634),
            Music("audio/oras-trainer.mp3", 13579, 91548),
            Music("audio/oras-rival.mp3", 14303, 69149),
            Music("audio/sm-trainer.mp3", 8323, 89230),
            Music("audio/sm-rival.mp3", 11389, 62158)
        )
        const val MAX_PENDING_BATTLE_CUES = 16
        const val MAX_PENDING_BATTLE_CUE_AGE_MILLIS = 5_000L
    }

}
