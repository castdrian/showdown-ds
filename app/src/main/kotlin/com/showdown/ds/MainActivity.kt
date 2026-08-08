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
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private var reconnectLobbyCommands: List<String>? = null
    private var activeSearchFormat: String? = null
    private var loginInFlight = false
    private var authenticated = false
    private var activeBattleRoomId: String? = null
    private var battleProtocolReady = false
    private var displayedOutgoingChallenge: ShowdownLobbyState.OutgoingChallenge? = null
    private val battleAudioHandler = Handler(Looper.getMainLooper())
    private val battleEventHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val battleRejoinTimeout = Runnable {
        if (activeBattleRoomId != null && !battleProtocolReady && shouldMaintainConnection) {
            activeBattleRoomId = null
            pendingLobbyCommands = null
            pendingLobbyStatus = null
            reconnectLobbyCommands = null
            activeSearchFormat = null
            pendingSearch = false
            pendingSearchTeamPacked = null
            shouldMaintainConnection = false
            showdownConnection?.close()
            showdownConnection = null
            clearPersistedLobbyState()
            session.prepareForLobby()
            session.setConnectionStatus("That battle room is no longer available. Find another battle.")
        }
    }
    private val pendingBattleEvents = ArrayDeque<String>()
    private var battleEventPlaybackScheduled = false
    private var shouldMaintainConnection = false
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
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
            if (showdownConnection?.send(roomId, command) != true) session.handleDecisionSendFailure()
        } else {
            session.handleDecisionSendFailure()
        }
    }
    private val chatListener = BattleSession.ChatListener { message ->
        val roomId = activeBattleRoomId ?: "lobby"
        if (showdownConnection?.send(roomId, message) != true) {
            session.removeLocalChat(message)
            session.setConnectionStatus("Connect to Showdown before sending chat.")
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
                BattleSession.ClientAction.EXPORT_REPLAY -> copyBattleTranscript()
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
        session = BattleSession().apply { prepareForLobby() }
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
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        setContentView(createPrimaryScreen())
        displayManager?.registerDisplayListener(displayListener, null)
        showSecondaryDisplay()
        restoreLobbyConnection(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("maintain_connection", shouldMaintainConnection)
        outState.putBoolean("pending_search", pendingSearch)
        outState.putString("pending_search_team", pendingSearchTeamPacked)
        outState.putString("pending_lobby_status", pendingLobbyStatus)
        outState.putStringArrayList("pending_lobby_commands", ArrayList(pendingLobbyCommands.orEmpty()))
        outState.putStringArrayList("reconnect_lobby_commands", ArrayList(reconnectLobbyCommands.orEmpty()))
        outState.putString("active_search_format", activeSearchFormat)
        outState.putString("active_battle_room", activeBattleRoomId)
        super.onSaveInstanceState(outState)
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
        battleAudioHandler.removeCallbacksAndMessages(null)
        reconnectHandler.removeCallbacksAndMessages(null)
        shouldMaintainConnection = false
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
        if (::session.isInitialized && shouldMaintainConnection && showdownConnection == null && !isFinishing) connectLobbySocket()
    }

    override fun onBackPressed() {
        if (::session.isInitialized && (session.panel != BattleSession.Panel.MOVES || session.selectedGimmick != null || session.targetOptions().isNotEmpty())) {
            cancelController()
            return
        }
        super.onBackPressed()
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

    private fun findBattle() {
        if (activeSearchFormat != null || pendingSearch) {
            cancelActiveSearch()
            return
        }
        if (authenticated && lobbyState.battles.isNotEmpty()) {
            showBattleRoomPicker()
            return
        }
        beginBattleSearch()
    }

    private fun beginBattleSearch() {
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

    private fun showBattleRoomPicker() {
        val battles = lobbyState.battles.entries.toList()
        val labels = listOf("Find a new battle") + battles.map { (roomId, description) -> "$description\n$roomId" }
        AlertDialog.Builder(this)
            .setTitle("Showdown rooms")
            .setItems(labels.toTypedArray()) { _, selected ->
                if (selected == 0) {
                    beginBattleSearch()
                } else {
                    val roomId = battles[selected - 1].key
                    startLobbyConnection(
                        listOf(ShowdownLobbyState.joinBattleCommand(roomId)),
                        "Joining battle room…"
                    )
                }
            }
            .setNegativeButton("Close", null)
            .show()
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
        reconnectLobbyCommands = null
        shouldMaintainConnection = false
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.removeCallbacks(battleRejoinTimeout)
        reconnectScheduled = false
        clearPersistedLobbyState()
        session.setConnectionStatus("Battle search cancelled.")
    }

    private fun startLobbyConnection(lobbyCommands: List<String>? = null, lobbyStatus: String? = null) {
        session.prepareForLobby()
        activeBattleRoomId = null
        battleProtocolReady = false
        clearBattleEventPlayback()
        shouldMaintainConnection = true
        reconnectAttempt = 0
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectScheduled = false
        session.setLiveBattleActive(false)
        showdownConnection?.close()
        pendingSearch = lobbyCommands == null
        pendingLobbyCommands = lobbyCommands
        pendingLobbyStatus = lobbyStatus
        reconnectLobbyCommands = lobbyCommands
        loginInFlight = false
        authenticated = false
        persistLobbyState()
        connectLobbySocket()
    }

    private fun restoreLobbyConnection(savedInstanceState: Bundle?) {
        val preferences = getSharedPreferences("showdown_live", MODE_PRIVATE)
        val hasSavedInstance = savedInstanceState?.containsKey("maintain_connection") == true
        val maintain = if (hasSavedInstance) {
            savedInstanceState?.getBoolean("maintain_connection") == true
        } else {
            preferences.getBoolean("maintain_connection", false)
        }
        if (!maintain) return
        shouldMaintainConnection = true
        reconnectAttempt = 0
        pendingSearch = savedInstanceState?.getBoolean("pending_search") ?: preferences.getBoolean("pending_search", false)
        pendingSearchTeamPacked = savedInstanceState?.getString("pending_search_team") ?: preferences.getString("pending_search_team", null)
        pendingLobbyStatus = savedInstanceState?.getString("pending_lobby_status") ?: preferences.getString("pending_lobby_status", null)
        pendingLobbyCommands = savedInstanceState?.getStringArrayList("pending_lobby_commands")?.takeIf { it.isNotEmpty() }
            ?: decodeLobbyCommands(preferences.getString("pending_lobby_commands", null))
        reconnectLobbyCommands = savedInstanceState?.getStringArrayList("reconnect_lobby_commands")?.takeIf { it.isNotEmpty() }
            ?: decodeLobbyCommands(preferences.getString("reconnect_lobby_commands", null))
        activeSearchFormat = savedInstanceState?.getString("active_search_format") ?: preferences.getString("active_search_format", null)
        activeBattleRoomId = savedInstanceState?.getString("active_battle_room") ?: preferences.getString("active_battle_room", null)
        battleProtocolReady = false
        session.setLiveBattleActive(false)
        connectLobbySocket()
    }

    private fun persistLobbyState() {
        getSharedPreferences("showdown_live", MODE_PRIVATE).edit()
            .putBoolean("maintain_connection", shouldMaintainConnection)
            .putBoolean("pending_search", pendingSearch)
            .putString("pending_search_team", pendingSearchTeamPacked)
            .putString("pending_lobby_status", pendingLobbyStatus)
            .putString("pending_lobby_commands", encodeLobbyCommands(pendingLobbyCommands))
            .putString("reconnect_lobby_commands", encodeLobbyCommands(reconnectLobbyCommands))
            .putString("active_search_format", activeSearchFormat)
            .putString("active_battle_room", activeBattleRoomId)
            .apply()
    }

    private fun clearPersistedLobbyState() {
        getSharedPreferences("showdown_live", MODE_PRIVATE).edit().clear().apply()
    }

    private fun encodeLobbyCommands(commands: List<String>?) = commands?.joinToString("\u0000")

    private fun decodeLobbyCommands(commands: String?) = commands?.split('\u0000')?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }

    private fun connectLobbySocket() {
        val previousConnection = showdownConnection
        showdownConnection = null
        previousConnection?.close()
        lateinit var connection: ShowdownConnection
        connection = ShowdownConnection(serverEndpoint, object : ShowdownConnection.Listener {
            override fun onConnectionStateChanged(state: ShowdownConnection.State, detail: String) {
                runOnUiThread {
                    if (showdownConnection !== connection) return@runOnUiThread
                    if (state == ShowdownConnection.State.DISCONNECTED || state == ShowdownConnection.State.FAILED) {
                        battleProtocolReady = false
                        session.setLiveBattleActive(false)
                    }
                    val status = when (state) {
                        ShowdownConnection.State.CONNECTING -> "Connecting to ${serverEndpoint.displayName}…"
                        ShowdownConnection.State.CONNECTED -> {
                            reconnectAttempt = 0
                            reconnectScheduled = false
                            if (credentialsStore.load() == null) "Joining ${serverEndpoint.displayName}…"
                            else "Signing in to ${serverEndpoint.displayName}…"
                        }
                        ShowdownConnection.State.DISCONNECTED -> {
                            loginInFlight = false
                            authenticated = false
                            if (shouldMaintainConnection) {
                                scheduleReconnect()
                                "Connection lost. Reconnecting to ${serverEndpoint.displayName}…"
                            } else {
                                pendingSearch = false
                                pendingLobbyCommands = null
                                pendingLobbyStatus = null
                                activeSearchFormat = null
                                detail.ifBlank { "Disconnected from ${serverEndpoint.displayName}." }
                            }
                        }
                        ShowdownConnection.State.FAILED -> {
                            loginInFlight = false
                            authenticated = false
                            if (shouldMaintainConnection) {
                                scheduleReconnect()
                                "Could not reach ${serverEndpoint.displayName}. Retrying…"
                            } else {
                                detail.ifBlank { "Could not reach ${serverEndpoint.displayName}." }
                            }
                        }
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
                        reconnectLobbyCommands = null
                        session.setConnectionStatus(error)
                    }
                    if (roomId == null || roomId == "lobby") {
                        session.applyLobbyChat(lines)
                        session.applyServerFormats(lines)
                        getSharedPreferences("showdown", MODE_PRIVATE).edit()
                            .putString("match_format", session.matchFormat.id)
                            .putString("match_format_label", session.matchFormat.label)
                            .apply()
                        val previousChallenges = lobbyState.incomingChallenges
                        val previousBattleRoomIds = lobbyState.battles.keys
                        lobbyState.applyProtocol(lines)
                        if (activeSearchFormat != null) {
                            lobbyState.firstNewBattle(previousBattleRoomIds)?.let { matchedRoomId ->
                                joinMatchedBattle(connection, matchedRoomId)
                            }
                        }
                        lobbyState.incomingChallenges.keys.firstOrNull { it !in previousChallenges }?.let { username ->
                            showIncomingChallenge(username, lobbyState.incomingChallenges[username].orEmpty())
                        }
                        val outgoingChallenge = lobbyState.outgoingChallenge
                        if (outgoingChallenge != null && outgoingChallenge != displayedOutgoingChallenge) {
                            displayedOutgoingChallenge = outgoingChallenge
                            showOutgoingChallenge(outgoingChallenge)
                        } else if (outgoingChallenge == null) {
                            displayedOutgoingChallenge = null
                        }
                        if (lobbyState.isSearching(session.matchFormat.id)) {
                            session.setConnectionStatus("Searching ${session.matchFormat.label}…")
                        }
                    }
                    if (roomId?.startsWith("battle-") == true) {
                        if (lines.any { it.startsWith("|init|battle") }) clearBattleEventPlayback()
                        if (lines.any { it.startsWith("|init|battle") }) reconnectHandler.removeCallbacks(battleRejoinTimeout)
                        activeBattleRoomId = roomId
                        activeSearchFormat = null
                        reconnectLobbyCommands = null
                        persistLobbyState()
                        session.applyProtocolPacket(lines)
                        if (lines.any { it.startsWith("|init|battle") }) battleProtocolReady = true
                        session.setLiveBattleActive(battleProtocolReady)
                        if (session.isBattleFinished()) {
                            lobbyState.clearBattle(roomId)
                            activeBattleRoomId = null
                            battleProtocolReady = false
                            clearPersistedLobbyState()
                        }
                        session.setLiveBattleActive(activeBattleRoomId != null && battleProtocolReady)
                    }
                }
            }
        })
        showdownConnection = connection
        connection.connect()
    }

    private fun joinMatchedBattle(connection: ShowdownConnection, roomId: String) {
        if (activeBattleRoomId != null || !connection.sendGlobal(ShowdownLobbyState.joinBattleCommand(roomId))) return
        activeSearchFormat?.let(lobbyState::clearSearch)
        activeSearchFormat = null
        pendingSearch = false
        pendingSearchTeamPacked = null
        pendingLobbyCommands = null
        pendingLobbyStatus = null
        reconnectLobbyCommands = null
        activeBattleRoomId = roomId
        battleProtocolReady = false
        session.setLiveBattleActive(false)
        session.setConnectionStatus("Joining battle…")
        persistLobbyState()
    }

    private fun scheduleReconnect() {
        if (!shouldMaintainConnection || reconnectScheduled) return
        reconnectScheduled = true
        val delayMillis = (1_000L shl reconnectAttempt.coerceAtMost(4)).coerceAtMost(16_000L)
        reconnectAttempt += 1
        reconnectHandler.postDelayed({
            reconnectScheduled = false
            if (shouldMaintainConnection) connectLobbySocket()
        }, delayMillis)
    }

    private fun sendPendingLobbyCommands(connection: ShowdownConnection) {
        if (!authenticated || showdownConnection !== connection) return
        val commands = pendingLobbyCommands ?: when {
            pendingSearch -> {
                if (!session.matchFormat.usesRandomTeams && pendingSearchTeamPacked.isNullOrBlank()) {
                    pendingSearch = false
                    session.setConnectionStatus("Save a team for ${session.matchFormat.label} before searching.")
                    return
                }
                ShowdownLobbyState.searchCommands(session.matchFormat.id, pendingSearchTeamPacked)
            }
            activeSearchFormat != null -> ShowdownLobbyState.searchCommands(activeSearchFormat!!, pendingSearchTeamPacked)
            activeBattleRoomId != null -> listOf(ShowdownLobbyState.joinBattleCommand(activeBattleRoomId!!))
            reconnectLobbyCommands != null -> reconnectLobbyCommands!!
            else -> return
        }
        val rejoiningBattle = activeBattleRoomId != null && commands == listOf(ShowdownLobbyState.joinBattleCommand(activeBattleRoomId!!))
        val searching = commands.any { it.startsWith("/search ") }
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
        } else if (searching) {
            activeSearchFormat = commands.first { it.startsWith("/search ") }.removePrefix("/search ")
            session.setConnectionStatus("Searching ${session.matchFormat.label}…")
        } else if (rejoiningBattle) {
            session.setConnectionStatus("Rejoining battle…")
            reconnectHandler.removeCallbacks(battleRejoinTimeout)
            reconnectHandler.postDelayed(battleRejoinTimeout, BATTLE_REJOIN_TIMEOUT_MILLIS)
        } else if (reconnectLobbyCommands != null) {
            session.setConnectionStatus("Restoring challenge…")
        }
        persistLobbyState()
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

    private fun showOutgoingChallenge(challenge: ShowdownLobbyState.OutgoingChallenge) {
        AlertDialog.Builder(this)
            .setTitle("Challenge pending")
            .setMessage("Waiting for ${challenge.username} to accept your ${challenge.format} challenge.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Cancel challenge") { _, _ ->
                sendLobbyCommand(ShowdownLobbyState.cancelChallengeCommand(challenge.username), "Challenge cancelled.")
            }
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
        when {
            commands.any { it.startsWith("/accept ") || it.startsWith("/challenge ") } -> reconnectLobbyCommands = commands
            commands.any { it.startsWith("/cancelchallenge ") || it.startsWith("/reject ") } -> reconnectLobbyCommands = null
        }
        persistLobbyState()
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
                    if (shouldMaintainConnection && activeBattleRoomId == null) {
                        session.setConnectionStatus("Server set to ${endpoint.displayName}. Reconnecting…")
                        reconnectHandler.removeCallbacksAndMessages(null)
                        reconnectScheduled = false
                        connectLobbySocket()
                    } else {
                        session.setConnectionStatus("Server set to ${endpoint.displayName}. It will be used next time you connect.")
                    }
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
        lobbyState.clear()
        shouldMaintainConnection = false
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectScheduled = false
        showdownConnection?.close()
        showdownConnection = null
        pendingSearch = false
        pendingLobbyCommands = null
        pendingLobbyStatus = null
        reconnectLobbyCommands = null
        activeSearchFormat = null
        activeBattleRoomId = null
        displayedOutgoingChallenge = null
        clearPersistedLobbyState()
        session.prepareForLobby()
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
            .setNeutralButton("Export backup") { _, _ -> copyTeamBackup() }
            .setPositiveButton("Import backup") { _, _ -> showTeamBackupImport() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun copyTeamBackup() {
        val value = teamLibrary.exportBackup(readable = true)
        if (value.isBlank()) {
            session.setConnectionStatus("There are no teams to export.")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Showdown team backup", value))
        session.setConnectionStatus("Team backup copied to the clipboard.")
    }

    private fun showTeamBackupImport() {
        val input = EditText(this).apply {
            hint = "Paste a Showdown team backup"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setMinLines(8)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipboardValue = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
            if (clipboardValue.isLikelyTeamBackup()) {
                setText(clipboardValue)
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Import team backup")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ ->
                val imported = teamLibrary.importBackup(input.text.toString())
                session.setConnectionStatus(
                    if (imported.isEmpty()) "No valid Showdown teams were found in that backup."
                    else "Imported ${imported.size} team${if (imported.size == 1) "" else "s"}."
                )
            }
            .show()
    }

    private fun String.isLikelyTeamBackup() = contains("===") || contains("]") && contains("|") ||
        contains("\n-") || contains("Ability:", true) || contains(" @ ")

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
        val formatPicker = Button(this).apply {
            text = "Choose format from Showdown"
            setOnClickListener { showTeamFormatPicker(format) }
        }
        val packed = EditText(this).apply {
            hint = "Packed or Showdown export"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setMinLines(2)
            setText(existing?.packed.orEmpty())
        }
        val sets = existing?.let { ShowdownTeamCodec.unpack(it.packed) }.orEmpty().ifEmpty { listOf(ShowdownTeamSet()) }
        val setEditors = mutableListOf<TeamSetEditor>()
        val setFields = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            for (index in 0 until 6) {
                setEditors += createTeamSetEditor(this, index, sets.getOrNull(index) ?: ShowdownTeamSet())
            }
        }
        val importButton = Button(this).apply {
            text = "Load Showdown export into editor"
            setOnClickListener {
                val imported = ShowdownTeamCodec.parse(packed.text.toString())
                if (imported.isEmpty()) {
                    session.setConnectionStatus("Enter a valid packed team or Showdown export before loading it.")
                } else {
                    setEditors.forEachIndexed { index, editor -> populateTeamSetEditor(editor, imported.getOrNull(index) ?: ShowdownTeamSet()) }
                    session.setConnectionStatus("Loaded ${imported.size} Pokémon into the editor.")
                }
            }
        }
        val copyButton = Button(this).apply {
            text = "Copy packed team"
            setOnClickListener {
                val value = ShowdownTeamCodec.pack(setEditors.map(::readTeamSetEditor)).ifBlank {
                    ShowdownTeamCodec.pack(ShowdownTeamCodec.parse(packed.text.toString()))
                }
                if (value.isBlank()) {
                    session.setConnectionStatus("Add at least one Pokémon before copying the team.")
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Showdown packed team", value))
                    session.setConnectionStatus("Packed team copied to the clipboard.")
                }
            }
        }
        val copyTextButton = Button(this).apply {
            text = "Copy Showdown export"
            setOnClickListener {
                val value = ShowdownTeamCodec.toText(setEditors.map(::readTeamSetEditor)).ifBlank {
                    ShowdownTeamCodec.toText(ShowdownTeamCodec.parse(packed.text.toString()))
                }
                if (value.isBlank()) {
                    session.setConnectionStatus("Add at least one Pokémon before copying the export.")
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Showdown team export", value))
                    session.setConnectionStatus("Showdown export copied to the clipboard.")
                }
            }
        }
        val copyJsonButton = Button(this).apply {
            text = "Copy JSON team"
            setOnClickListener {
                val editedJson = ShowdownTeamCodec.toJson(setEditors.map(::readTeamSetEditor))
                val value = editedJson.takeUnless { it == "[]" }
                    ?: ShowdownTeamCodec.toJson(ShowdownTeamCodec.parse(packed.text.toString()))
                if (value == "[]") {
                    session.setConnectionStatus("Add at least one Pokémon before copying the JSON team.")
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Showdown JSON team", value))
                    session.setConnectionStatus("JSON team copied to the clipboard.")
                }
            }
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(format)
            addView(formatPicker)
            addView(importButton)
            addView(copyButton)
            addView(copyTextButton)
            addView(copyJsonButton)
            addView(packed)
            addView(setFields)
        }
        val scroll = ScrollView(this).apply {
            addView(fields)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add team" else "Edit team")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                teamLibrary.remove(existing.id)
                session.setConnectionStatus("Deleted ${existing.name}.")
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val teamFormat = format.text.toString().trim()
                val editedSets = setEditors.map(::readTeamSetEditor)
                val editedPacked = ShowdownTeamCodec.pack(editedSets)
                val importedSets = ShowdownTeamCodec.parse(packed.text.toString())
                val teamPacked = editedPacked.ifBlank { ShowdownTeamCodec.pack(importedSets) }
                val validation = if (editedPacked.isNotBlank()) {
                    ShowdownTeamCodec.validate(editedSets)
                } else {
                    ShowdownTeamCodec.validate(importedSets)
                }
                when {
                    teamFormat.isBlank() || teamPacked.isBlank() -> session.setConnectionStatus("Enter a format ID and at least one Pokémon.")
                    validation.isNotEmpty() -> session.setConnectionStatus(validation.first())
                    else -> {
                        teamLibrary.save(name.text.toString(), teamFormat, teamPacked, existing?.id ?: java.util.UUID.randomUUID().toString())
                        session.setConnectionStatus("Saved ${name.text.toString().trim().ifBlank { "Untitled team" }}.")
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showTeamFormatPicker(target: EditText) {
        val typedFormat = target.text.toString().trim()
        val formats = (session.availableMatchFormats() + typedFormat.takeIf { it.isNotBlank() }?.let { BattleSession.MatchFormat(it, it) })
            .filterNotNull()
            .distinctBy { it.id }
        AlertDialog.Builder(this)
            .setTitle("Choose team format")
            .setSingleChoiceItems(formats.map { "${it.label}\n${it.id}" }.toTypedArray(), formats.indexOfFirst { it.id.equals(typedFormat, true) }) { dialog, selected ->
                target.setText(formats[selected].id)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private data class TeamSetEditor(
        val nickname: EditText,
        val species: EditText,
        val item: EditText,
        val ability: EditText,
        val moves: EditText,
        val nature: EditText,
        val evs: EditText,
        val gender: EditText,
        val ivs: EditText,
        val shiny: CheckBox,
        val level: EditText,
        val happiness: EditText,
        val pokeBall: EditText,
        val hiddenPowerType: EditText,
        val gigantamax: CheckBox,
        val dynamaxLevel: EditText,
        val teraType: EditText
    )

    private fun createTeamSetEditor(parent: LinearLayout, index: Int, set: ShowdownTeamSet): TeamSetEditor {
        parent.addView(TextView(this).apply {
            text = "Pokémon ${index + 1}"
            textSize = 18f
            setPadding(0, 20, 0, 4)
        })
        val editor = TeamSetEditor(
            nickname = teamField("Nickname", set.nickname),
            species = teamField("Species", set.species),
            item = teamField("Item", set.item),
            ability = teamField("Ability", set.ability),
            moves = teamField("Moves, comma-separated", set.moves.joinToString(",")),
            nature = teamField("Nature", set.nature),
            evs = teamField("EVs HP,Atk,Def,SpA,SpD,Spe", set.evs.joinToString(",").takeUnless { set.evs == List(6) { 0 } }.orEmpty()),
            gender = teamField("Gender M or F", set.gender),
            ivs = teamField("IVs HP,Atk,Def,SpA,SpD,Spe", set.ivs.joinToString(",").takeUnless { set.ivs == List(6) { 31 } }.orEmpty()),
            shiny = CheckBox(this).apply { text = "Shiny"; isChecked = set.shiny },
            level = teamField("Level", set.level.takeUnless { it == 100 }?.toString().orEmpty()),
            happiness = teamField("Happiness", set.happiness.takeUnless { it == 255 }?.toString().orEmpty()),
            pokeBall = teamField("Poké Ball", set.pokeBall),
            hiddenPowerType = teamField("Hidden Power type", set.hiddenPowerType),
            gigantamax = CheckBox(this).apply { text = "Gigantamax"; isChecked = set.gigantamax },
            dynamaxLevel = teamField("Dynamax level", set.dynamaxLevel.takeUnless { it == 10 }?.toString().orEmpty()),
            teraType = teamField("Tera type", set.teraType)
        )
        listOf(
            editor.nickname,
            editor.species,
            editor.item,
            editor.ability,
            editor.moves,
            editor.nature,
            editor.evs,
            editor.gender,
            editor.ivs,
            editor.shiny,
            editor.level,
            editor.happiness,
            editor.pokeBall,
            editor.hiddenPowerType,
            editor.gigantamax,
            editor.dynamaxLevel,
            editor.teraType
        ).forEach(parent::addView)
        return editor
    }

    private fun teamField(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        setText(value)
    }

    private fun populateTeamSetEditor(editor: TeamSetEditor, set: ShowdownTeamSet) {
        editor.nickname.setText(set.nickname)
        editor.species.setText(set.species)
        editor.item.setText(set.item)
        editor.ability.setText(set.ability)
        editor.moves.setText(set.moves.joinToString(","))
        editor.nature.setText(set.nature)
        editor.evs.setText(set.evs.joinToString(",").takeUnless { set.evs == List(6) { 0 } }.orEmpty())
        editor.gender.setText(set.gender)
        editor.ivs.setText(set.ivs.joinToString(",").takeUnless { set.ivs == List(6) { 31 } }.orEmpty())
        editor.shiny.isChecked = set.shiny
        editor.level.setText(set.level.takeUnless { it == 100 }?.toString().orEmpty())
        editor.happiness.setText(set.happiness.takeUnless { it == 255 }?.toString().orEmpty())
        editor.pokeBall.setText(set.pokeBall)
        editor.hiddenPowerType.setText(set.hiddenPowerType)
        editor.gigantamax.isChecked = set.gigantamax
        editor.dynamaxLevel.setText(set.dynamaxLevel.takeUnless { it == 10 }?.toString().orEmpty())
        editor.teraType.setText(set.teraType)
    }

    private fun readTeamSetEditor(editor: TeamSetEditor): ShowdownTeamSet = ShowdownTeamSet(
        nickname = editor.nickname.text.toString(),
        species = editor.species.text.toString(),
        item = editor.item.text.toString(),
        ability = editor.ability.text.toString(),
        moves = editor.moves.text.toString().split(',').map(String::trim).filter(String::isNotBlank),
        nature = editor.nature.text.toString(),
        evs = editorValues(editor.evs.text.toString(), 0),
        gender = editor.gender.text.toString(),
        ivs = editorValues(editor.ivs.text.toString(), 31),
        shiny = editor.shiny.isChecked,
        level = editor.level.text.toString().toIntOrNull() ?: 100,
        happiness = editor.happiness.text.toString().toIntOrNull() ?: 255,
        pokeBall = editor.pokeBall.text.toString(),
        hiddenPowerType = editor.hiddenPowerType.text.toString(),
        gigantamax = editor.gigantamax.isChecked,
        dynamaxLevel = editor.dynamaxLevel.text.toString().toIntOrNull() ?: 10,
        teraType = editor.teraType.text.toString()
    )

    private fun editorValues(value: String, default: Int): List<Int> {
        if (value.isBlank()) return List(6) { default }
        return value.split(',').map { it.trim().toIntOrNull() ?: default }.take(6).let { values ->
            values + List(6 - values.size) { default }
        }
    }

    private fun showChatComposer() {
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before opening chat.")
            return
        }
        val input = EditText(this).apply {
            hint = "Send a message"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(if (activeBattleRoomId == null) "Lobby chat" else "Battle chat")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ -> session.sendChat(input.text.toString()) }
            .show()
    }

    private fun copyBattleTranscript() {
        val transcript = session.protocolHistory().joinToString("\n")
        if (transcript.isBlank()) {
            session.setConnectionStatus("No battle transcript is available yet.")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Showdown battle transcript", transcript))
        session.setConnectionStatus("Battle transcript copied to the clipboard.")
    }

    private fun showFormatPicker() {
        val formats = session.availableMatchFormats()
        AlertDialog.Builder(this)
            .setTitle("Battle format")
            .setSingleChoiceItems(formats.map { it.label }.toTypedArray(), formats.indexOf(session.matchFormat)) { dialog, selected ->
                val format = formats[selected]
                if (activeSearchFormat != null || pendingSearch) cancelActiveSearch()
                session.setMatchFormat(format)
                getSharedPreferences("showdown", MODE_PRIVATE).edit()
                    .putString("match_format", format.id)
                    .putString("match_format_label", format.label)
                    .apply()
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
        val preferences = getSharedPreferences("showdown", MODE_PRIVATE)
        val saved = preferences.getString("match_format", null)
        return BattleSession.MatchFormat.defaults.firstOrNull { it.id == saved }
            ?: saved?.let { BattleSession.MatchFormat(it, preferences.getString("match_format_label", it) ?: it) }
            ?: BattleSession.MatchFormat.GEN7_RANDOM
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
        if (session.targetOptions().isNotEmpty()) session.cycleTarget(direction) else session.cyclePanel(direction)
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
        const val BATTLE_REJOIN_TIMEOUT_MILLIS = 15_000L

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
