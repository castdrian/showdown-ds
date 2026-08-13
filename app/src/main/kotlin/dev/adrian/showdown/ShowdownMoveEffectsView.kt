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
import org.json.JSONArray

@SuppressLint("SetJavaScriptEnabled")
class ShowdownMoveEffectsView(
    context: Context,
    private val audioCueListener: (BattleAudioCue) -> Unit,
    private val audioCueResetter: () -> Unit = {},
    private val protocolHistoryProvider: () -> List<String>,
    private val audioMoveResetter: () -> Unit = {}
) : WebView(context) {
    private val pendingPackets = ShowdownMoveEffectsQueue()
    private var pageLoaded = false
    private var playbackPaused = false
    private var playbackSpeed = 1f
    private var released = false
    private val nativeAudioBridge = NativeAudioBridge(audioCueListener, audioCueResetter, audioMoveResetter)

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
        addJavascriptInterface(nativeAudioBridge, NATIVE_AUDIO_BRIDGE)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (released) return
                pageLoaded = true
                runJavascript("window.ShowdownNativeEffects.setSpeed($playbackSpeed);")
                seed(protocolHistoryProvider())
                if (playbackPaused) runJavascript("window.ShowdownNativeEffects.pause();")
                flushPendingPackets()
            }
        }
        loadDataWithBaseURL(BASE_URL, DOCUMENT, "text/html", "UTF-8", null)
    }

    fun seed(lines: List<String>) {
        val packet = lines.filter { it.startsWith('|') }
        pendingPackets.resetWith(packet)
        if (packet.isNotEmpty()) flushPendingPackets(allowSeedWhilePaused = true)
    }

    fun applyProtocol(lines: List<String>) {
        val packet = lines.filter { it.startsWith('|') }
        if (packet.isEmpty()) return
        if (packet.any { it.startsWith("|init|battle") }) {
            pendingPackets.clear()
        }
        BattlePlaybackTiming.chunks(packet).forEach(pendingPackets::add)
        flushPendingPackets()
    }

    fun setPlaybackPaused(paused: Boolean) {
        if (playbackPaused == paused) return
        playbackPaused = paused
        if (paused) {
            runJavascript("window.ShowdownNativeEffects.pause();")
        } else {
            runJavascript("window.ShowdownNativeEffects.resume();")
            flushPendingPackets()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val nextSpeed = speed.coerceIn(0.25f, 4f)
        if (nextSpeed == playbackSpeed) return
        playbackSpeed = nextSpeed
        runJavascript("window.ShowdownNativeEffects.setSpeed($playbackSpeed);")
    }

    fun release() {
        if (released) return
        released = true
        pendingPackets.clear()
        val cleanup = {
            pageLoaded = false
            stopLoading()
            destroy()
        }
        if (pageLoaded) {
            evaluateJavascript("window.ShowdownNativeEffects.release();") { cleanup() }
        } else {
            cleanup()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun flushPendingPackets(allowSeedWhilePaused: Boolean = false) {
        if ((!allowSeedWhilePaused && playbackPaused) || !pageLoaded) return
        while (true) {
            when (val packet = pendingPackets.poll() ?: break) {
                is ShowdownMoveEffectsQueue.Packet.Seed -> evaluateJavascript(
                    "window.ShowdownNativeEffects.seed(${JSONArray(packet.lines)});",
                    null
                )
                is ShowdownMoveEffectsQueue.Packet.Receive -> evaluateJavascript(
                    "window.ShowdownNativeEffects.receive(${JSONArray(packet.lines)});",
                    null
                )
            }
        }
    }

    private fun runJavascript(script: String) {
        if (pageLoaded) evaluateJavascript(script, null)
    }

    private companion object {
        const val BASE_URL = "https://play.pokemonshowdown.com/"
        const val NATIVE_AUDIO_BRIDGE = "ShowdownNativeAudio"
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
                    .result { display: none !important; }
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
                        var chromeObserver = null;
                        function nativeCue(value) {
                            if (window.ShowdownNativeAudio) window.ShowdownNativeAudio.cue(value);
                        }
                        function nativeMoveStarted() {
                            if (window.ShowdownNativeAudio) window.ShowdownNativeAudio.moveStarted();
                        }
                        function nativeBattleStarted() {
                            if (window.ShowdownNativeAudio) window.ShowdownNativeAudio.battleStarted();
                        }
                        function installAudioHooks() {
                            if (BattleScene.prototype.__showdownNativeAudioHooked) return;
                            var originalRunMoveAnim = BattleScene.prototype.runMoveAnim;
                            BattleScene.prototype.runMoveAnim = function (moveid, participants) {
                                return originalRunMoveAnim.apply(this, arguments);
                            };
                            var originalUseMove = Battle.prototype.useMove;
                            Battle.prototype.useMove = function (pokemon, move) {
                                nativeMoveStarted();
                                this.scene.__showdownNativeDamageArmed = !!move && move.category !== 'Status';
                                this.scene.__showdownNativeDamagePlayed = false;
                                this.scene.__showdownNativeDamagePending = false;
                                this.scene.__showdownNativeResultCues = [];
                                return originalUseMove.apply(this, arguments);
                            };
                            var originalResultAnim = BattleScene.prototype.resultAnim;
                            BattleScene.prototype.resultAnim = function () {
                                if (this.animating && !this.__showdownNativeAudioSilent && this.__showdownNativeResultCues && this.__showdownNativeResultCues.length) {
                                    var cue = this.__showdownNativeResultCues.shift();
                                    nativeCue(cue);
                                }
                                return originalResultAnim.apply(this, arguments);
                            };
                            var originalDamageAnim = BattleScene.prototype.damageAnim;
                            BattleScene.prototype.damageAnim = function () {
                                if (this.animating && !this.__showdownNativeAudioSilent && this.__showdownNativeDamagePending && this.__showdownNativeDamageArmed && !this.__showdownNativeDamagePlayed) {
                                    this.__showdownNativeDamagePlayed = true;
                                    this.__showdownNativeDamagePending = false;
                                    nativeCue('generic_damage');
                                }
                                return originalDamageAnim.apply(this, arguments);
                            };
                            var originalHealAnim = BattleScene.prototype.healAnim;
                            BattleScene.prototype.healAnim = function () {
                                this.__showdownNativeDamagePending = false;
                                return originalHealAnim.apply(this, arguments);
                            };
                            var originalRunMinor = Battle.prototype.runMinor;
                            Battle.prototype.runMinor = function (args) {
                                var resultCue = null;
                                var kwArgs = arguments[1] || {};
                                this.scene.__showdownNativeAudioSilent = !!kwArgs.silent;
                                if (!this.scene.__showdownNativeResultCues) this.scene.__showdownNativeResultCues = [];
                                if (this.scene.animating && !kwArgs.silent) {
                                    var magnitude = Number(args[3]);
                                    if (args[0] === '-supereffective') resultCue = 'super_effective';
                                    if (args[0] === '-resisted') resultCue = 'not_very_effective';
                                    if (args[0] === '-boost' && magnitude !== 0) resultCue = magnitude > 0 ? 'stat_boost' : 'stat_drop';
                                    if (args[0] === '-unboost' && magnitude !== 0) resultCue = magnitude > 0 ? 'stat_drop' : 'stat_boost';
                                    if (args[0] === '-setboost' && magnitude !== 0) resultCue = magnitude > 0 ? 'stat_boost' : 'stat_drop';
                                    if (args[0] === '-clearpositiveboost') resultCue = 'stat_drop';
                                    if (args[0] === '-clearnegativeboost') resultCue = 'stat_boost';
                                    if (resultCue) this.scene.__showdownNativeResultCues.push(resultCue);
                                    var directDamage = args[0] === '-damage' && !kwArgs.from;
                                    if (args[0] === '-sethp' && !kwArgs.from) {
                                        for (var setHpIndex = 1; setHpIndex + 1 < args.length; setHpIndex += 2) {
                                            var setHpTarget = this.getPokemon(args[setHpIndex]);
                                            var setHpChange = setHpTarget && setHpTarget.healthParse(args[setHpIndex + 1]);
                                            if (setHpChange && setHpChange[0] < 0) directDamage = true;
                                        }
                                    }
                                    if (directDamage && this.scene.__showdownNativeDamageArmed) this.scene.__showdownNativeDamagePending = true;
                                }
                                var result = originalRunMinor.apply(this, arguments);
                                this.scene.__showdownNativeAudioSilent = false;
                                return result;
                            };
                            BattleScene.prototype.__showdownNativeAudioHooked = true;
                        }
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
                        function observeChrome() {
                            if (chromeObserver) chromeObserver.disconnect();
                            chromeObserver = new MutationObserver(function () {
                                if (battle) hideChrome();
                            });
                            chromeObserver.observe(document.getElementById('battle'), { childList: true, subtree: true });
                            hideChrome();
                        }
                        function createBattle() {
                            nativeBattleStarted();
                            installAudioHooks();
                            if (chromeObserver) {
                                chromeObserver.disconnect();
                                chromeObserver = null;
                            }
                            if (battle) battle.destroy();
                            document.getElementById('battle').innerHTML = '';
                            document.getElementById('log').innerHTML = '';
                            battle = new Battle({ id: 'showdownds', paused: true, ${'$'}frame: jQuery('#battle'), ${'$'}logFrame: jQuery('#log') });
                            battle.setMute(true);
                            var scene = battle.scene;
                            var updateAcceleration = scene.updateAcceleration;
                            scene.updateAcceleration = function () {
                                updateAcceleration.call(scene);
                                scene.acceleration *= animationSpeed;
                            };
                            scene.acceleration = animationSpeed;
                            observeChrome();
                            layout();
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
                            },
                            release: function () {
                                if (chromeObserver) {
                                    chromeObserver.disconnect();
                                    chromeObserver = null;
                                }
                                if (battle) {
                                    battle.destroy();
                                    battle = null;
                                }
                            }
                        };
                    }());
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private class NativeAudioBridge(
        callback: (BattleAudioCue) -> Unit,
        private val resetAudio: () -> Unit,
        private val resetMoveAudio: () -> Unit
    ) {
        private val cueSequencer = BattleAudioCueSequencer(callback)

        @JavascriptInterface
        fun battleStarted() {
            cueSequencer.reset()
            resetAudio()
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

    }
}
