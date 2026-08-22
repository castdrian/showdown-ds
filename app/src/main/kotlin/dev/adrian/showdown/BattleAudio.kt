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
import java.io.FileOutputStream
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
    private data class PendingAnnouncerCue(
        val cue: BattleAnnouncerCue,
        val path: String,
        val soundId: Int,
        val startAtMillis: Long
    )
    private data class ScheduledAnnouncerCue(
        val pending: PendingAnnouncerCue,
        val scheduledAtMillis: Long,
        var runnable: Runnable? = null
    )
    private data class PausedAnnouncerCue(
        val pending: PendingAnnouncerCue,
        val remainingDelayMillis: Long
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
    private val announcerFiles = mutableMapOf<BattleAnnouncerCue, File?>()
    private val loadedTransientSoundIds = mutableSetOf<Int>()
    private val activeBattleStreamIds = mutableSetOf<Int>()
    private val pendingBattleCues = ArrayDeque<PendingBattleCue>()
    private val pendingTransientSounds = ArrayDeque<PendingTransientSound>()
    private val pendingAnnouncerCues = ArrayDeque<PendingAnnouncerCue>()
    private val scheduledBattleCues = mutableListOf<ScheduledBattleCue>()
    private val pausedBattleCues = mutableListOf<PausedBattleCue>()
    private val scheduledAnnouncerCues = mutableListOf<ScheduledAnnouncerCue>()
    private val pausedAnnouncerCues = mutableListOf<PausedAnnouncerCue>()
    private val activeAnnouncerStreamIds = mutableSetOf<Int>()
    private val diagnosticEvents = ArrayDeque<BattleAudioCueEvent>()
    private val cuePlaybackQueue = BattleAudioCuePlaybackQueue()
    private val announcerPlaybackQueue = BattleAnnouncerCuePlaybackQueue()
    private var cuePlaybackGeneration = 0L
    private var announcerPlaybackGeneration = 0L
    private var announcerCuesPaused = false
    private var announcerCuesPausedAtMillis = 0L
    private var notificationFile: File? = null
    private var bgmFile: File? = null
    private var bgmPlayer: MediaPlayer? = null
    private var bgmPrepared = false
    private val soundEffectsEnabled = AtomicBoolean(true)
    @Volatile
    private var musicEnabled = false
    @Volatile
    private var announcerEnabled = false
    private var battlePlaybackSpeed = 1f
    private val released = AtomicBoolean(false)
    private val previewRunnables = mutableSetOf<Runnable>()
    @Volatile
    private var selectedMusic = MUSIC[session.showdownMusicIndex()]
    private var battleCuesPaused = false
    private var battleCuesPausedAtMillis = 0L
    private val loopBoundaryRunnable = object : Runnable {
        override fun run() {
            val player = bgmPlayer ?: return
            if (!musicEnabled || !bgmPrepared || !player.isPlaying) return
            if (player.currentPosition < selectedMusic.loopEnd - MUSIC_LOOP_GUARD_MILLIS) {
                scheduleMusicLoop(player)
                return
            }
            startMusicFromLoopStart(player)
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
        val wasAnnouncerEnabled = announcerEnabled
        val effectsEnabled = session.soundEffectsEnabled
        soundEffectsEnabled.set(effectsEnabled)
        announcerEnabled = session.announcerEnabled
        if (announcerEnabled && !wasAnnouncerEnabled) audioCueHandler.post(::preloadAnnouncerAssets)
        if (!announcerEnabled && wasAnnouncerEnabled) audioCueHandler.post(::clearAnnouncerCues)
        if (!effectsEnabled) {
            audioCueHandler.post {
                clearPendingBattleCues()
                clearPendingTransientSounds()
                clearAnnouncerCues()
            }
        }
        musicEnabled = session.musicEnabled
        audioCueHandler.post {
            if (musicEnabled) {
                startMusicIfReady()
                if (bgmPrepared && bgmPlayer?.isPlaying == false) {
                    bgmPlayer?.start()
                    bgmPlayer?.let(::scheduleMusicLoop)
                }
            } else {
                audioCueHandler.removeCallbacks(loopBoundaryRunnable)
                bgmPlayer?.pause()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val nextSpeed = BattlePlaybackSpeed.coerce(speed)
        audioCueHandler.postAtFrontOfQueue {
            if (released.get()) return@postAtFrontOfQueue
            battlePlaybackSpeed = nextSpeed
            cuePlaybackQueue.setPlaybackSpeed(nextSpeed)
            announcerPlaybackQueue.setPlaybackSpeed(nextSpeed)
        }
    }

    fun pauseMusic() {
        audioCueHandler.post {
            audioCueHandler.removeCallbacks(loopBoundaryRunnable)
            bgmPlayer?.pause()
        }
    }

    fun pauseAnnouncerCues() {
        audioCueHandler.postAtFrontOfQueue {
            if (released.get() || announcerCuesPaused) return@postAtFrontOfQueue
            val nowMillis = SystemClock.elapsedRealtime()
            announcerCuesPaused = true
            announcerCuesPausedAtMillis = nowMillis
            scheduledAnnouncerCues
                .sortedBy { it.scheduledAtMillis }
                .forEach { scheduled ->
                    scheduled.runnable?.let(audioCueHandler::removeCallbacks)
                    pausedAnnouncerCues += PausedAnnouncerCue(
                        scheduled.pending,
                        (scheduled.scheduledAtMillis - nowMillis).coerceAtLeast(0L)
                    )
                }
            scheduledAnnouncerCues.clear()
            activeAnnouncerStreamIds.forEach { transientSoundPool?.pause(it) }
        }
    }

    fun resumeAnnouncerCues() {
        audioCueHandler.postAtFrontOfQueue {
            if (released.get() || !announcerCuesPaused) return@postAtFrontOfQueue
            val nowMillis = SystemClock.elapsedRealtime()
            val pausedDurationMillis = (nowMillis - announcerCuesPausedAtMillis).coerceAtLeast(0L)
            val shiftedPending = pendingAnnouncerCues.map { pending ->
                pending.copy(startAtMillis = pending.startAtMillis + pausedDurationMillis)
            }
            pendingAnnouncerCues.clear()
            shiftedPending.forEach(pendingAnnouncerCues::addLast)
            val restored = pausedAnnouncerCues
                .sortedBy { it.remainingDelayMillis }
                .map { paused ->
                    paused.pending.copy(startAtMillis = nowMillis + paused.remainingDelayMillis)
                }
            val restoredEndAtMillis = (shiftedPending + restored).maxOfOrNull { pending ->
                pending.startAtMillis + announcerPlaybackQueue.playbackDurationMillis(pending.cue) + ANNOUNCER_CUE_GAP_MILLIS
            } ?: nowMillis
            announcerPlaybackQueue.reset(maxOf(nowMillis, restoredEndAtMillis))
            pausedAnnouncerCues.clear()
            announcerCuesPaused = false
            announcerCuesPausedAtMillis = 0L
            restored.forEach { pending ->
                val soundId = transientSoundIds[pending.path]
                if (soundId != null && soundId in loadedTransientSoundIds) {
                    scheduleAnnouncerCue(pending.copy(soundId = soundId))
                } else {
                    pendingAnnouncerCues.addLast(pending)
                }
            }
            activeAnnouncerStreamIds.forEach { transientSoundPool?.resume(it) }
            flushPendingAnnouncerCues()
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        audioCueHandler.removeCallbacks(loopBoundaryRunnable)
        previewRunnables.toList().forEach(mainHandler::removeCallbacks)
        previewRunnables.clear()
        audioCueHandler.removeCallbacksAndMessages(null)
        audioCueHandler.post {
            bgmPlayer?.release()
            bgmPlayer = null
            bgmPrepared = false
            pendingBattleCues.clear()
            clearAnnouncerCues()
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
                it.remainingDelayMillis + cuePlaybackQueue.playbackDurationMillis(it.cue) + BATTLE_CUE_GAP_MILLIS
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
            val playback = cuePlaybackQueue.enqueue(cue, queuedAtMillis)
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
        val streamId = runCatching {
            battleSoundPool.play(soundId, 0.72f, 0.72f, 1, 0, battlePlaybackSpeed)
        }.getOrDefault(0)
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

    fun playAnnouncerCue(cue: BattleAnnouncerCue) {
        if (!announcerEnabled || !soundEffectsEnabled.get()) return
        audioCueHandler.post {
            if (!announcerEnabled || !soundEffectsEnabled.get()) return@post
            announcerFile(cue)?.let { enqueueAnnouncerCue(cue, it.path) }
        }
    }

    fun resetAnnouncerCues() {
        audioCueHandler.postAtFrontOfQueue {
            if (!released.get()) clearAnnouncerCues()
        }
    }

    private fun startMusicIfReady() {
        if (!musicEnabled || bgmPlayer != null) return
        val file = bgmFile ?: return
        val player = MediaPlayer()
        bgmPlayer = player
        runCatching {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            player.setDataSource(file.path)
            player.setVolume(0.32f, 0.32f)
            player.setOnErrorListener { _, _, _ ->
                releaseMusicPlayer(player)
                true
            }
            player.setOnPreparedListener {
                if (bgmPlayer !== player || released.get()) {
                    releaseMusicPlayer(player)
                    return@setOnPreparedListener
                }
                bgmPrepared = true
                if (!musicEnabled) return@setOnPreparedListener
                startMusicFromLoopStart(player)
            }
            player.setOnCompletionListener {
                if (bgmPlayer !== player || released.get() || !musicEnabled) return@setOnCompletionListener
                startMusicFromLoopStart(player)
            }
            player.prepareAsync()
        }.onFailure {
            releaseMusicPlayer(player)
        }
    }

    private fun releaseMusicPlayer(player: MediaPlayer) {
        if (bgmPlayer === player) {
            bgmPlayer = null
            bgmPrepared = false
            bgmFile = null
        }
        runCatching { player.release() }
    }

    private fun selectMusic(music: Music) {
        audioCueHandler.removeCallbacks(loopBoundaryRunnable)
        bgmPlayer?.release()
        bgmPlayer = null
        bgmPrepared = false
        bgmFile = null
        selectedMusic = music
        requestMusic(music)
    }

    private fun scheduleMusicLoop(player: MediaPlayer) {
        audioCueHandler.removeCallbacks(loopBoundaryRunnable)
        val remainingMillis = battleMusicLoopDelayMillis(
            player.currentPosition,
            selectedMusic.loopEnd,
            MUSIC_LOOP_GUARD_MILLIS,
            MUSIC_LOOP_POLL_INTERVAL_MILLIS
        )
        audioCueHandler.postDelayed(loopBoundaryRunnable, remainingMillis)
    }

    private fun startMusicFromLoopStart(player: MediaPlayer) {
        runCatching {
            player.seekTo(selectedMusic.loopStart)
            player.start()
            scheduleMusicLoop(player)
        }.onFailure { releaseMusicPlayer(player) }
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

    private fun preloadAnnouncerAssets() {
        BattleAnnouncerCue.values().forEach(::announcerFile)
    }

    private fun announcerFile(cue: BattleAnnouncerCue): File? = announcerFiles.getOrPut(cue) {
        val target = File(context.cacheDir, "showdown-announcer-${cue.assetName}.wav")
        if (target.isFile) {
            target
        } else {
            runCatching {
                context.assets.open(BattleAnnouncerAssets.assetPath(cue)).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                target
            }.getOrNull()
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
        val soundId = transientSoundId(path, pool)
        if (soundId == 0) return
        if (soundId !in loadedTransientSoundIds) {
            if (pendingTransientSounds.size < MAX_PENDING_TRANSIENT_SOUNDS) {
                pendingTransientSounds.addLast(PendingTransientSound(path, volume, SystemClock.elapsedRealtime()))
            }
            return
        }
        runCatching { pool.play(soundId, volume, volume, 1, 0, 1f) }
    }

    private fun enqueueAnnouncerCue(cue: BattleAnnouncerCue, path: String) {
        val pool = transientSoundPool ?: return
        val soundId = transientSoundId(path, pool)
        if (soundId == 0) return
        if (pendingAnnouncerCues.size >= MAX_PENDING_ANNOUNCER_CUES) pendingAnnouncerCues.removeFirst()
        val requestedAtMillis = SystemClock.elapsedRealtime()
        val playback = announcerPlaybackQueue.enqueue(cue, requestedAtMillis)
        val pending = PendingAnnouncerCue(
            cue,
            path,
            soundId,
            requestedAtMillis + playback.delayMillis
        )
        if (!announcerCuesPaused && soundId in loadedTransientSoundIds) {
            scheduleAnnouncerCue(pending)
        } else {
            pendingAnnouncerCues.addLast(pending)
        }
    }

    private fun scheduleAnnouncerCue(pending: PendingAnnouncerCue) {
        if (announcerCuesPaused) return
        if (pending.soundId !in loadedTransientSoundIds) {
            pendingAnnouncerCues.addLast(pending)
            return
        }
        val delayMillis = (pending.startAtMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val generation = announcerPlaybackGeneration
        val scheduled = ScheduledAnnouncerCue(
            pending,
            SystemClock.elapsedRealtime() + delayMillis
        )
        lateinit var runnable: Runnable
        runnable = Runnable {
            scheduledAnnouncerCues.remove(scheduled)
            if (generation != announcerPlaybackGeneration || released.get() || !announcerEnabled || !soundEffectsEnabled.get()) return@Runnable
            val soundId = transientSoundIds[pending.path]
            if (soundId != pending.soundId || soundId !in loadedTransientSoundIds) return@Runnable
            transientSoundPool?.let { pool ->
                val streamId = runCatching {
                    pool.play(soundId, 0.64f, 0.64f, 1, 0, battlePlaybackSpeed)
                }.getOrDefault(0)
                if (streamId != 0) activeAnnouncerStreamIds += streamId
            }
        }
        scheduled.runnable = runnable
        scheduledAnnouncerCues += scheduled
        audioCueHandler.postDelayed(runnable, delayMillis)
    }

    private fun flushPendingAnnouncerCues() {
        if (released.get() || !announcerEnabled || !soundEffectsEnabled.get()) {
            clearAnnouncerCues()
            return
        }
        if (announcerCuesPaused) return
        val pendingCount = pendingAnnouncerCues.size
        repeat(pendingCount) {
            val pending = pendingAnnouncerCues.removeFirst()
            val soundId = transientSoundIds[pending.path]
            if (soundId != null && soundId in loadedTransientSoundIds) {
                scheduleAnnouncerCue(pending.copy(soundId = soundId))
            } else {
                pendingAnnouncerCues.addLast(pending)
            }
        }
    }

    private fun clearAnnouncerCues() {
        pendingAnnouncerCues.clear()
        scheduledAnnouncerCues.forEach { it.runnable?.let(audioCueHandler::removeCallbacks) }
        scheduledAnnouncerCues.clear()
        pausedAnnouncerCues.clear()
        activeAnnouncerStreamIds.forEach { transientSoundPool?.stop(it) }
        activeAnnouncerStreamIds.clear()
        announcerPlaybackGeneration += 1
        announcerCuesPaused = false
        announcerCuesPausedAtMillis = 0L
        announcerPlaybackQueue.reset(SystemClock.elapsedRealtime())
    }

    private fun transientSoundId(path: String, pool: SoundPool): Int = transientSoundIds[path] ?: runCatching {
        while (transientSoundIds.size >= MAX_TRANSIENT_SOUND_SAMPLES) evictOldestTransientSound(pool)
        pool.load(path, 1)
    }.getOrDefault(0).also { loadedId ->
        if (loadedId != 0) {
            transientSoundIds[path] = loadedId
            transientSoundPathsById[loadedId] = path
        }
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
                        flushPendingAnnouncerCues()
                    } else if (status != 0) {
                        transientSoundPathsById.remove(sampleId)
                        transientSoundIds.remove(path)
                        pendingTransientSounds.removeIf { it.path == path }
                        pendingAnnouncerCues.removeIf { it.path == path }
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
        const val MAX_PENDING_ANNOUNCER_CUES = 12
        const val MAX_PENDING_TRANSIENT_SOUND_AGE_MILLIS = 4_000L
        const val MAX_TRANSIENT_SOUND_SAMPLES = 24
        const val MAX_DIAGNOSTIC_EVENTS = 24
        const val BATTLE_CUE_GAP_MILLIS = 24L
        const val ANNOUNCER_CUE_GAP_MILLIS = 24L
        const val MUSIC_LOOP_GUARD_MILLIS = 750L
        const val MUSIC_LOOP_POLL_INTERVAL_MILLIS = 500L
    }

}

internal fun battleMusicLoopDelayMillis(
    currentPosition: Int,
    loopEnd: Int,
    guardMillis: Long,
    pollIntervalMillis: Long = 0L
): Long {
    val remainingToGuard = loopEnd.toLong() - currentPosition.toLong() - guardMillis
    return if (remainingToGuard <= 0L) 0L else remainingToGuard + pollIntervalMillis
}
