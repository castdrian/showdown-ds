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
    private val audioMoveResetter: () -> Unit = {},
    private val battleLogListener: (String) -> Unit = {}
) : WebView(context) {
    private val pendingPackets = ShowdownMoveEffectsQueue()
    private var pageLoaded = false
    private var playbackPaused = false
    private var playbackSpeed = 1f
    private var released = false
    private val nativeAudioBridge = NativeAudioBridge(audioCueListener, audioCueResetter, audioMoveResetter)
    private val nativeBattleLogBridge = NativeBattleLogBridge(battleLogListener)

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
        addJavascriptInterface(nativeBattleLogBridge, NATIVE_BATTLE_LOG_BRIDGE)
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
        const val NATIVE_BATTLE_LOG_BRIDGE = "ShowdownNativeBattleLog"
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
                        var captureNativeBattleLog = true;
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
                        function clearNativeCueTimers(scene) {
                            if (!scene || !scene.__showdownNativeCueTimers) return;
                            scene.__showdownNativeCueTimers.forEach(function (timer) {
                                clearTimeout(timer);
                            });
                            scene.__showdownNativeCueTimers = [];
                        }
                        function scheduleNativeCue(scene, value) {
                            if (!scene || !scene.animating || scene.__showdownNativeAudioSilent) return;
                            if (!scene.__showdownNativeCueTimers) scene.__showdownNativeCueTimers = [];
                            var delay = Math.max(0, Number(scene.timeOffset) || 0);
                            var timer = setTimeout(function () {
                                var timers = scene.__showdownNativeCueTimers || [];
                                var timerIndex = timers.indexOf(timer);
                                if (timerIndex >= 0) timers.splice(timerIndex, 1);
                                if (scene.animating && !scene.__showdownNativeAudioSilent) nativeCue(value);
                            }, delay);
                            scene.__showdownNativeCueTimers.push(timer);
                        }
                        function nativeBattleLog(value) {
                            if (!captureNativeBattleLog || !window.ShowdownNativeBattleLog || !value) return;
                            window.ShowdownNativeBattleLog.entry(String(value));
                        }
                        function installBattleLogHooks() {
                            if (typeof BattleLog === 'undefined' || BattleLog.prototype.__showdownNativeBattleLogHooked) return;
                            var originalAddDiv = BattleLog.prototype.addDiv;
                            BattleLog.prototype.addDiv = function (className, html) {
                                nativeBattleLog(html);
                                return originalAddDiv.apply(this, arguments);
                            };
                            var originalAddBattleMessage = BattleLog.prototype.addBattleMessage;
                            BattleLog.prototype.addBattleMessage = function (args, kwArgs) {
                                if (args && args[0] === 'turn') {
                                    nativeBattleLog('Turn ' + args[1]);
                                }
                                return originalAddBattleMessage.apply(this, arguments);
                            };
                            BattleLog.prototype.__showdownNativeBattleLogHooked = true;
                        }
                        function installAudioHooks() {
                            if (BattleScene.prototype.__showdownNativeAudioHooked) return;
                            function moveCanDamage(move) {
                                if (!move || move.category === 'Status') return false;
                                var basePower = Number(move.basePower);
                                var hasFixedDamage = move.damage !== undefined && move.damage !== null && move.damage !== 0 && move.damage !== false;
                                var hasDamageCallback = typeof move.damageCallback === 'function';
                                return (isFinite(basePower) && basePower > 0) || hasFixedDamage || hasDamageCallback;
                            }
                            var originalUseMove = Battle.prototype.useMove;
                            Battle.prototype.useMove = function (pokemon, move) {
                                clearNativeCueTimers(this.scene);
                                nativeMoveStarted();
                                this.scene.__showdownNativeDamageArmed = moveCanDamage(move);
                                this.scene.__showdownNativeDamageWindow = this.scene.__showdownNativeDamageArmed;
                                this.scene.__showdownNativeDamagePlayed = false;
                                this.scene.__showdownNativeHealthEvents = [];
                                this.scene.__showdownNativeResultCues = [];
                                return originalUseMove.apply(this, arguments);
                            };
                            var originalRunMajor = Battle.prototype.runMajor;
                            Battle.prototype.runMajor = function (args) {
                                if (!args || args[0] !== 'move') {
                                    this.scene.__showdownNativeDamageArmed = false;
                                    this.scene.__showdownNativeDamageWindow = false;
                                }
                                return originalRunMajor.apply(this, arguments);
                            };
                            var originalStopAnimation = BattleScene.prototype.stopAnimation;
                            BattleScene.prototype.stopAnimation = function () {
                                clearNativeCueTimers(this);
                                return originalStopAnimation.apply(this, arguments);
                            };
                            var originalResultAnim = BattleScene.prototype.resultAnim;
                            BattleScene.prototype.resultAnim = function () {
                                var shouldCueResult = this.animating && !this.__showdownNativeAudioSilent && this.__showdownNativeResultCues && this.__showdownNativeResultCues.length;
                                var resultCue = shouldCueResult ? this.__showdownNativeResultCues.shift() : null;
                                if (resultCue) {
                                    scheduleNativeCue(this, resultCue);
                                }
                                return originalResultAnim.apply(this, arguments);
                            };
                            var originalDamageAnim = BattleScene.prototype.damageAnim;
                            BattleScene.prototype.damageAnim = function () {
                                var healthEvent = this.__showdownNativeHealthEvents && this.__showdownNativeHealthEvents.length ? this.__showdownNativeHealthEvents.shift() : null;
                                var shouldCueDamage = healthEvent === 'damage' && this.animating && !this.__showdownNativeAudioSilent && this.__showdownNativeDamageArmed && !this.__showdownNativeDamagePlayed;
                                if (shouldCueDamage) {
                                    this.__showdownNativeDamagePlayed = true;
                                    scheduleNativeCue(this, 'generic_damage');
                                }
                                return originalDamageAnim.apply(this, arguments);
                            };
                            var originalHealAnim = BattleScene.prototype.healAnim;
                            BattleScene.prototype.healAnim = function () {
                                if (this.__showdownNativeHealthEvents && this.__showdownNativeHealthEvents.length) this.__showdownNativeHealthEvents.shift();
                                return originalHealAnim.apply(this, arguments);
                            };
                            var originalRunMinor = Battle.prototype.runMinor;
                            function setHpValue(pokemon, health) {
                                var hp = String(health || '').split(' ')[0];
                                if (hp === '0' || hp === '0.0') return 0;
                                var slashIndex = hp.indexOf('/');
                                if (slashIndex > 0) {
                                    var absoluteHp = parseFloat(hp.slice(0, slashIndex));
                                    return isFinite(absoluteHp) ? absoluteHp : null;
                                }
                                var percentage = parseFloat(hp.replace('%', ''));
                                return pokemon && isFinite(percentage) ? (pokemon.maxhp || 100) * percentage / 100 : null;
                            }
                            function isDirectMoveDamage(source) {
                                var normalized = String(source || '').trim().toLowerCase();
                                return !normalized;
                            }
                            function keepsDirectMoveDamageWindow(args) {
                                return args[0] === '-crit' || args[0] === '-supereffective' || args[0] === '-resisted';
                            }
                            Battle.prototype.runMinor = function (args) {
                                var resultCue = null;
                                var kwArgs = arguments[1] || {};
                                var previousAudioSilent = !!this.scene.__showdownNativeAudioSilent;
                                var directMoveDamage = this.scene.__showdownNativeDamageWindow && isDirectMoveDamage(kwArgs.from);
                                if (this.scene.__showdownNativeDamageWindow && args[0] !== '-damage' && args[0] !== '-sethp' && !keepsDirectMoveDamageWindow(args)) {
                                    this.scene.__showdownNativeDamageWindow = false;
                                }
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
                                    if (!this.scene.__showdownNativeHealthEvents) this.scene.__showdownNativeHealthEvents = [];
                                    if (args[0] === '-damage') {
                                        this.scene.__showdownNativeHealthEvents.push(directMoveDamage ? 'damage' : 'other');
                                        this.scene.__showdownNativeDamageWindow = false;
                                    }
                                    if (args[0] === '-heal') this.scene.__showdownNativeHealthEvents.push('heal');
                                    if (args[0] === '-sethp' && directMoveDamage) {
                                        for (var setHpIndex = 1; setHpIndex + 1 < args.length; setHpIndex += 2) {
                                            var setHpTarget = this.getPokemon(args[setHpIndex]);
                                            var nextHp = setHpValue(setHpTarget, args[setHpIndex + 1]);
                                            if (setHpTarget && nextHp !== null) this.scene.__showdownNativeHealthEvents.push(nextHp < setHpTarget.hp ? 'damage' : nextHp > setHpTarget.hp ? 'heal' : 'other');
                                        }
                                        this.scene.__showdownNativeDamageWindow = false;
                                    }
                                }
                                var result = originalRunMinor.apply(this, arguments);
                                this.scene.__showdownNativeAudioSilent = previousAudioSilent;
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
                            installBattleLogHooks();
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
                                scene.acceleration = animationSpeed;
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
                                captureNativeBattleLog = false;
                                battle.scene.animationOff();
                                add(lines);
                                battle.play();
                                battle.scene.animationOn();
                            },
                            receive: function (lines) {
                                if (!battle || lines.some(function (line) { return line.indexOf('|init|battle') === 0; })) createBattle();
                                captureNativeBattleLog = true;
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

    private class NativeBattleLogBridge(
        private val callback: (String) -> Unit
    ) {
        @JavascriptInterface
        fun entry(value: String) {
            val entries = ShowdownBattleLogFilter.visibleEntries(value)
            if (entries.isNotEmpty()) callback(entries.joinToString("<br />"))
        }
    }
}
