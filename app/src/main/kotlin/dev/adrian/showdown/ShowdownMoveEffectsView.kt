package dev.adrian.showdown

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.os.Handler
import android.os.Looper
import org.json.JSONArray

@SuppressLint("SetJavaScriptEnabled")
class ShowdownMoveEffectsView(
    context: Context,
    private val audioCueListener: (BattleAudioCue) -> Unit,
    private val audioCueResetter: () -> Unit = {},
    private val audioMoveResetter: () -> Unit = {},
    private val announcerCueListener: (BattleAnnouncerCue) -> Unit = {},
    private val announcerCueResetter: () -> Unit = {},
    private val battleLogListener: (String, Long, Long?, List<Long>) -> Unit = { _, _, _, _ -> },
    private val battleMarkupListener: (String, String, Long) -> Unit = { _, _, _ -> },
    private val battleLogSyncListener: (Long) -> Unit = {},
    private val battleEffectsIdleListener: (Long) -> Unit = {}
) : WebView(context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingPackets = ShowdownMoveEffectsQueue()
    private var pageLoaded = false
    private var javascriptReady = false
    private var playbackPaused = false
    private var playbackSpeed = 1f
    private var battlePerspective = "p1"
    private var animationsDisabledForTesting = false
    private var released = false
    private var cleanupCompleted = false
    private val releaseFallbackRunnable = Runnable { cleanupOnMainThread() }
    private val nativeIdlePauseRunnable = Runnable {
        if (!released && javascriptReady && !playbackPaused) {
            runJavascript("window.ShowdownNativeEffects.pauseWhenIdle();")
        }
    }
    private val nativeAudioBridge = NativeAudioBridge(
        audioCueListener,
        audioCueResetter,
        audioMoveResetter,
        announcerCueListener,
        announcerCueResetter
    )
    private val nativeBattleLogBridge = NativeBattleLogBridge(
        battleLogListener,
        battleMarkupListener,
        battleLogSyncListener,
        battleEffectsIdleListener,
        { mainHandler.post { markJavascriptReady() } }
    )

    init {
        setBackgroundColor(Color.BLACK)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        addJavascriptInterface(nativeAudioBridge, NATIVE_AUDIO_BRIDGE)
        addJavascriptInterface(nativeBattleLogBridge, NATIVE_BATTLE_LOG_BRIDGE)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (released) return
                pageLoaded = true
                initializeJavascriptIfReady()
            }
        }
        loadDataWithBaseURL(BASE_URL, loadDocument(context), "text/html", "UTF-8", null)
    }

    private fun loadDocument(context: Context): String {
        val template = context.assets.open(ShowdownBattleSceneDocument.ASSET_NAME).bufferedReader().use { it.readText() }
        return ShowdownBattleSceneDocument.render(template, BuildConfig.DEBUG)
    }

    fun seed(lines: List<String>) = seed(lines, 0L)

    fun recoverFromProtocolHistory(lines: List<String>, effectsBarrierToken: Long) = seed(lines, effectsBarrierToken)

    private fun seed(lines: List<String>, effectsBarrierToken: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { seed(lines, effectsBarrierToken) }
            return
        }
        if (released) return
        val packet = lines.filter { it.startsWith('|') }
        pendingPackets.resetWith(packet, effectsBarrierToken)
        if (packet.isNotEmpty()) flushPendingPackets(allowSeedWhilePaused = true)
    }

    fun applyProtocol(
        lines: List<String>,
        battleLogGeneration: Long = 0L,
        effectsBarrierToken: Long = 0L,
        identitiesByLine: List<BattleSession.ProtocolLineIdentity> = emptyList()
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                applyProtocol(lines, battleLogGeneration, effectsBarrierToken, identitiesByLine)
            }
            return
        }
        if (released) return
        val protocolLines = lines.mapIndexedNotNull { index, line ->
            line.takeIf { it.startsWith('|') }?.let { it to identitiesByLine.getOrNull(index) }
        }
        val packet = protocolLines.map { it.first }
        if (packet.isEmpty()) return
        if (packet.any { it.startsWith("|init|battle") }) {
            pendingPackets.clear()
        }
        val chunks = BattlePlaybackTiming.chunks(packet)
        var chunkOffset = 0
        chunks.forEachIndexed { index, chunk ->
            val chunkMessageIds = protocolLines
                .drop(chunkOffset)
                .take(chunk.size)
                .map { it.second ?: BattleSession.ProtocolLineIdentity(0L, emptyList()) }
            chunkOffset += chunk.size
            pendingPackets.add(
                chunk,
                battleLogGeneration,
                index == chunks.lastIndex,
                if (index == chunks.lastIndex) effectsBarrierToken else 0L,
                chunkMessageIds
            )
        }
        flushPendingPackets()
    }

    fun setPlaybackPaused(paused: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setPlaybackPaused(paused) }
            return
        }
        if (released) return
        if (playbackPaused == paused) return
        playbackPaused = paused
        if (paused) {
            mainHandler.removeCallbacks(nativeIdlePauseRunnable)
            runJavascript("window.ShowdownNativeEffects.pause();")
        } else {
            runJavascript("window.ShowdownNativeEffects.resume();")
            flushPendingPackets()
            scheduleNativeIdlePause()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setPlaybackSpeed(speed) }
            return
        }
        if (released) return
        val nextSpeed = BattlePlaybackSpeed.coerce(speed)
        if (nextSpeed == playbackSpeed) return
        playbackSpeed = nextSpeed
        runJavascript("window.ShowdownNativeEffects.setSpeed($playbackSpeed);")
    }

    fun setPerspective(side: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setPerspective(side) }
            return
        }
        if (released) return
        val next = ShowdownBattlePerspective.acceptedSide(side) ?: return
        battlePerspective = next
        runJavascript("window.ShowdownNativeEffects.setPerspective('$next');")
    }

    fun setAnimationsDisabledForTesting(disabled: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setAnimationsDisabledForTesting(disabled) }
            return
        }
        if (released) return
        animationsDisabledForTesting = disabled
        runJavascript("window.ShowdownNativeEffects.setAnimationsDisabledForTesting($disabled);")
    }

    fun release() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { release() }
            return
        }
        if (released) return
        released = true
        mainHandler.removeCallbacks(nativeIdlePauseRunnable)
        mainHandler.removeCallbacks(releaseFallbackRunnable)
        pendingPackets.clear()
        mainHandler.postDelayed(releaseFallbackRunnable, RELEASE_FALLBACK_DELAY_MILLIS)
        val cleanup = { cleanupOnMainThread() }
        if (javascriptReady) {
            evaluateJavascript("window.ShowdownNativeEffects.release();") { mainHandler.post(cleanup) }
        } else {
            cleanup()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun flushPendingPackets(allowSeedWhilePaused: Boolean = false) {
        if ((!allowSeedWhilePaused && playbackPaused) || !javascriptReady) return
        var dispatched = false
        while (true) {
            when (val packet = pendingPackets.poll() ?: break) {
                is ShowdownMoveEffectsQueue.Packet.Seed -> evaluateJavascript(
                    "window.ShowdownNativeEffects.seed(${JSONArray(packet.lines)}, ${packet.effectsBarrierToken});",
                    null
                ).also { dispatched = true }
                is ShowdownMoveEffectsQueue.Packet.Receive -> evaluateJavascript(
                    "window.ShowdownNativeEffects.receive(${JSONArray(packet.lines)}, ${packet.battleLogGeneration}, ${packet.synchronizeBattleLog}, ${protocolLineIdentitiesJson(packet.identitiesByLine)}, ${packet.effectsBarrierToken});",
                    null
                ).also { dispatched = true }
            }
        }
        if (dispatched) scheduleNativeIdlePause()
    }

    private fun scheduleNativeIdlePause() {
        mainHandler.removeCallbacks(nativeIdlePauseRunnable)
        if (!released && javascriptReady && !playbackPaused) {
            mainHandler.postDelayed(nativeIdlePauseRunnable, NATIVE_IDLE_CHECK_DELAY_MILLIS)
        }
    }

    private fun runJavascript(script: String) {
        if (javascriptReady) evaluateJavascript(script, null)
    }

    private fun markJavascriptReady() {
        if (released || javascriptReady) return
        javascriptReady = true
        initializeJavascriptIfReady()
    }

    private fun initializeJavascriptIfReady() {
        if (released || !pageLoaded || !javascriptReady) return
        runJavascript("window.ShowdownNativeEffects.setSpeed($playbackSpeed);")
        runJavascript("window.ShowdownNativeEffects.setPerspective('$battlePerspective');")
        if (animationsDisabledForTesting) {
            runJavascript("window.ShowdownNativeEffects.setAnimationsDisabledForTesting(true);")
        }
        if (playbackPaused) runJavascript("window.ShowdownNativeEffects.pause();")
        flushPendingPackets()
    }

    private fun protocolLineIdentitiesJson(identitiesByLine: List<BattleSession.ProtocolLineIdentity>) = JSONArray().apply {
        identitiesByLine.forEach { identity ->
            put(JSONArray().apply {
                put(identity.eventId)
                put(JSONArray().apply { identity.messageIds.forEach(::put) })
            })
        }
    }

    private fun cleanupOnMainThread() {
        if (cleanupCompleted) return
        cleanupCompleted = true
        mainHandler.removeCallbacks(releaseFallbackRunnable)
        pageLoaded = false
        javascriptReady = false
        stopLoading()
        destroy()
    }

    private companion object {
        const val BASE_URL = "https://play.pokemonshowdown.com/"
        const val NATIVE_AUDIO_BRIDGE = "ShowdownNativeAudio"
        const val NATIVE_BATTLE_LOG_BRIDGE = "ShowdownNativeBattleLog"
        const val NATIVE_IDLE_CHECK_DELAY_MILLIS = 350L
        const val RELEASE_FALLBACK_DELAY_MILLIS = 250L
    }

    private class NativeAudioBridge(
        callback: (BattleAudioCue) -> Unit,
        private val resetAudio: () -> Unit,
        private val resetMoveAudio: () -> Unit,
        private val announcerCallback: (BattleAnnouncerCue) -> Unit,
        private val resetAnnouncer: () -> Unit
    ) {
        private val cueSequencer = BattleAudioCueSequencer(callback)

        @JavascriptInterface
        fun battleStarted() {
            cueSequencer.reset()
            resetAudio()
            resetAnnouncer()
            announcerCallback(BattleAnnouncerCue.BATTLE_START)
        }

        @JavascriptInterface
        fun battleRecovered() {
            cueSequencer.reset()
            resetAudio()
            resetMoveAudio()
            resetAnnouncer()
        }

        @JavascriptInterface
        fun moveStarted() {
            resetMoveAudio()
            cueSequencer.beginMove()
        }

        @JavascriptInterface
        fun cue(value: String) {
            BattleAudioCueResolver.cueForNativeValue(value)?.let { cue ->
                cueSequencer.receive(cue)
            }
        }

        @JavascriptInterface
        fun announcer(value: String) {
            BattleAnnouncerCueResolver.cueForNativeValue(value)?.let(announcerCallback)
        }

    }

    private class NativeBattleLogBridge(
        private val callback: (String, Long, Long?, List<Long>) -> Unit,
        private val markupCallback: (String, String, Long) -> Unit,
        private val syncCallback: (Long) -> Unit,
        private val effectsIdleCallback: (Long) -> Unit,
        private val readyCallback: () -> Unit
    ) {
        @JavascriptInterface
        fun ready() {
            readyCallback()
        }

        @JavascriptInterface
        fun entry(value: String, generation: Long, protocolLineIdentityJson: String) {
            val entries = ShowdownBattleLogFilter.visibleEntries(value)
            if (entries.isNotEmpty()) {
                val protocolLineIdentity = runCatching<Pair<Long?, List<Long>>> {
                    val identity = JSONArray(protocolLineIdentityJson)
                    val eventId = identity.getLong(0).takeIf { it > 0L }
                    val messageIds = identity.optJSONArray(1)?.let { ids ->
                        List(ids.length()) { index -> ids.getLong(index) }
                    }.orEmpty()
                    eventId to messageIds
                }.getOrElse { null to emptyList() }
                callback(
                    entries.joinToString("<br />"),
                    generation,
                    protocolLineIdentity.first,
                    protocolLineIdentity.second
                )
            }
        }

        @JavascriptInterface
        fun markup(key: String, value: String, generation: Long) {
            markupCallback(key, value, generation)
        }

        @JavascriptInterface
        fun synced(generation: Long) {
            syncCallback(generation)
        }

        @JavascriptInterface
        fun effectsIdle(token: Long) {
            effectsIdleCallback(token)
        }
    }
}
