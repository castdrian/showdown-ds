package com.showdown.ds

import android.app.Activity
import android.app.AlertDialog
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import java.util.ArrayDeque

class MainActivity : Activity() {
    private var displayManager: DisplayManager? = null
    private var secondaryPresentation: ThorPresentation? = null
    private var battleScene: BattleSceneView? = null
    private var showdownMoveEffects: ShowdownMoveEffectsView? = null
    private var commandDeck: CommandDeckView? = null
    private lateinit var session: BattleSession
    private lateinit var battleAudio: BattleAudio
    private lateinit var spriteCache: ShowdownSpriteCache
    private lateinit var moveDex: ShowdownMoveDex
    private lateinit var serverEndpoint: ShowdownServerEndpoint
    private lateinit var credentialsStore: ShowdownCredentialsStore
    private lateinit var teamLibrary: ShowdownTeamLibrary
    private var showdownConnection: ShowdownConnection? = null
    private val lobbyState = ShowdownLobbyState()
    private val loginClient = ShowdownLoginClient()
    private var pendingSearch = false
    private var pendingSearchTeamPacked: String? = null
    private var pendingLobbyCommands: List<String>? = null
    private var pendingLobbyStatus: String? = null
    private var activeSearchFormat: String? = null
    private var loginInFlight = false
    private var authenticated = false
    private var activeBattleRoomId: String? = null
    private val demoHandler = Handler(Looper.getMainLooper())
    private val battleAudioHandler = Handler(Looper.getMainLooper())
    private val battleEventHandler = Handler(Looper.getMainLooper())
    private val pendingBattleEvents = ArrayDeque<String>()
    private var battleEventPlaybackScheduled = false
    private var demoTurnIndex = 0
    private var controllerHorizontal = 0
    private var controllerVertical = 0
    private val sessionListener = BattleSession.Listener { refreshDisplays() }
    private val battleEventListener = BattleSession.BattleEventListener { events ->
        runOnUiThread {
            pendingBattleEvents.addAll(events)
            flushBattleEventPlayback()
        }
    }
    private val protocolListener = BattleSession.ProtocolListener { lines ->
        runOnUiThread { showdownMoveEffects?.applyProtocol(lines) }
    }
    private val decisionListener = BattleSession.DecisionListener { command ->
        clearBattleEventPlayback()
        val roomId = activeBattleRoomId
        if (roomId != null) {
            showdownConnection?.send(roomId, command)
        } else {
            runOnUiThread { resolveDemoTurn(command) }
        }
    }
    private val chatListener = BattleSession.ChatListener { message ->
        val roomId = activeBattleRoomId
        if (roomId == null || showdownConnection?.send(roomId, message) != true) {
            session.setConnectionStatus("Chat is available once you join a live battle.")
        }
    }
    private val feedbackListener = BattleSession.FeedbackListener { feedback ->
        runOnUiThread { handleBattleFeedback(feedback) }
    }
    private val clientActionListener = BattleSession.ClientActionListener { action ->
        runOnUiThread {
            when (action) {
                BattleSession.ClientAction.FIND_BATTLE -> findBattle()
                BattleSession.ClientAction.CONFIGURE_SERVER -> showServerSettings()
                BattleSession.ClientAction.CONFIGURE_ACCOUNT -> showAccountSettings()
                BattleSession.ClientAction.CONFIGURE_TEAM -> showTeamLibrary()
                BattleSession.ClientAction.CHOOSE_FORMAT -> showFormatPicker()
                BattleSession.ClientAction.OPEN_CHAT -> showChatComposer()
                BattleSession.ClientAction.FORFEIT -> confirmForfeit()
                BattleSession.ClientAction.CHALLENGE_PLAYER -> showChallengeComposer()
            }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            showSecondaryDisplay()
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (secondaryPresentation?.display?.displayId == displayId) dismissSecondaryDisplay()
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplays()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        nativeInitializeVulkan()
        serverEndpoint = loadServerEndpoint()
        credentialsStore = ShowdownCredentialsStore(this)
        teamLibrary = ShowdownTeamLibrary(this)
        session = createDemoSession()
        session.setMatchFormat(loadMatchFormat())
        session.addListener(sessionListener)
        session.addBattleEventListener(battleEventListener)
        session.addProtocolListener(protocolListener)
        session.addDecisionListener(decisionListener)
        session.addChatListener(chatListener)
        session.addFeedbackListener(feedbackListener)
        session.addClientActionListener(clientActionListener)
        spriteCache = ShowdownSpriteCache(this)
        moveDex = ShowdownMoveDex(spriteCache)
        session.setMoveTypeResolver(moveDex::typeFor)
        session.setPokemonTypeResolver(moveDex::typesFor)
        moveDex.load {
            session.setMoveTypeResolver(moveDex::typeFor)
            session.setPokemonTypeResolver(moveDex::typesFor)
        }
        battleAudio = BattleAudio(this, spriteCache, session)
        battleAudio.updateOptions(session)
        battleAudio.preloadMoves(session.moves().map { it.name })
        session.presentBattleEvent(session.sendOutMessage(session.playerPokemon, true))
        battleAudioHandler.postDelayed(
            { session.presentBattleEvent(session.sendOutMessage(session.opponentPokemon, false)) },
            BattleSceneTiming.summonDurationNanos / 1_000_000L
        )
        battleAudioHandler.postDelayed({ battleAudio.playCry(session.playerPokemon) }, 30)
        battleAudioHandler.postDelayed(
            { battleAudio.playCry(session.opponentPokemon) },
            BattleSceneTiming.summonDurationNanos / 1_000_000L + 30L
        )
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        setContentView(createPrimaryScreen())
        displayManager?.registerDisplayListener(displayListener, null)
        showSecondaryDisplay()
    }

    override fun onDestroy() {
        dismissSecondaryDisplay()
        displayManager?.unregisterDisplayListener(displayListener)
        if (::session.isInitialized) session.removeListener(sessionListener)
        if (::session.isInitialized) session.removeBattleEventListener(battleEventListener)
        if (::session.isInitialized) session.removeProtocolListener(protocolListener)
        if (::session.isInitialized) session.removeDecisionListener(decisionListener)
        if (::session.isInitialized) session.removeChatListener(chatListener)
        if (::session.isInitialized) session.removeFeedbackListener(feedbackListener)
        if (::session.isInitialized) session.removeClientActionListener(clientActionListener)
        demoHandler.removeCallbacksAndMessages(null)
        battleAudioHandler.removeCallbacksAndMessages(null)
        clearBattleEventPlayback()
        showdownConnection?.close()
        showdownConnection = null
        if (::battleAudio.isInitialized) battleAudio.release()
        if (::moveDex.isInitialized) moveDex.close()
        if (::spriteCache.isInitialized) spriteCache.close()
        showdownMoveEffects?.release()
        showdownMoveEffects = null
        nativeReleaseVulkan()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0 && isConfirmButton(keyCode)) return true
        return if (handleControllerKey(keyCode)) true else super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && event.action == MotionEvent.ACTION_MOVE) {
            val horizontal = axisDirection(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X)
            val vertical = axisDirection(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)
            if (horizontal != controllerHorizontal || vertical != controllerVertical) {
                controllerHorizontal = horizontal
                controllerVertical = vertical
                if (horizontal != 0 || vertical != 0) navigateController(horizontal, vertical)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onPause() {
        if (::battleAudio.isInitialized) battleAudio.pauseMusic()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::battleAudio.isInitialized && ::session.isInitialized) battleAudio.updateOptions(session)
    }

    private fun configureWindow() {
        window.statusBarColor = 0xFF071329.toInt()
        window.navigationBarColor = 0xFF071329.toInt()
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun createPrimaryScreen(): View {
        val frame = FrameLayout(this)
        val surfaceView = VulkanSurfaceView(this)
        surfaceView.setZOrderOnTop(false)
        frame.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        battleScene = BattleSceneView(this, session, spriteCache)
        frame.addView(battleScene, FrameLayout.LayoutParams(-1, -1))
        showdownMoveEffects = ShowdownMoveEffectsView(this, battleAudio::planMovePresentation, battleAudio::playMove).also { effects ->
            frame.addView(effects, FrameLayout.LayoutParams(-1, -1))
            effects.seed(session.protocolHistory())
        }
        return frame
    }

    private fun showSecondaryDisplay() {
        if (isFinishing || displayManager == null || secondaryPresentation != null) return
        findThorDisplay()?.let { display ->
            secondaryPresentation = ThorPresentation(this, display).also { presentation ->
                presentation.setOnDismissListener { secondaryPresentation = null }
                presentation.show()
            }
        }
    }

    private fun findThorDisplay(): Display? {
        var fallback: Display? = null
        displayManager?.displays?.forEach { display ->
            if (display.displayId == Display.DEFAULT_DISPLAY) return@forEach
            if (display.mode.physicalWidth == 1240 && display.mode.physicalHeight == 1080) return display
            if (fallback == null) fallback = display
        }
        return fallback
    }

    private fun dismissSecondaryDisplay() {
        secondaryPresentation?.dismiss()
        secondaryPresentation = null
    }

    private fun handleControllerKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> navigateController(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> navigateController(1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> navigateController(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> navigateController(0, 1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> confirmController()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> cancelController()
            KeyEvent.KEYCODE_BUTTON_L1 -> cycleController(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> cycleController(1)
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_START -> openPanel(BattleSession.Panel.MENU)
            KeyEvent.KEYCODE_BUTTON_Y -> openPanel(BattleSession.Panel.TEAM)
            KeyEvent.KEYCODE_BUTTON_L2 -> openPanel(BattleSession.Panel.ACTIVITY)
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_SELECT -> openPanel(BattleSession.Panel.MENU)
            KeyEvent.KEYCODE_BUTTON_THUMBR -> cycleGimmick()
            else -> return false
        }
        return true
    }

    private fun isConfirmButton(keyCode: Int) = keyCode == KeyEvent.KEYCODE_BUTTON_A ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER

    private fun axisDirection(event: MotionEvent, primaryAxis: Int, fallbackAxis: Int): Int {
        var value = event.getAxisValue(primaryAxis)
        if (kotlin.math.abs(value) < 0.45f) value = event.getAxisValue(fallbackAxis)
        return when {
            value > 0.45f -> 1
            value < -0.45f -> -1
            else -> 0
        }
    }

    private fun refreshDisplays() {
        if (::battleAudio.isInitialized && ::session.isInitialized) {
            battleAudio.updateOptions(session)
            battleAudio.preloadMoves(session.moves().map { it.name })
        }
        battleScene?.invalidate()
        commandDeck?.invalidate()
    }

    private fun flushBattleEventPlayback() {
        if (battleEventPlaybackScheduled || pendingBattleEvents.isEmpty()) return
        session.presentBattleEvent(pendingBattleEvents.removeFirst())
        battleEventPlaybackScheduled = true
        battleEventHandler.postDelayed({
            battleEventPlaybackScheduled = false
            flushBattleEventPlayback()
        }, BattlePlaybackTiming.EVENT_PAUSE_MILLIS)
    }

    private fun clearBattleEventPlayback() {
        battleEventHandler.removeCallbacksAndMessages(null)
        pendingBattleEvents.clear()
        battleEventPlaybackScheduled = false
    }

    private fun createDemoSession() = BattleSession().apply {
        applyProtocolLine("|init|battle")
        applyProtocolLine("|player|p1|ADRIAN")
        applyProtocolLine("|player|p2|GLADION")
        applyProtocolLine("|tier|[Gen 7] OU")
        applyProtocolLine("|switch|p1a: Decidueye|Decidueye, L50, M|293/293")
        applyProtocolLine("|switch|p2a: Blissey|Blissey, L50, F|100/100")
        applyProtocolLine("|turn|1")
        applyProtocolLine("|request|{\"active\":[{\"moves\":[{\"move\":\"Spirit Shackle\",\"type\":\"Ghost\",\"pp\":10},{\"move\":\"Leaf Blade\",\"type\":\"Grass\",\"pp\":15},{\"move\":\"Brave Bird\",\"type\":\"Flying\",\"pp\":15},{\"move\":\"Sucker Punch\",\"type\":\"Dark\",\"pp\":5}]}]}")
    }

    private fun resolveDemoTurn(command: String) {
        val moveIndex = command.removePrefix("/choose move ").substringBefore(' ').substringBefore('|').toIntOrNull()?.minus(1) ?: return
        val move = session.moves().getOrNull(moveIndex)?.name ?: return
        val turnIndex = demoTurnIndex++
        val playerTarget = session.opponentPokemon
        val opponentTarget = session.playerPokemon
        val playerOutcome = when (move) {
            "Spirit Shackle" -> DemoOutcome(24, null)
            "Leaf Blade" -> DemoOutcome(34, "-crit")
            "Brave Bird" -> DemoOutcome(18, "-resisted")
            "Sucker Punch" -> DemoOutcome(24, null)
            else -> DemoOutcome(20, null)
        }
        val opponentOutcome = when (turnIndex % 3) {
            0 -> DemoMove("Ice Beam", 18, null)
            1 -> DemoMove("Shadow Ball", 32, "-supereffective")
            else -> DemoMove("Psychic", 21, null)
        }
        val opponentHp = reducedCondition(session.opponentHp, playerOutcome.damagePercent)
        val playerHp = reducedCondition(session.playerHp, opponentOutcome.damagePercent)
        val opponentFainted = opponentHp.startsWith("0 ")
        demoHandler.removeCallbacksAndMessages(null)
        demoHandler.postDelayed({
            session.applyProtocolPacket(
                buildList {
                    add("|move|p1a: $opponentTarget|$move|p2a: $playerTarget")
                    add("|-damage|p2a: $playerTarget|$opponentHp")
                    playerOutcome.modifier?.let { add("|$it|p2a: $playerTarget") }
                    if (opponentFainted) {
                        add("|faint|p2a: $playerTarget")
                        add("|win|${session.playerName}")
                    }
                }
            )
        }, 450)
        if (opponentFainted) return
        demoHandler.postDelayed({
            session.applyProtocolPacket(
                buildList {
                    add("|move|p2a: $playerTarget|${opponentOutcome.move}|p1a: $opponentTarget")
                    add("|-damage|p1a: $opponentTarget|$playerHp")
                    opponentOutcome.modifier?.let { add("|$it|p1a: $opponentTarget") }
                }
            )
        }, 3_350)
        demoHandler.postDelayed({ session.applyProtocolLine("|turn|${session.turn + 1}") }, 6_950)
        demoHandler.postDelayed({ session.applyProtocolLine(demoRequest(moveIndex)) }, 7_500)
    }

    private fun demoRequest(selectedMoveIndex: Int): String {
        val moves = session.moves().mapIndexed { index, move ->
            val pp = (move.pp - if (index == selectedMoveIndex) 1 else 0).coerceAtLeast(0)
            "{\"move\":\"${move.name}\",\"type\":\"${move.type}\",\"pp\":$pp,\"maxpp\":${move.maxPp},\"category\":\"${move.category}\",\"basePower\":${move.power.toIntOrNull() ?: 0},\"accuracy\":\"${move.accuracy}\"}"
        }.joinToString(",")
        return "|request|{\"rqid\":${demoTurnIndex + 100},\"active\":[{\"canZMove\":[{}],\"moves\":[$moves]}]}"
    }

    private fun reducedCondition(condition: String, damagePercent: Int): String {
        val hp = condition.substringBefore(' ').split('/', limit = 2)
        val current = hp.getOrNull(0)?.toIntOrNull() ?: 100
        val maximum = hp.getOrNull(1)?.toIntOrNull() ?: 100
        val damage = ((maximum * damagePercent.coerceIn(0, 100)) + 99) / 100
        val remaining = (current - damage).coerceAtLeast(0)
        return if (remaining == 0) "0 fnt" else "$remaining/$maximum"
    }

    private data class DemoOutcome(val damagePercent: Int, val modifier: String?)

    private data class DemoMove(val move: String, val damagePercent: Int, val modifier: String?)

    private fun findBattle() {
        if (activeSearchFormat != null || pendingSearch) {
            cancelActiveSearch()
            return
        }
        val teamOptions = teamLibrary.teams().filter { it.format.equals(session.matchFormat.id, true) }
        if (!session.matchFormat.usesRandomTeams && teamOptions.isEmpty()) {
            session.setConnectionStatus("Save a ${session.matchFormat.label} team before searching.")
            showTeamLibrary()
            return
        }
        if (!session.matchFormat.usesRandomTeams && teamOptions.size > 1) {
            showTeamPicker(teamOptions) {
                pendingSearchTeamPacked = it.packed
                startLobbyConnection()
            }
            return
        }
        pendingSearchTeamPacked = teamOptions.firstOrNull()?.packed?.takeUnless { session.matchFormat.usesRandomTeams }
        startLobbyConnection()
    }

    private fun showTeamPicker(teams: List<ShowdownTeam>, onSelected: (ShowdownTeam) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Choose a team")
            .setSingleChoiceItems(teams.map { "${it.name} · ${it.format}" }.toTypedArray(), -1) { dialog, selected ->
                dialog.dismiss()
                onSelected(teams[selected])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cancelActiveSearch() {
        activeSearchFormat?.let { format ->
            if (lobbyState.isSearching(format)) showdownConnection?.sendGlobal(ShowdownLobbyState.cancelSearchCommand())
            lobbyState.clearSearch(format)
        }
        activeSearchFormat = null
        pendingSearch = false
        pendingSearchTeamPacked = null
        session.setConnectionStatus("Battle search cancelled.")
    }

    private fun startLobbyConnection(lobbyCommands: List<String>? = null, lobbyStatus: String? = null) {
        activeBattleRoomId = null
        clearBattleEventPlayback()
        session.setLiveBattleActive(false)
        showdownConnection?.close()
        pendingSearch = lobbyCommands == null
        pendingLobbyCommands = lobbyCommands
        pendingLobbyStatus = lobbyStatus
        loginInFlight = false
        authenticated = false
        lateinit var connection: ShowdownConnection
        connection = ShowdownConnection(serverEndpoint, object : ShowdownConnection.Listener {
            override fun onConnectionStateChanged(state: ShowdownConnection.State, detail: String) {
                runOnUiThread {
                    if (showdownConnection !== connection) return@runOnUiThread
                    val status = when (state) {
                        ShowdownConnection.State.CONNECTING -> "Connecting to ${serverEndpoint.displayName}…"
                        ShowdownConnection.State.CONNECTED -> {
                            if (credentialsStore.load() == null) "Joining ${serverEndpoint.displayName}…"
                            else "Signing in to ${serverEndpoint.displayName}…"
                        }
                        ShowdownConnection.State.DISCONNECTED -> {
                            pendingSearch = false
                            pendingLobbyCommands = null
                            pendingLobbyStatus = null
                            activeSearchFormat = null
                            loginInFlight = false
                            authenticated = false
                            detail.ifBlank { "Disconnected from ${serverEndpoint.displayName}." }
                        }
                        ShowdownConnection.State.FAILED -> detail.ifBlank { "Could not reach ${serverEndpoint.displayName}." }
                    }
                    session.setConnectionStatus(status)
                }
            }

            override fun onProtocol(roomId: String?, lines: List<String>) {
                runOnUiThread {
                    if (showdownConnection !== connection) return@runOnUiThread
                    lines.mapNotNull(ShowdownAuthentication::userUpdate).firstOrNull()?.let { update ->
                        session.setLocalUsername(update.username)
                        if (credentialsStore.load() == null || update.named) {
                            authenticated = true
                            sendPendingLobbyCommands(connection)
                        }
                    }
                    lines.mapNotNull(ShowdownAuthentication::challenge).firstOrNull()?.let { challenge ->
                        credentialsStore.load()?.takeUnless { loginInFlight }?.let { credentials ->
                            loginInFlight = true
                            loginClient.login(serverEndpoint, credentials, challenge) { result ->
                                runOnUiThread {
                                    if (showdownConnection !== connection) return@runOnUiThread
                                    loginInFlight = false
                                    result.onSuccess {
                                        if (connection.sendGlobal(ShowdownAuthentication.renameCommand(credentials.username, it))) {
                                            session.setConnectionStatus("Finishing sign-in as ${credentials.username}…")
                                        } else {
                                            pendingSearch = false
                                            session.setConnectionStatus("Showdown sign-in could not reach the server.")
                                        }
                                    }
                                    result.onFailure { error ->
                                        pendingSearch = false
                                        session.setConnectionStatus(error.message ?: "Showdown sign-in failed.")
                                    }
                                }
                            }
                        }
                    }
                    lines.mapNotNull(ShowdownAuthentication::serverError).firstOrNull()?.let { error ->
                        loginInFlight = false
                        pendingSearch = false
                        pendingLobbyCommands = null
                        pendingLobbyStatus = null
                        session.setConnectionStatus(error)
                    }
                    if (roomId == null) {
                        session.applyServerFormats(lines)
                        val previousChallenges = lobbyState.incomingChallenges
                        lobbyState.applyProtocol(lines)
                        lobbyState.incomingChallenges.keys.firstOrNull { it !in previousChallenges }?.let { username ->
                            showIncomingChallenge(username, lobbyState.incomingChallenges[username].orEmpty())
                        }
                        if (lobbyState.isSearching(session.matchFormat.id)) {
                            session.setConnectionStatus("Searching ${session.matchFormat.label}…")
                        }
                    }
                    if (roomId?.startsWith("battle-") == true) {
                        if (lines.any { it.startsWith("|init|battle") }) clearBattleEventPlayback()
                        activeBattleRoomId = roomId
                        session.setLiveBattleActive(true)
                        activeSearchFormat = null
                        session.applyProtocolPacket(lines)
                        session.setLiveBattleActive(!session.isBattleFinished())
                    }
                }
            }
        })
        showdownConnection = connection
        connection.connect()
    }

    private fun sendPendingLobbyCommands(connection: ShowdownConnection) {
        if (!authenticated || showdownConnection !== connection) return
        val commands = pendingLobbyCommands ?: if (pendingSearch) {
            if (!session.matchFormat.usesRandomTeams && pendingSearchTeamPacked.isNullOrBlank()) {
                pendingSearch = false
                session.setConnectionStatus("Save a team for ${session.matchFormat.label} before searching.")
                return
            }
            ShowdownLobbyState.searchCommands(session.matchFormat.id, pendingSearchTeamPacked)
        } else {
            return
        }
        val sent = commands.all(connection::sendGlobal)
        if (!sent) {
            session.setConnectionStatus("Could not send the Showdown lobby command.")
            return
        }
        pendingLobbyCommands = null
        val status = pendingLobbyStatus
        pendingSearch = false
        pendingLobbyStatus = null
        if (status != null) {
            session.setConnectionStatus(status)
        } else {
            activeSearchFormat = session.matchFormat.id
            session.setConnectionStatus("Searching ${session.matchFormat.label}…")
        }
    }

    private fun showChallengeComposer() {
        val username = EditText(this).apply {
            hint = "Username"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Challenge player")
            .setView(username)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Challenge") { _, _ -> beginChallenge(username.text.toString()) }
            .show()
    }

    private fun beginChallenge(username: String) {
        val target = username.trim()
        if (target.isBlank()) {
            session.setConnectionStatus("Enter a username to challenge.")
            return
        }
        val teams = teamLibrary.teams().filter { it.format.equals(session.matchFormat.id, true) }
        if (session.matchFormat.usesRandomTeams) {
            startChallenge(target, null)
        } else if (teams.isEmpty()) {
            session.setConnectionStatus("Save a ${session.matchFormat.label} team before challenging.")
            showTeamLibrary()
        } else if (teams.size == 1) {
            startChallenge(target, teams.single().packed)
        } else {
            showTeamPicker(teams) { startChallenge(target, it.packed) }
        }
    }

    private fun startChallenge(username: String, packedTeam: String?) {
        startLobbyConnection(
            ShowdownLobbyState.challengeCommands(username, session.matchFormat.id, packedTeam),
            "Challenge sent to $username."
        )
    }

    private fun showIncomingChallenge(username: String, format: String) {
        AlertDialog.Builder(this)
            .setTitle("Battle challenge")
            .setMessage("$username challenged you to $format.")
            .setNegativeButton("Reject") { _, _ -> sendLobbyCommand(ShowdownLobbyState.rejectChallengeCommand(username), "Challenge rejected.") }
            .setNeutralButton("Ignore", null)
            .setPositiveButton("Accept") { _, _ -> beginAcceptChallenge(username, format) }
            .show()
    }

    private fun beginAcceptChallenge(username: String, format: String) {
        val matchFormat = BattleSession.MatchFormat(format, format)
        val teams = teamLibrary.teams().filter { it.format.equals(format, true) }
        if (matchFormat.usesRandomTeams) {
            sendLobbyCommands(ShowdownLobbyState.acceptChallengeCommands(username, null), "Challenge accepted.")
        } else if (teams.isEmpty()) {
            session.setConnectionStatus("Save a $format team before accepting.")
            showTeamLibrary()
        } else if (teams.size == 1) {
            sendLobbyCommands(ShowdownLobbyState.acceptChallengeCommands(username, teams.single().packed), "Challenge accepted.")
        } else {
            showTeamPicker(teams) { team ->
                sendLobbyCommands(ShowdownLobbyState.acceptChallengeCommands(username, team.packed), "Challenge accepted.")
            }
        }
    }

    private fun sendLobbyCommand(command: String, status: String) {
        sendLobbyCommands(listOf(command), status)
    }

    private fun sendLobbyCommands(commands: List<String>, status: String) {
        val connection = showdownConnection
        if (!authenticated || connection == null || !commands.all(connection::sendGlobal)) {
            session.setConnectionStatus("Connect to Showdown before using lobby challenges.")
            return
        }
        session.setConnectionStatus(status)
    }

    private fun showServerSettings() {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(serverEndpoint.webSocketUrl)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Pokémon Showdown server")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use") { _, _ ->
                val endpoint = ShowdownServerEndpoint.fromInput(input.text.toString())
                if (endpoint == null) {
                    session.setConnectionStatus("Enter a valid ws://, wss://, http://, or https:// server address.")
                } else {
                    serverEndpoint = endpoint
                    getSharedPreferences("showdown", MODE_PRIVATE).edit().putString("server_endpoint", endpoint.webSocketUrl).apply()
                    session.setConnectionStatus("Server set to ${endpoint.displayName}.")
                }
            }
            .show()
    }

    private fun showAccountSettings() {
        val credentials = credentialsStore.load()
        val username = EditText(this).apply {
            hint = "Username"
            setSingleLine(true)
            setText(credentials?.username.orEmpty())
        }
        val password = EditText(this).apply {
            hint = "Password"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(credentials?.password.orEmpty())
        }
        val fields = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(56, 8, 56, 0)
            addView(username)
            addView(password)
        }
        AlertDialog.Builder(this)
            .setTitle("Showdown account")
            .setView(fields)
            .setNeutralButton("Sign out") { _, _ -> signOut() }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = ShowdownCredentials(username.text.toString().trim(), password.text.toString())
                if (value.username.isBlank() || value.password.isBlank()) {
                    session.setConnectionStatus("Enter both a username and password.")
                } else {
                    credentialsStore.save(value)
                    session.setConnectionStatus("Showdown account saved. It will sign in when you connect.")
                }
            }
            .show()
    }

    private fun signOut() {
        credentialsStore.clear()
        showdownConnection?.close()
        showdownConnection = null
        pendingSearch = false
        pendingLobbyCommands = null
        pendingLobbyStatus = null
        activeSearchFormat = null
        activeBattleRoomId = null
        session.setLiveBattleActive(false)
        session.setConnectionStatus("Signed out of Showdown.")
    }

    private fun showTeamLibrary() {
        val teams = teamLibrary.teams()
        val labels = teams.map { "${it.name} · ${it.format}" } + "Add team"
        AlertDialog.Builder(this)
            .setTitle("Team library")
            .setItems(labels.toTypedArray()) { _, selected ->
                if (selected == teams.size) showTeamEditor() else showTeamEditor(teams[selected])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTeamEditor(existing: ShowdownTeam? = null) {
        val name = EditText(this).apply {
            hint = "Team name"
            setSingleLine(true)
            setText(existing?.name.orEmpty())
        }
        val format = EditText(this).apply {
            hint = "Format ID, for example gen9ou"
            setSingleLine(true)
            setText(existing?.format ?: session.matchFormat.id)
        }
        val packed = EditText(this).apply {
            hint = "Packed team string"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setMinLines(4)
            setText(existing?.packed.orEmpty())
        }
        val fields = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(56, 8, 56, 0)
            addView(name)
            addView(format)
            addView(packed)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add team" else "Edit team")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val teamFormat = format.text.toString().trim()
                val teamPacked = packed.text.toString().trim()
                if (teamFormat.isBlank() || teamPacked.isBlank()) {
                    session.setConnectionStatus("Enter a format ID and packed team.")
                } else {
                    teamLibrary.save(name.text.toString(), teamFormat, teamPacked, existing?.id ?: java.util.UUID.randomUUID().toString())
                    session.setConnectionStatus("Saved ${name.text.toString().trim().ifBlank { "Untitled team" }}.")
                }
            }
        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                teamLibrary.remove(existing.id)
                session.setConnectionStatus("Deleted ${existing.name}.")
            }
        }
        builder.show()
    }

    private fun showChatComposer() {
        if (activeBattleRoomId == null) {
            session.setConnectionStatus("Start or join a battle before opening battle chat.")
            return
        }
        val input = EditText(this).apply {
            hint = "Send a message"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Battle chat")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ -> session.sendChat(input.text.toString()) }
            .show()
    }

    private fun showFormatPicker() {
        val formats = session.availableMatchFormats()
        AlertDialog.Builder(this)
            .setTitle("Battle format")
            .setSingleChoiceItems(formats.map { it.label }.toTypedArray(), formats.indexOf(session.matchFormat)) { dialog, selected ->
                val format = formats[selected]
                if (activeSearchFormat != null || pendingSearch) cancelActiveSearch()
                session.setMatchFormat(format)
                getSharedPreferences("showdown", MODE_PRIVATE).edit().putString("match_format", format.id).apply()
                dialog.dismiss()
            }
            .show()
    }

    private fun confirmForfeit() {
        val roomId = activeBattleRoomId
        if (roomId == null) {
            session.setConnectionStatus("There is no live battle to forfeit.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Forfeit battle?")
            .setMessage("This will immediately concede the current battle.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Forfeit") { _, _ ->
                if (showdownConnection?.send(roomId, "/forfeit") == true) {
                    session.setConnectionStatus("Forfeit requested.")
                } else {
                    session.setConnectionStatus("Unable to forfeit the current battle.")
                }
            }
            .show()
    }

    private fun loadServerEndpoint(): ShowdownServerEndpoint {
        val saved = getSharedPreferences("showdown", MODE_PRIVATE).getString("server_endpoint", null)
        return saved?.let(ShowdownServerEndpoint::fromInput) ?: ShowdownServerEndpoint.playShowdown
    }

    private fun loadMatchFormat(): BattleSession.MatchFormat {
        val saved = getSharedPreferences("showdown", MODE_PRIVATE).getString("match_format", null)
        return BattleSession.MatchFormat.defaults.firstOrNull { it.id == saved } ?: BattleSession.MatchFormat.GEN7_RANDOM
    }

    private fun handleBattleFeedback(feedback: BattleSession.BattleFeedback) {
        when (feedback.type) {
            BattleSession.FeedbackType.ENTRY -> {
                if (feedback.delayMillis > 0L) {
                    battleAudioHandler.postDelayed({ session.presentBattleEvent(feedback.message) }, feedback.delayMillis)
                } else {
                    session.presentBattleEvent(feedback.message)
                }
            }
            BattleSession.FeedbackType.POKEMON_CRY -> {
                if (feedback.delayMillis > 0L) {
                    battleAudioHandler.postDelayed({ battleAudio.playCry(feedback.actor) }, feedback.delayMillis)
                } else {
                    battleAudio.playCry(feedback.actor)
                }
            }
            BattleSession.FeedbackType.MOVE -> Unit
            BattleSession.FeedbackType.HIT -> {
                playImpactHaptic(feedback.impact)
            }
        }
    }

    private fun playImpactHaptic(impact: BattleSession.HitImpact) {
        if (!session.hapticsEnabled) return
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return
        val timings = when (impact) {
            BattleSession.HitImpact.RESISTED -> longArrayOf(0, 18)
            BattleSession.HitImpact.NORMAL -> longArrayOf(0, 28)
            BattleSession.HitImpact.SUPER_EFFECTIVE -> longArrayOf(0, 34, 36, 38)
            BattleSession.HitImpact.CRITICAL -> longArrayOf(0, 42, 30, 46)
            BattleSession.HitImpact.SUPER_EFFECTIVE_CRITICAL -> longArrayOf(0, 48, 28, 56, 28, 50)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private fun navigateController(horizontal: Int, vertical: Int) {
        session.moveFocus(horizontal, vertical)
        battleAudio.playNavigation()
    }

    private fun confirmController() {
        session.confirmSelection()
        battleAudio.playConfirm()
    }

    private fun cancelController() {
        session.goBack()
        battleAudio.playCancel()
    }

    private fun cycleController(direction: Int) {
        session.cyclePanel(direction)
        battleAudio.playNavigation()
    }

    private fun cycleGimmick() {
        session.cycleGimmick()
        battleAudio.playNavigation()
    }

    private fun openPanel(panel: BattleSession.Panel) {
        session.selectPanel(panel)
        battleAudio.playNavigation()
    }

    private inner class ThorPresentation(context: Context, display: Display) : Presentation(context, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            configurePresentationWindow(window)
            val frame = FrameLayout(context)
            val surfaceView = VulkanSurfaceView(context)
            surfaceView.setZOrderOnTop(false)
            frame.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
            commandDeck = CommandDeckView(context, session, spriteCache, object : CommandDeckView.InteractionListener {
                override fun onNavigation() {
                    battleAudio.playNavigation()
                }

                override fun onConfirmation() {
                    battleAudio.playConfirm()
                }
            })
            frame.addView(commandDeck, FrameLayout.LayoutParams(-1, -1))
            setContentView(frame)
        }
    }

    private fun configurePresentationWindow(presentationWindow: Window?) {
        presentationWindow ?: return
        presentationWindow.setDimAmount(0f)
        presentationWindow.statusBarColor = 0xFF071329.toInt()
        presentationWindow.navigationBarColor = 0xFF071329.toInt()
        presentationWindow.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        init {
            System.loadLibrary("showdown_vulkan")
        }

        @JvmStatic
        private external fun nativeInitializeVulkan(): Boolean

        @JvmStatic
        private external fun nativeReleaseVulkan()

        @JvmStatic
        external fun nativeAttachSurface(surface: Surface): Long

        @JvmStatic
        external fun nativeDetachSurface(surfaceId: Long)
    }
}
