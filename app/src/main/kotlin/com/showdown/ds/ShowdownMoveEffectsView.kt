package com.showdown.ds

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import org.json.JSONArray
import java.util.ArrayDeque

@SuppressLint("SetJavaScriptEnabled")
class ShowdownMoveEffectsView(
    context: Context,
    private val audioCueListener: (List<String>) -> Unit
) : WebView(context) {
    private val pendingPackets = ArrayDeque<List<String>>()
    private var flushScheduled = false
    private var pageLoaded = false
    private var playbackPaused = false
    private var playbackSpeed = 1f
    private var scheduledPauseMillis = 0L
    private var scheduledAtMillis = 0L
    private var scheduledSpeed = 1f
    private var playbackPausedRemainingMillis: Long? = null
    private val flushRunnable = Runnable {
        flushScheduled = false
        flushPendingPackets()
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageLoaded = true
                runJavascript("window.ShowdownNativeEffects.setSpeed($playbackSpeed);")
                if (playbackPaused) runJavascript("window.ShowdownNativeEffects.pause();")
                flushPendingPackets()
            }
        }
        loadDataWithBaseURL(BASE_URL, DOCUMENT, "text/html", "UTF-8", null)
    }

    fun seed(lines: List<String>) {
        val packet = lines.filter { it.startsWith('|') }
        if (packet.isEmpty()) return
        pendingPackets.addLast(listOf(SEED_PREFIX) + packet)
        flushPendingPackets()
    }

    fun applyProtocol(lines: List<String>) {
        val packet = lines.filter { it.startsWith('|') }
        if (packet.isEmpty()) return
        if (packet.any { it.startsWith("|init|battle") }) {
            pendingPackets.clear()
            removeCallbacks(flushRunnable)
            flushScheduled = false
            playbackPausedRemainingMillis = null
            scheduledPauseMillis = 0L
            scheduledAtMillis = 0L
            scheduledSpeed = playbackSpeed
        }
        BattlePlaybackTiming.chunks(packet).forEach(pendingPackets::addLast)
        flushPendingPackets()
    }

    fun setPlaybackPaused(paused: Boolean) {
        if (playbackPaused == paused) return
        playbackPaused = paused
        if (paused) {
            playbackPausedRemainingMillis = remainingPauseMillis()
            removeCallbacks(flushRunnable)
            flushScheduled = false
            runJavascript("window.ShowdownNativeEffects.pause();")
        } else {
            runJavascript("window.ShowdownNativeEffects.resume();")
            playbackPausedRemainingMillis?.let { remainingMillis ->
                playbackPausedRemainingMillis = null
                scheduleFlush(remainingMillis)
            } ?: flushPendingPackets()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val nextSpeed = speed.coerceIn(0.25f, 4f)
        if (nextSpeed == playbackSpeed) return
        val remainingMillis = if (flushScheduled && !playbackPaused) remainingPauseMillis() else null
        if (remainingMillis != null) {
            removeCallbacks(flushRunnable)
            flushScheduled = false
        }
        playbackSpeed = nextSpeed
        runJavascript("window.ShowdownNativeEffects.setSpeed($playbackSpeed);")
        if (remainingMillis != null) scheduleFlush(remainingMillis)
    }

    fun release() {
        pendingPackets.clear()
        removeCallbacks(flushRunnable)
        flushScheduled = false
        playbackPausedRemainingMillis = null
        stopLoading()
        destroy()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun flushPendingPackets() {
        if (playbackPaused || !pageLoaded || pendingPackets.isEmpty() || flushScheduled) return
        val packet = pendingPackets.removeFirst()
        val payload = JSONArray(packet)
        val receiver = if (packet.firstOrNull() == SEED_PREFIX) "seed" else "receive"
        val lines = if (receiver == "seed") JSONArray(packet.drop(1)) else payload
        if (receiver != "seed") audioCueListener(packet.toList())
        evaluateJavascript("window.ShowdownNativeEffects.$receiver($lines);", null)
        val pauseMillis = if (receiver == "seed") 0L else BattlePlaybackTiming.pauseAfter(packet)
        scheduleFlush(pauseMillis)
    }

    private fun scheduleFlush(pauseMillis: Long) {
        flushScheduled = true
        scheduledPauseMillis = pauseMillis
        scheduledAtMillis = SystemClock.elapsedRealtime()
        scheduledSpeed = playbackSpeed
        if (pauseMillis == 0L) {
            post(flushRunnable)
        } else {
            postDelayed(flushRunnable, BattlePlaybackTiming.scaledPause(pauseMillis, playbackSpeed))
        }
    }

    private fun remainingPauseMillis(): Long? {
        if (!flushScheduled) return null
        val elapsedMillis = (SystemClock.elapsedRealtime() - scheduledAtMillis).coerceAtLeast(0L)
        val consumedMillis = (elapsedMillis * scheduledSpeed).toLong()
        return (scheduledPauseMillis - consumedMillis).coerceAtLeast(0L)
    }

    private fun runJavascript(script: String) {
        if (pageLoaded) evaluateJavascript(script, null)
    }

    private companion object {
        const val BASE_URL = "https://play.pokemonshowdown.com/"
        const val SEED_PREFIX = "__seed__"
        val DOCUMENT = """
            <!doctype html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                <link rel="stylesheet" href="https://play.pokemonshowdown.com/style/battle.css">
                <style>
                    html, body, #stage { margin: 0; width: 100%; height: 100%; overflow: hidden; background: transparent !important; }
                    #battle { position: absolute; top: 0; left: 0; width: 640px; height: 360px; transform-origin: top left; overflow: hidden; border: 0; background: transparent !important; }
                    #log { display: none; }
                    .battle, .innerbattle { border: 0 !important; background: transparent !important; }
                    .native-effects-hidden { visibility: hidden !important; }
                </style>
                <script src="https://play.pokemonshowdown.com/config/config.js"></script>
                <script src="https://play.pokemonshowdown.com/js/lib/jquery-2.2.4.min.js"></script>
                <script src="https://play.pokemonshowdown.com/js/battle-sound.js"></script>
                <script src="https://play.pokemonshowdown.com/js/battledata.js"></script>
                <script src="https://play.pokemonshowdown.com/data/pokedex-mini.js"></script>
                <script src="https://play.pokemonshowdown.com/data/typechart.js"></script>
                <script src="https://play.pokemonshowdown.com/js/battle-animations.js"></script>
                <script src="https://play.pokemonshowdown.com/js/battle-animations-moves.js"></script>
                <script src="https://play.pokemonshowdown.com/js/battle.js"></script>
                <script src="https://play.pokemonshowdown.com/js/battle-tooltips.js"></script>
            </head>
            <body>
                <div id="stage"><div id="battle"></div></div><div id="log"></div>
                <script>
                    (function () {
                        var battle = null;
                        var animationSpeed = 1;
                        var hideFrame = 0;
                        function layout() {
                            var width = window.innerWidth;
                            var height = window.innerHeight;
                            var scale = Math.min(width / 640, height / 360);
                            var left = (width - 640 * scale) / 2;
                            var top = (height - 360 * scale) / 2;
                            document.getElementById('battle').style.transform = 'translate(' + left + 'px,' + top + 'px) scale(' + scale + ')';
                        }
                        function hideChrome() {
                            var scene = battle.scene;
                            [scene.${'$'}bg, scene.${'$'}terrain, scene.${'$'}weather, scene.${'$'}sprite, scene.${'$'}stat, scene.${'$'}leftbar, scene.${'$'}rightbar, scene.${'$'}turn, scene.${'$'}messagebar, scene.${'$'}delay, scene.${'$'}tooltips].forEach(function (element) {
                                element.addClass('native-effects-hidden').css('visibility', 'hidden');
                            });
                            scene.${'$'}sprites.concat(scene.${'$'}spritesFront).forEach(function (element) {
                                element.addClass('native-effects-hidden').css('visibility', 'hidden');
                            });
                        }
                        function keepChromeHidden() {
                            if (!battle) return;
                            hideChrome();
                            hideFrame = window.requestAnimationFrame(keepChromeHidden);
                        }
                        function createBattle() {
                            if (battle) battle.destroy();
                            if (hideFrame) window.cancelAnimationFrame(hideFrame);
                            document.getElementById('battle').innerHTML = '';
                            document.getElementById('log').innerHTML = '';
                            battle = new Battle({ id: 'showdownds', ${'$'}frame: jQuery('#battle'), ${'$'}logFrame: jQuery('#log') });
                            battle.setMute(true);
                            var scene = battle.scene;
                            var updateAcceleration = scene.updateAcceleration;
                            scene.updateAcceleration = function () {
                                updateAcceleration.call(scene);
                                scene.acceleration *= animationSpeed;
                            };
                            scene.acceleration = animationSpeed;
                            hideChrome();
                            layout();
                            keepChromeHidden();
                        }
                        function add(lines) {
                            lines.forEach(function (line) {
                                if (line.indexOf('|request|') !== 0) battle.add(line);
                            });
                        }
                        window.addEventListener('resize', layout);
                        window.ShowdownNativeEffects = {
                            seed: function (lines) {
                                createBattle();
                                battle.scene.animationOff();
                                add(lines);
                                battle.play();
                                battle.scene.animationOn();
                            },
                            receive: function (lines) {
                                if (!battle || lines.some(function (line) { return line.indexOf('|init|battle') === 0; })) createBattle();
                                add(lines);
                                if (battle.paused) battle.play();
                            },
                            setSpeed: function (speed) {
                                animationSpeed = Math.max(0.25, Math.min(4, Number(speed) || 1));
                                if (battle) battle.scene.acceleration = animationSpeed;
                            },
                            pause: function () {
                                if (battle) battle.pause();
                            },
                            resume: function () {
                                if (battle && battle.paused) battle.play();
                            }
                        };
                    }());
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
