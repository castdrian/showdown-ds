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
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BattleAudio(
    private val context: Context,
    private val resourceCache: ShowdownSpriteCache,
    session: BattleSession
) {
    private data class PendingBattleCue(val cue: BattleAudioCue, var queuedAtMillis: Long)
    private data class ScheduledBattleCue(
        val cue: BattleAudioCue,
        val soundId: Int,
        val queuedAtMillis: Long,
        val plannedDelayMillis: Long,
        val scheduledAtMillis: Long,
        var runnable: Runnable? = null
    )
    private data class PausedBattleCue(
        val cue: BattleAudioCue,
        val soundId: Int,
        val queuedAtMillis: Long,
        val plannedDelayMillis: Long,
        val remainingDelayMillis: Long
    )
    private data class PendingTransientSound(
        val path: String,
        val volume: Float,
        val queuedAtMillis: Long
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioCueThread = HandlerThread("showdown-audio").also { it.start() }
    private val audioCueHandler = Handler(audioCueThread.looper)
    private val battleSoundPool = createSoundPool(8)
    private var transientSoundPool: SoundPool? = null
    private val battleSoundIds = Collections.synchronizedMap(mutableMapOf<BattleAudioCue, Int>())
    private val loadedBattleSoundIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val failedBattleCues = Collections.synchronizedSet(mutableSetOf<BattleAudioCue>())
    private val transientSoundIds = LinkedHashMap<String, Int>(16, 0.75f, true)
    private val transientSoundPathsById = mutableMapOf<Int, String>()
    private val loadedTransientSoundIds = mutableSetOf<Int>()
    private val activeBattleStreamIds = mutableSetOf<Int>()
    private val pendingBattleCues = ArrayDeque<PendingBattleCue>()
    private val pendingTransientSounds = ArrayDeque<PendingTransientSound>()
    private val scheduledBattleCues = mutableListOf<ScheduledBattleCue>()
    private val pausedBattleCues = mutableListOf<PausedBattleCue>()
    private val diagnosticEvents = ArrayDeque<BattleAudioCueEvent>()
    private val cuePlaybackQueue = BattleAudioCuePlaybackQueue()
    private var cuePlaybackGeneration = 0L
    private var notificationFile: File? = null
    private var bgmFile: File? = null
    private var bgmPlayer: MediaPlayer? = null
    private var bgmPrepared = false
    private val soundEffectsEnabled = AtomicBoolean(true)
    @Volatile
    private var musicEnabled = false
    private val released = AtomicBoolean(false)
    private val previewRunnables = mutableSetOf<Runnable>()
    @Volatile
    private var selectedMusic = MUSIC[session.showdownMusicIndex()]
    private var battleCuesPaused = false
    private var battleCuesPausedAtMillis = 0L
    private val loopCheck = object : Runnable {
        override fun run() {
            val player = bgmPlayer ?: return
            if (musicEnabled && player.isPlaying) {
                if (player.currentPosition >= selectedMusic.loopEnd - 750) player.seekTo(selectedMusic.loopStart)
                audioCueHandler.postDelayed(this, 500)
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
            } else {
                audioCueHandler.post {
                    synchronized(battleSoundIds) {
                        battleSoundIds.entries.firstOrNull { it.value == sampleId }?.key?.let { failedBattleCues += it }
                    }
                }
            }
        }
        audioCueHandler.post(::initializeTransientSoundPool)
        audioCueHandler.post(::loadBattleSounds)
        resourceCache.requestAudio("audio/notification.wav") { notificationFile = it }
        requestMusic(selectedMusic)
    }

    fun updateOptions(session: BattleSession) {
        if (released.get()) return
        val requestedMusic = MUSIC[session.showdownMusicIndex()]
        if (requestedMusic != selectedMusic) audioCueHandler.post { selectMusic(requestedMusic) }
        val effectsEnabled = session.soundEffectsEnabled
        soundEffectsEnabled.set(effectsEnabled)
        if (!effectsEnabled) {
            audioCueHandler.post {
                clearPendingBattleCues()
                clearPendingTransientSounds()
            }
        }
        musicEnabled = session.musicEnabled
        audioCueHandler.post {
            if (musicEnabled) {
                startMusicIfReady()
                if (bgmPrepared && bgmPlayer?.isPlaying == false) {
                    bgmPlayer?.start()
                    audioCueHandler.post(loopCheck)
                }
            } else {
                bgmPlayer?.pause()
            }
        }
    }

    fun pauseMusic() {
        audioCueHandler.post { bgmPlayer?.pause() }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        audioCueHandler.removeCallbacks(loopCheck)
        previewRunnables.toList().forEach(mainHandler::removeCallbacks)
        previewRunnables.clear()
        audioCueHandler.removeCallbacksAndMessages(null)
        audioCueHandler.post {
            bgmPlayer?.release()
            bgmPlayer = null
            bgmPrepared = false
            pendingBattleCues.clear()
            scheduledBattleCues.clear()
            pausedBattleCues.clear()
            stopActiveBattleStreams()
            clearPendingTransientSounds()
            battleSoundPool.release()
            transientSoundIds.clear()
            transientSoundPathsById.clear()
            loadedTransientSoundIds.clear()
            transientSoundPool?.release()
            transientSoundPool = null
            audioCueThread.quitSafely()
        }
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

    fun diagnosticSnapshot(): BattleAudioDiagnosticSnapshot {
        val loaded = synchronized(battleSoundIds) {
            battleSoundIds.filterValues { it in loadedBattleSoundIds }.keys.toSet()
        }
        val failed = synchronized(failedBattleCues) { failedBattleCues.toSet() }
        val events = synchronized(diagnosticEvents) { diagnosticEvents.toList() }
        return BattleAudioDiagnosticSnapshot(loaded, failed, events)
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
            if (!released.get()) {
                clearPendingBattleCues()
                synchronized(diagnosticEvents) { diagnosticEvents.clear() }
            }
        }
    }

    fun pauseBattleCues() {
        audioCueHandler.postAtFrontOfQueue {
            if (released.get() || battleCuesPaused) return@postAtFrontOfQueue
            val nowMillis = SystemClock.elapsedRealtime()
            battleCuesPaused = true
            battleCuesPausedAtMillis = nowMillis
            scheduledBattleCues
                .sortedBy { it.scheduledAtMillis }
                .forEach { scheduled ->
                    scheduled.runnable?.let(audioCueHandler::removeCallbacks)
                    pausedBattleCues += PausedBattleCue(
                        scheduled.cue,
                        scheduled.soundId,
                        scheduled.queuedAtMillis,
                        scheduled.plannedDelayMillis,
                        (scheduled.scheduledAtMillis - nowMillis).coerceAtLeast(0L)
                    )
                }
            scheduledBattleCues.clear()
            activeBattleStreamIds.forEach(battleSoundPool::pause)
        }
    }

    fun resumeBattleCues() {
        audioCueHandler.postAtFrontOfQueue {
            if (released.get() || !battleCuesPaused) return@postAtFrontOfQueue
            val nowMillis = SystemClock.elapsedRealtime()
            val pausedDurationMillis = (nowMillis - battleCuesPausedAtMillis).coerceAtLeast(0L)
            pendingBattleCues.forEach { it.queuedAtMillis += pausedDurationMillis }
            val restored = pausedBattleCues
                .sortedBy { it.remainingDelayMillis }
                .map { it.copy(queuedAtMillis = it.queuedAtMillis + pausedDurationMillis) }
            val restoredEndMillis = restored.maxOfOrNull {
                it.remainingDelayMillis + it.cue.playbackDurationMillis + BATTLE_CUE_GAP_MILLIS
            } ?: 0L
            cuePlaybackQueue.reset(maxOf(nowMillis, cuePlaybackQueue.availableAtMillis(), nowMillis + restoredEndMillis))
            pausedBattleCues.clear()
            battleCuesPaused = false
            battleCuesPausedAtMillis = 0L
            restored.forEach { paused ->
                scheduleBattleCue(
                    paused.cue,
                    paused.soundId,
                    paused.queuedAtMillis,
                    paused.plannedDelayMillis,
                    paused.remainingDelayMillis
                )
            }
            activeBattleStreamIds.forEach(battleSoundPool::resume)
            flushPendingBattleCues()
        }
    }

    fun beginBattleMove() {
        audioCueHandler.postAtFrontOfQueue {
            if (!released.get()) clearPendingBattleCues()
        }
    }

    private fun flushPendingBattleCues() {
        if (released.get() || !soundEffectsEnabled.get()) {
            clearPendingBattleCues()
            return
        }
        if (battleCuesPaused) return
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
            val cue = pending.cue
            val queuedAtMillis = pending.queuedAtMillis
            val playback = cuePlaybackQueue.enqueue(cue, nowMillis)
            scheduleBattleCue(cue, soundId, queuedAtMillis, playback.delayMillis, playback.delayMillis)
        }
    }

    private fun scheduleBattleCue(
        cue: BattleAudioCue,
        soundId: Int,
        queuedAtMillis: Long,
        plannedDelayMillis: Long,
        delayMillis: Long
    ) {
        if (released.get() || !soundEffectsEnabled.get() || battleCuesPaused) return
        val scheduled = ScheduledBattleCue(
            cue,
            soundId,
            queuedAtMillis,
            plannedDelayMillis,
            SystemClock.elapsedRealtime() + delayMillis
        )
        val generation = cuePlaybackGeneration
        lateinit var runnable: Runnable
        runnable = Runnable {
            scheduledBattleCues.remove(scheduled)
            if (generation != cuePlaybackGeneration || released.get() || !soundEffectsEnabled.get() || battleCuesPaused) return@Runnable
            playBattleCueNow(cue, soundId, queuedAtMillis, plannedDelayMillis)
        }
        scheduled.runnable = runnable
        scheduledBattleCues += scheduled
        audioCueHandler.postDelayed(runnable, delayMillis)
    }

    private fun clearPendingBattleCues() {
        pendingBattleCues.clear()
        scheduledBattleCues.forEach { it.runnable?.let(audioCueHandler::removeCallbacks) }
        scheduledBattleCues.clear()
        pausedBattleCues.clear()
        stopActiveBattleStreams()
        cuePlaybackGeneration += 1
        cuePlaybackQueue.reset(SystemClock.elapsedRealtime())
    }

    private fun stopActiveBattleStreams() {
        activeBattleStreamIds.forEach(battleSoundPool::stop)
        activeBattleStreamIds.clear()
    }

    private fun playBattleCueNow(cue: BattleAudioCue, soundId: Int, queuedAtMillis: Long, plannedDelayMillis: Long) {
        if (released.get() || !soundEffectsEnabled.get()) return
        val streamId = runCatching { battleSoundPool.play(soundId, 0.72f, 0.72f, 1, 0, 1f) }.getOrDefault(0)
        val playbackAccepted = streamId != 0
        if (playbackAccepted) activeBattleStreamIds += streamId
        val actualDelayMillis = (SystemClock.elapsedRealtime() - queuedAtMillis).coerceAtLeast(0L)
        synchronized(diagnosticEvents) {
            if (diagnosticEvents.size >= MAX_DIAGNOSTIC_EVENTS) diagnosticEvents.removeFirst()
            diagnosticEvents.addLast(
                BattleAudioCueEvent(
                    cue,
                    queuedAtMillis,
                    plannedDelayMillis,
                    actualDelayMillis,
                    playbackAccepted
                )
            )
        }
    }

    fun playCry(species: String) {
        if (!soundEffectsEnabled.get()) return
        resourceCache.requestAudio("audio/cries/${resourceId(species)}.mp3") { file ->
            file?.let { audioCueHandler.post { playTransientSound(it.path, 0.60f) } }
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
                audioCueHandler.post(loopCheck)
            }
            setOnCompletionListener {
                if (musicEnabled) {
                    seekTo(selectedMusic.loopStart)
                    start()
                    audioCueHandler.post(loopCheck)
                }
            }
            prepareAsync()
        }
    }

    private fun selectMusic(music: Music) {
        audioCueHandler.removeCallbacks(loopCheck)
        bgmPlayer?.release()
        bgmPlayer = null
        bgmPrepared = false
        bgmFile = null
        selectedMusic = music
        requestMusic(music)
    }

    private fun requestMusic(music: Music) {
        resourceCache.requestAudio(music.path) { file ->
            file ?: return@requestAudio
            audioCueHandler.post {
                if (released.get() || music != selectedMusic) return@post
                bgmFile = file
                startMusicIfReady()
            }
        }
    }

    private fun playNotification(volume: Float) {
        if (soundEffectsEnabled.get()) notificationFile?.let { file ->
            audioCueHandler.post { playTransientSound(file.path, volume) }
        }
    }

    private fun loadBattleSounds() {
        if (released.get()) return
        BattleAudioCue.values().forEach { cue ->
            runCatching {
                context.assets.openFd("move-sfx/${cue.assetName}.mp3").use { asset ->
                    battleSoundIds[cue] = battleSoundPool.load(asset.fileDescriptor, asset.startOffset, asset.length, 1)
                }
            }.onFailure { failedBattleCues += cue }
        }
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

    private fun playTransientSound(path: String, volume: Float) {
        if (released.get() || !soundEffectsEnabled.get()) return
        val pool = transientSoundPool ?: return
        val soundId = transientSoundIds[path] ?: runCatching {
            while (transientSoundIds.size >= MAX_TRANSIENT_SOUND_SAMPLES) evictOldestTransientSound(pool)
            pool.load(path, 1)
        }.getOrDefault(0).also { loadedId ->
            if (loadedId != 0) {
                transientSoundIds[path] = loadedId
                transientSoundPathsById[loadedId] = path
            }
        }
        if (soundId == 0) return
        if (soundId !in loadedTransientSoundIds) {
            if (pendingTransientSounds.size < MAX_PENDING_TRANSIENT_SOUNDS) {
                pendingTransientSounds.addLast(PendingTransientSound(path, volume, SystemClock.elapsedRealtime()))
            }
            return
        }
        runCatching { pool.play(soundId, volume, volume, 1, 0, 1f) }
    }

    private fun flushPendingTransientSounds() {
        if (released.get() || !soundEffectsEnabled.get()) {
            clearPendingTransientSounds()
            return
        }
        val pendingCount = pendingTransientSounds.size
        repeat(pendingCount) {
            val pending = pendingTransientSounds.removeFirst()
            if (SystemClock.elapsedRealtime() - pending.queuedAtMillis > MAX_PENDING_TRANSIENT_SOUND_AGE_MILLIS) return@repeat
            val soundId = transientSoundIds[pending.path]
            if (soundId == null || soundId !in loadedTransientSoundIds) {
                pendingTransientSounds.addLast(pending)
            } else {
                transientSoundPool?.let { pool ->
                    runCatching { pool.play(soundId, pending.volume, pending.volume, 1, 0, 1f) }
                }
            }
        }
    }

    private fun clearPendingTransientSounds() {
        pendingTransientSounds.clear()
    }

    private fun evictOldestTransientSound(pool: SoundPool) {
        val eldest = transientSoundIds.entries.firstOrNull() ?: return
        val path = eldest.key
        val soundId = eldest.value
        transientSoundIds.remove(path)
        transientSoundPathsById.remove(soundId)
        loadedTransientSoundIds.remove(soundId)
        pendingTransientSounds.removeIf { it.path == path }
        runCatching { pool.unload(soundId) }
    }

    private fun initializeTransientSoundPool() {
        if (released.get()) return
        transientSoundPool = createSoundPool(8).also { pool ->
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                audioCueHandler.post {
                    val path = transientSoundPathsById[sampleId] ?: return@post
                    if (status == 0 && transientSoundIds[path] == sampleId) {
                        loadedTransientSoundIds += sampleId
                        flushPendingTransientSounds()
                    } else if (status != 0) {
                        transientSoundPathsById.remove(sampleId)
                        transientSoundIds.remove(path)
                        pendingTransientSounds.removeIf { it.path == path }
                    }
                }
            }
        }
    }

    private fun createSoundPool(maxStreams: Int) = SoundPool.Builder()
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

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
        const val MAX_PENDING_TRANSIENT_SOUNDS = 12
        const val MAX_PENDING_TRANSIENT_SOUND_AGE_MILLIS = 4_000L
        const val MAX_TRANSIENT_SOUND_SAMPLES = 24
        const val MAX_DIAGNOSTIC_EVENTS = 24
        const val BATTLE_CUE_GAP_MILLIS = 24L
    }

}
