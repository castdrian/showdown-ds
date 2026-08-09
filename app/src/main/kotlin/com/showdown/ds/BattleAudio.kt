package com.showdown.ds

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

class BattleAudio(
    private val context: Context,
    private val resourceCache: ShowdownSpriteCache,
    session: BattleSession
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var notificationFile: File? = null
    private var bgmFile: File? = null
    private var bgmPlayer: MediaPlayer? = null
    private var bgmPrepared = false
    private var soundEffectsEnabled = true
    private var musicEnabled = false
    private var released = false
    private val transientPlayers = mutableSetOf<MediaPlayer>()
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
        released = true
        mainHandler.removeCallbacks(loopCheck)
        bgmPlayer?.release()
        bgmPlayer = null
        transientPlayers.toList().forEach(::finishPlayer)
    }

    fun playNavigation() = playNotification(0.35f)

    fun playConfirm() = playNotification(0.55f)

    fun playCancel() = playNotification(0.25f)

    fun playBattleCue(cue: BattleAudioCue) {
        if (!soundEffectsEnabled) return
        playCue(cue)
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

    private fun playFile(file: File, volume: Float) {
        playPlayer(volume) { player -> player.setDataSource(file.path) }
    }

    private fun playCue(cue: BattleAudioCue) {
        playPlayer(0.72f) { player ->
            context.assets.openFd("move-sfx/${cue.assetName}.mp3").use { asset ->
                player.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
        }
    }

    private fun playPlayer(volume: Float, configure: (MediaPlayer) -> Unit) {
        if (released) return
        val player = MediaPlayer()
        transientPlayers += player
        runCatching {
            configure(player)
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            player.setVolume(volume, volume)
            player.setOnPreparedListener {
                if (released || it !in transientPlayers) finishPlayer(it) else it.start()
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
    }
}
