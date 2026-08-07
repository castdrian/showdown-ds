package com.showdown.ds

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class BattleAudio(
    private val context: Context,
    private val resourceCache: ShowdownSpriteCache,
    session: BattleSession
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var notificationFile: File? = null
    private var bgmFile: File? = null
    private var bgmPlayer: MediaPlayer? = null
    private var activeMovePlayer: MediaPlayer? = null
    private var activeMoveStop: Runnable? = null
    private var bgmPrepared = false
    private var soundEffectsEnabled = true
    private var musicEnabled = false
    private val moveSoundDirectory = File(context.cacheDir, "move-sfx").apply { mkdirs() }
    private val moveSoundCache = ConcurrentHashMap<String, File>()
    private val moveDurationCache = ConcurrentHashMap<String, Long>()
    private val pendingMoveSounds = ConcurrentHashMap.newKeySet<String>()
    private val moveSoundCallbacks = mutableMapOf<String, MutableList<(File?) -> Unit>>()
    private val moveSoundLock = Any()
    private val moveSoundExecutor = Executors.newFixedThreadPool(2)
    private val moveSoundTimeline = MoveSoundTimeline()
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
        resourceCache.requestAudio("audio/notification.wav") { notificationFile = it }
        requestMusic(selectedMusic)
    }

    fun updateOptions(session: BattleSession) {
        val requestedMusic = MUSIC[session.showdownMusicIndex()]
        if (requestedMusic != selectedMusic) selectMusic(requestedMusic)
        soundEffectsEnabled = session.soundEffectsEnabled
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
        mainHandler.removeCallbacks(loopCheck)
        bgmPlayer?.release()
        bgmPlayer = null
        stopActiveMove()
        moveSoundTimeline.beginMove()
        moveSoundExecutor.shutdownNow()
    }

    fun playNavigation() = playNotification(0.35f)

    fun playConfirm() = playNotification(0.55f)

    fun playCancel() = playNotification(0.25f)

    fun playImpact(impact: BattleSession.HitImpact) {
        val volume = when (impact) {
            BattleSession.HitImpact.RESISTED -> 0.28f
            BattleSession.HitImpact.NORMAL -> 0.45f
            BattleSession.HitImpact.SUPER_EFFECTIVE -> 0.62f
            BattleSession.HitImpact.CRITICAL -> 0.70f
            BattleSession.HitImpact.SUPER_EFFECTIVE_CRITICAL -> 0.82f
        }
        val effect = when (impact) {
            BattleSession.HitImpact.RESISTED -> "Hit Weak Not Very Effective"
            BattleSession.HitImpact.NORMAL, BattleSession.HitImpact.CRITICAL -> "Hit Normal Damage"
            BattleSession.HitImpact.SUPER_EFFECTIVE, BattleSession.HitImpact.SUPER_EFFECTIVE_CRITICAL -> "Hit Super Effective"
        }
        loadMoveSound(effect) { file -> file?.let { playFile(it, volume) } }
    }

    fun preloadMoves(moves: List<String>) {
        moves.forEach { loadMoveSound(it) { } }
    }

    fun planMovePresentation(move: String, visualDurationMillis: Long) =
        MovePresentationTiming.plan(visualDurationMillis, moveDurationMillis(move))

    fun playMove(move: String, presentationDurationMillis: Long) {
        if (!soundEffectsEnabled) return
        if (presentationDurationMillis <= 0L) return
        val moveToken = moveSoundTimeline.beginMove(SystemClock.elapsedRealtime())
        stopActiveMove()
        val id = resourceId(move)
        val audioDurationMillis = moveDurationMillis(move)
        if (audioDurationMillis <= 0L) return
        moveSoundCache[id]?.let { file ->
            playMoveFile(file, audioDurationMillis, presentationDurationMillis, moveToken)
            return
        }
        playMoveAsset(id, audioDurationMillis, presentationDurationMillis, moveToken)
    }

    fun playCry(species: String) {
        if (!soundEffectsEnabled) return
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
        if (soundEffectsEnabled) notificationFile?.let { playFile(it, volume) }
    }

    private fun loadMoveSound(move: String, receiver: (File?) -> Unit) {
        val id = resourceId(move)
        moveSoundCache[id]?.let {
            receiver(it)
            return
        }
        synchronized(moveSoundLock) {
            moveSoundCache[id]?.let {
                receiver(it)
                return
            }
            moveSoundCallbacks.getOrPut(id) { mutableListOf() } += receiver
            if (!pendingMoveSounds.add(id)) return
        }
        moveSoundExecutor.execute {
            val file = copyMoveSound(id)
            if (file != null) moveSoundCache[id] = file
            val callbacks = synchronized(moveSoundLock) {
                pendingMoveSounds.remove(id)
                moveSoundCallbacks.remove(id).orEmpty()
            }
            mainHandler.post { callbacks.forEach { it(file) } }
        }
    }

    private fun copyMoveSound(id: String): File? {
        val target = File(moveSoundDirectory, "$id.mp3")
        if (target.isFile && target.length() > 0L) return target
        return runCatching {
            context.assets.open("move-sfx/$id.mp3").use { input ->
                val temporary = File(target.parentFile, "${target.name}.part")
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
                if (!temporary.renameTo(target)) throw IOException("Unable to cache move sound")
            }
            target
        }.getOrNull()
    }

    private fun playFile(file: File, volume: Float) {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(file.path)
            setVolume(volume, volume)
            setOnPreparedListener { start() }
            setOnCompletionListener { release() }
            prepareAsync()
        }
    }

    private fun playMoveAsset(id: String, audioDurationMillis: Long, presentationDurationMillis: Long, moveToken: Long) {
        val player = runCatching {
            context.assets.openFd("move-sfx/$id.mp3").use { asset ->
                MediaPlayer().apply {
                    setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
            }
        }.getOrNull() ?: return
        startMovePlayer(player, audioDurationMillis, presentationDurationMillis, moveToken)
    }

    private fun playMoveFile(file: File, audioDurationMillis: Long, presentationDurationMillis: Long, moveToken: Long) {
        val player = runCatching {
            MediaPlayer().apply { setDataSource(file.path) }
        }.getOrNull() ?: return
        startMovePlayer(player, audioDurationMillis, presentationDurationMillis, moveToken)
    }

    private fun startMovePlayer(player: MediaPlayer, audioDurationMillis: Long, presentationDurationMillis: Long, moveToken: Long) {
        val visualDispatchAtMillis = SystemClock.elapsedRealtime()
        player.apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setVolume(0.72f, 0.72f)
            setOnPreparedListener {
                val remainingPresentationMillis = presentationDurationMillis - (SystemClock.elapsedRealtime() - visualDispatchAtMillis)
                if (activeMovePlayer !== this || !moveSoundTimeline.isPlayable(moveToken, SystemClock.elapsedRealtime()) || remainingPresentationMillis <= 0L) {
                    release()
                    return@setOnPreparedListener
                }
                isLooping = audioDurationMillis < remainingPresentationMillis
                start()
                val stop = Runnable {
                    if (activeMovePlayer === this) {
                        activeMovePlayer = null
                        release()
                    }
                }
                activeMoveStop = stop
                mainHandler.postDelayed(stop, remainingPresentationMillis)
            }
            setOnCompletionListener {
                if (activeMovePlayer === this) {
                    activeMovePlayer = null
                    activeMoveStop?.let(mainHandler::removeCallbacks)
                    activeMoveStop = null
                }
                release()
            }
            prepareAsync()
        }
        activeMovePlayer = player
    }

    private fun stopActiveMove() {
        activeMoveStop?.let(mainHandler::removeCallbacks)
        activeMoveStop = null
        activeMovePlayer?.let { player ->
            runCatching { player.stop() }
            player.release()
        }
        activeMovePlayer = null
    }

    private fun moveDurationMillis(move: String): Long {
        val id = resourceId(move)
        moveDurationCache[id]?.let { return it }
        val duration = runCatching {
            context.assets.openFd("move-sfx/$id.mp3").use { asset ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
            }
        }.getOrDefault(0L)
        if (duration > 0L) moveDurationCache[id] = duration
        return duration
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
    }
}
