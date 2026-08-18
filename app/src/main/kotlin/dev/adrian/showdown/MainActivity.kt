package dev.adrian.showdown

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.AutoCompleteTextView
import android.widget.ScrollView
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import java.util.ArrayDeque
import java.util.Locale

class MainActivity : Activity() {
    private data class RoomSelection(val id: String, val title: String, val subtitle: String, val chatRoom: Boolean)
    private data class PendingBattlePacket(
        val connection: ShowdownConnection?,
        val roomId: String?,
        val lines: List<String>
    )
    private data class PendingTeamUpload(val localId: String, val packed: String)
    private data class PendingTeamPrivacy(val localId: String, val remoteId: String)
    private data class PendingTeamDelete(val localId: String, val remoteId: String)
    private data class TeamDraft(val packed: String, val error: String?)
    private data class PendingRegistration(
        val credentials: ShowdownCredentials,
        val confirmation: String,
        val captcha: String
    )

    private var displayManager: DisplayManager? = null
    private var secondaryPresentation: ThorPresentation? = null
    private var secondaryPresentationRequested = false
    private var activityResumed = false
    private val secondaryDisplayRetry = Runnable {
        if (secondaryPresentationRequested && activityResumed && !isFinishing) showSecondaryDisplay()
    }
    private var battleScene: BattleSceneView? = null
    private var primaryFrame: FrameLayout? = null
    private var showdownMoveEffects: ShowdownMoveEffectsView? = null
    private var commandDeck: CommandDeckView? = null
    private lateinit var session: BattleSession
    private lateinit var battleAudio: BattleAudio
    private lateinit var spriteCache: ShowdownSpriteCache
    private lateinit var moveDex: ShowdownMoveDex
    private lateinit var serverEndpoint: ShowdownServerEndpoint
    private lateinit var credentialsStore: ShowdownCredentialsStore
    private lateinit var sessionStore: ShowdownSessionStore
    private lateinit var loginClient: ShowdownLoginClient
    private lateinit var teamLibrary: ShowdownTeamLibrary
    private lateinit var teamUrlFetcher: ShowdownTeamUrlFetcher
    private lateinit var replayFetcher: ShowdownReplayFetcher
    private var showdownConnection: ShowdownConnection? = null
    private var pendingTeamUpload: PendingTeamUpload? = null
    private var pendingTeamPrivacy: PendingTeamPrivacy? = null
    private var pendingTeamDelete: PendingTeamDelete? = null
    private var pendingTeamValidationFormat: String? = null
    private var teamUploadButtons: List<Button> = emptyList()
    private var teamPrivacyButton: Button? = null
    private var teamLibraryDialog: ShowdownDialog? = null
    private var teamEditorDialog: ShowdownDialog? = null
    private var teamEditorShareView: TextView? = null
    private val pokedex = ShowdownPokedex()
    private val lobbyState = ShowdownLobbyState()
    private val friendsState = ShowdownFriendsState()
    private val teamRemoteState = ShowdownTeamRemoteState()
    private val chatRoomState = ShowdownChatRoomState()
    private var pendingSearch = false
    private var pendingSearchTeamPacked: String? = null
    private var pendingLobbyCommands: List<String>? = null
    private var pendingLobbyStatus: String? = null
    private var reconnectLobbyCommands: List<String>? = null
    private var activeSearchFormat: String? = null
    private var loginInFlight = false
    private var sessionRestorePending = false
    private var registrationInFlight = false
    private var latestChallenge: String? = null
    private var pendingRegistration: PendingRegistration? = null
    private var authenticated = false
    private var serverUserNamed = false
    private var activeBattleRoomId: String? = null
    private var battleWasRegistered = false
    private var battleWasParticipant = false
    private var battleIsSpectator = false
    private var completedBattleRoomId: String? = null
    private var leftBattleRoomId: String? = null
    private var pendingBattleJoinRoomId: String? = null
    private var pendingBattleSearchFormat: String? = null
    private var pendingBattleSearchLabel: String? = null
    private var pendingBattleSearchUsesRandomTeams: Boolean? = null
    private var pendingBattleSearchTeamPacked: String? = null
    private var battleProtocolReady = false
    private var pendingDecisionCommand: String? = null
    private var pendingDecisionSentConnection: ShowdownConnection? = null
    private var displayedOutgoingChallenge: ShowdownLobbyState.OutgoingChallenge? = null
    private var displayedIncomingChallenge: String? = null
    private var roomListDialog: ShowdownDialog? = null
    private var roomListPending = false
    private val tournamentDirectoryState = ShowdownTournamentDirectoryState()
    private var tournamentDirectoryDialog: ShowdownDialog? = null
    private var tournamentDirectoryContentView: TextView? = null
    private var tournamentDirectoryLinks: LinearLayout? = null
    private var chatRoomDialog: ShowdownDialog? = null
    private var tournamentDialog: ShowdownDialog? = null
    private var ladderDialog: ShowdownDialog? = null
    private var ladderFormatId: String? = null
    private var chatRoomMessagesView: TextView? = null
    private var chatRoomInput: EditText? = null
    private var chatRoomScroll: ScrollView? = null
    private val lobbyChatState = ShowdownChatRoomState()
    private var lobbyChatDialog: ShowdownDialog? = null
    private var lobbyChatMessagesView: TextView? = null
    private var lobbyChatInput: EditText? = null
    private var lobbyChatScroll: ScrollView? = null
    private var pendingChatRoomId: String? = null
    private var tournamentStatusView: TextView? = null
    private var tournamentDetailsView: TextView? = null
    private var tournamentJoinButton: Button? = null
    private var tournamentLeaveButton: Button? = null
    private var tournamentValidateButton: Button? = null
    private var tournamentReadyButton: Button? = null
    private var tournamentAcceptButton: Button? = null
    private var tournamentCancelButton: Button? = null
    private var pokedexDialog: ShowdownDialog? = null
    private var pokedexSearchInput: EditText? = null
    private var pokedexResults: LinearLayout? = null
    private var pokedexDetails: TextView? = null
    private var pokedexSprite: ShowdownPokedexSpriteView? = null
    private var selectedPokedexEntry: ShowdownPokedex.Entry? = null
    private var pokedexLoading = false
    private var privateMessageDialog: ShowdownDialog? = null
    private var privateMessageTarget: String? = null
    private var privateMessageMessagesView: TextView? = null
    private var privateMessageInput: EditText? = null
    private var privateMessageScroll: ScrollView? = null
    private val privateMessageThreads = linkedMapOf<String, MutableList<String>>()
    private var accountDialog: ShowdownDialog? = null
    private var userDetailsDialog: ShowdownDialog? = null
    private var pendingUserDetailsId: String? = null
    private var friendsDialog: ShowdownDialog? = null
    private var friendsContentView: TextView? = null
    private var friendsInput: EditText? = null
    private var teamRemoteDialog: ShowdownDialog? = null
    private var teamRemoteContentView: TextView? = null
    private var teamRemoteLinks: LinearLayout? = null
    private val battleAudioHandler = Handler(Looper.getMainLooper())
    private val battleEventHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val displayRefreshScheduler = BattleDisplayRefreshScheduler(
        schedule = { runnable -> battleEventHandler.post(runnable) },
        refresh = {
            battleScene?.invalidate()
            commandDeck?.invalidate()
        }
    )
    private val battleRejoinTimeout = Runnable {
        if (activeBattleRoomId != null && !battleProtocolReady && shouldMaintainConnection) {
            shouldMaintainConnection = false
            showdownConnection?.close()
            showdownConnection = null
            clearBattleRoomState()
            session.setConnectionStatus("That battle room is no longer available. Find another battle.")
        }
    }
    private val sessionRestoreTimeout = Runnable {
        if (!sessionRestorePending || !shouldMaintainConnection) return@Runnable
        showdownConnection?.let { connection ->
            sessionRestorePending = false
            loginInFlight = false
            loginClient.clearSession()
            downgradeBattleRecoveryToGuest()
            authenticated = true
            sendPendingLobbyCommands(connection)
            session.setConnectionStatus("Your Showdown session expired. Joining as a guest…")
        }
    }
    private val pendingBattlePackets = ArrayDeque<PendingBattlePacket>()
    private var battlePacketPlaybackScheduled = false
    private var replayStatus: String? = null
    private var replayPaused = false
    private var replayPausedForLifecycle = false
    private var livePlaybackPausedForLifecycle = false
    private var livePlaybackPausedRemainingMillis: Long? = null
    private var replaySpeed = DEFAULT_BATTLE_SPEED
    private var restoredReplayPaused = false
    private var restoredReplaySpeed = DEFAULT_BATTLE_SPEED
    private var activeReplayLink: String? = null
    private var replayLoadRequest: String? = null
    private var playbackScheduledPauseMillis = 0L
    private var playbackScheduledAtMillis = 0L
    private var playbackScheduledSpeed = 1f
    private var playbackPausedRemainingMillis: Long? = null
    private val playbackAdvanceRunnable = Runnable {
        battlePacketPlaybackScheduled = false
        flushBattlePlayback()
    }
    private var shouldMaintainConnection = false
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
    private var controllerHorizontal = 0
    private var controllerVertical = 0
    private val sessionListener = BattleSession.Listener { refreshDisplays() }
    private val protocolListener = BattleSession.ProtocolListener { lines ->
        runOnUiThread { applyBattleProtocolToEffects(lines) }
    }
    private val decisionListener = BattleSession.DecisionListener { command ->
        if (session.isSpectatorMode()) {
            session.setConnectionStatus("Spectators cannot make battle choices.")
            return@DecisionListener
        }
        if (session.isReplayMode()) {
            session.setConnectionStatus("Replays are read-only.")
            return@DecisionListener
        }
        clearBattlePlayback()
        val roomId = activeBattleRoomId
        pendingDecisionCommand = command
        pendingDecisionSentConnection = null
        persistLobbyState(flushToDisk = true)
        val connection = showdownConnection
        if (roomId != null && connection?.send(roomId, command) == true) {
            pendingDecisionSentConnection = connection
            persistLobbyState(flushToDisk = true)
        } else {
            if (roomId == null) {
                pendingDecisionCommand = null
                pendingDecisionSentConnection = null
                persistLobbyState(flushToDisk = true)
            }
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
                BattleSession.ClientAction.OPEN_ROOMS -> showRoomList()
                BattleSession.ClientAction.CHOOSE_FORMAT -> showFormatPicker()
                BattleSession.ClientAction.OPEN_CHAT -> showChatComposer()
                BattleSession.ClientAction.FORFEIT -> confirmForfeit()
                BattleSession.ClientAction.LEAVE_BATTLE -> leaveBattle()
                BattleSession.ClientAction.CHALLENGE_PLAYER -> showChallengeComposer()
                BattleSession.ClientAction.EXPORT_REPLAY -> showReplayActions()
                BattleSession.ClientAction.SAVE_REPLAY -> saveBattleReplay()
                BattleSession.ClientAction.OPEN_REPLAY_CONTROLS -> showReplayControls()
                BattleSession.ClientAction.TOGGLE_BATTLE_TIMER -> toggleBattleTimer()
                BattleSession.ClientAction.CANCEL_CHOICE -> cancelChoice()
                BattleSession.ClientAction.SETTINGS_CHANGED -> {
                    persistUserPreferences()
                    battleAudio.updateOptions(session)
                }
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
            showSecondaryDisplay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        serverEndpoint = loadServerEndpoint()
        credentialsStore = ShowdownCredentialsStore(this)
        sessionStore = ShowdownSessionStore(this)
        loginClient = ShowdownLoginClient(
            initialCookies = sessionStore.load(),
            onCookiesChanged = sessionStore::save
        )
        teamLibrary = ShowdownTeamLibrary(this)
        teamUrlFetcher = ShowdownTeamUrlFetcher()
        replayFetcher = ShowdownReplayFetcher()
        session = BattleSession().apply { prepareForLobby() }
        session.setMatchFormat(loadMatchFormat())
        loadUserPreferences()
        restoredReplayPaused = savedInstanceState?.getBoolean("replay_paused") == true
        restoredReplaySpeed = savedInstanceState?.getFloat("replay_speed", replaySpeed) ?: replaySpeed
        savedInstanceState?.getString("pending_team_upload_local_id")?.let { localId ->
            pendingTeamUpload = PendingTeamUpload(localId, savedInstanceState.getString("pending_team_upload_packed").orEmpty())
        }
        savedInstanceState?.getString("pending_team_privacy_local_id")?.let { localId ->
            pendingTeamPrivacy = PendingTeamPrivacy(localId, savedInstanceState.getString("pending_team_privacy_remote_id").orEmpty())
        }
        savedInstanceState?.getString("pending_team_delete_local_id")?.let { localId ->
            pendingTeamDelete = PendingTeamDelete(localId, savedInstanceState.getString("pending_team_delete_remote_id").orEmpty())
        }
        pendingTeamValidationFormat = savedInstanceState?.getString("pending_team_validation_format")
        session.addListener(sessionListener)
        session.addProtocolListener(protocolListener)
        session.addDecisionListener(decisionListener)
        session.addChatListener(chatListener)
        session.addFeedbackListener(feedbackListener)
        session.addClientActionListener(clientActionListener)
        spriteCache = ShowdownSpriteCache(this)
        moveDex = ShowdownMoveDex(spriteCache)
        session.setMoveTypeResolver(moveDex::typeFor)
        session.setMoveInfoResolver(moveDex::infoFor)
        session.setPokemonTypeResolver(moveDex::typesFor)
        session.setTeamDetailNameResolvers(moveDex::moveNameFor, moveDex::itemNameFor, moveDex::abilityNameFor)
        moveDex.load {
            session.setMoveTypeResolver(moveDex::typeFor)
            session.setMoveInfoResolver(moveDex::infoFor)
            session.setPokemonTypeResolver(moveDex::typesFor)
            session.setTeamDetailNameResolvers(moveDex::moveNameFor, moveDex::itemNameFor, moveDex::abilityNameFor)
        }
        battleAudio = BattleAudio(this, spriteCache, session)
        battleAudio.updateOptions(session)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        setContentView(createPrimaryScreen())
        displayManager?.registerDisplayListener(displayListener, null)
        showSecondaryDisplay()
        restoreLobbyConnection(savedInstanceState)
        val incomingIntentHandled = handleIncomingIntent(intent)
        val restoredReplaySource = savedInstanceState?.getString("active_replay_source")
        if (!incomingIntentHandled) {
            restoredReplaySource?.let(::loadReplay)
        }
        if (ShowdownStartupPolicy.shouldConnectToLobby(shouldMaintainConnection, incomingIntentHandled, restoredReplaySource)) {
            startLobbyConnection(emptyList(), "Connecting to ${serverEndpoint.displayName}…")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberBattleIdentity()
        outState.putBoolean("maintain_connection", shouldMaintainConnection)
        outState.putBoolean("pending_search", pendingSearch)
        outState.putString("pending_search_team", pendingSearchTeamPacked)
        outState.putString("pending_lobby_status", pendingLobbyStatus)
        outState.putStringArrayList("pending_lobby_commands", ArrayList(pendingLobbyCommands.orEmpty()))
        outState.putStringArrayList("reconnect_lobby_commands", ArrayList(reconnectLobbyCommands.orEmpty()))
        outState.putString("active_search_format", activeSearchFormat)
        outState.putString("active_battle_room", activeBattleRoomId)
        outState.putBoolean("battle_registered", battleWasRegistered)
        outState.putBoolean("battle_participant", battleWasParticipant)
        outState.putBoolean("battle_spectator", battleIsSpectator)
        outState.putString("pending_battle_search_format", pendingBattleSearchFormat)
        outState.putString("pending_battle_search_label", pendingBattleSearchLabel)
        outState.putBoolean("pending_battle_search_random", pendingBattleSearchUsesRandomTeams == true)
        outState.putString("pending_battle_search_team", pendingBattleSearchTeamPacked)
        outState.putString("battle_player_slot", session.battlePlayerSlot())
        outState.putString("completed_battle_room", completedBattleRoomId)
        outState.putString("pending_decision_command", pendingDecisionCommand)
        outState.putString("pending_team_upload_local_id", pendingTeamUpload?.localId)
        outState.putString("pending_team_upload_packed", pendingTeamUpload?.packed)
        outState.putString("pending_team_privacy_local_id", pendingTeamPrivacy?.localId)
        outState.putString("pending_team_privacy_remote_id", pendingTeamPrivacy?.remoteId)
        outState.putString("pending_team_delete_local_id", pendingTeamDelete?.localId)
        outState.putString("pending_team_delete_remote_id", pendingTeamDelete?.remoteId)
        outState.putString("pending_team_validation_format", pendingTeamValidationFormat)
        outState.putString("active_replay_source", replayLoadRequest ?: activeReplayLink)
        outState.putBoolean("replay_paused", replayPaused || replayPausedForLifecycle)
        outState.putFloat("replay_speed", replaySpeed)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        activityResumed = false
        dismissSecondaryDisplay()
        roomListDialog?.dismiss()
        roomListDialog = null
        roomListPending = false
        tournamentDirectoryDialog?.dismiss()
        tournamentDirectoryDialog = null
        tournamentDirectoryContentView = null
        tournamentDirectoryLinks = null
        tournamentDirectoryState.clear()
        chatRoomDialog?.dismiss()
        chatRoomDialog = null
        tournamentDialog?.dismiss()
        tournamentDialog = null
        ladderDialog?.dismiss()
        ladderDialog = null
        chatRoomMessagesView = null
        chatRoomInput = null
        chatRoomScroll = null
        pendingChatRoomId = null
        chatRoomState.clear()
        lobbyChatDialog?.dismiss()
        lobbyChatDialog = null
        lobbyChatMessagesView = null
        lobbyChatInput = null
        lobbyChatScroll = null
        lobbyChatState.clear()
        privateMessageDialog?.dismiss()
        privateMessageDialog = null
        privateMessageTarget = null
        privateMessageMessagesView = null
        privateMessageInput = null
        privateMessageScroll = null
        privateMessageThreads.clear()
        accountDialog?.dismiss()
        accountDialog = null
        teamEditorDialog?.dismiss()
        teamEditorDialog = null
        teamEditorShareView = null
        pendingTeamUpload = null
        pendingTeamPrivacy = null
        pendingTeamDelete = null
        pendingTeamValidationFormat = null
        teamUploadButtons = emptyList()
        teamPrivacyButton = null
        userDetailsDialog?.dismiss()
        userDetailsDialog = null
        pendingUserDetailsId = null
        friendsDialog?.dismiss()
        friendsDialog = null
        friendsContentView = null
        friendsInput = null
        friendsState.clear()
        teamRemoteDialog?.dismiss()
        teamRemoteDialog = null
        teamRemoteContentView = null
        teamRemoteLinks = null
        teamRemoteState.clear()
        pokedexDialog?.dismiss()
        pokedexDialog = null
        pokedexSearchInput = null
        pokedexResults = null
        pokedexDetails = null
        pokedexSprite = null
        selectedPokedexEntry = null
        displayManager?.unregisterDisplayListener(displayListener)
        window.decorView.removeCallbacks(secondaryDisplayRetry)
        if (::session.isInitialized) session.removeListener(sessionListener)
        if (::session.isInitialized) session.removeProtocolListener(protocolListener)
        if (::session.isInitialized) session.removeDecisionListener(decisionListener)
        if (::session.isInitialized) session.removeChatListener(chatListener)
        if (::session.isInitialized) session.removeFeedbackListener(feedbackListener)
        if (::session.isInitialized) session.removeClientActionListener(clientActionListener)
        battleAudioHandler.removeCallbacksAndMessages(null)
        displayRefreshScheduler.cancel()
        reconnectHandler.removeCallbacksAndMessages(null)
        shouldMaintainConnection = false
        clearBattlePlayback()
        showdownConnection?.close()
        showdownConnection = null
        if (::battleAudio.isInitialized) battleAudio.release()
        if (::moveDex.isInitialized) moveDex.close()
        pokedex.close()
        if (::spriteCache.isInitialized) spriteCache.close()
        if (::teamUrlFetcher.isInitialized) teamUrlFetcher.close()
        if (::replayFetcher.isInitialized) replayFetcher.close()
        showdownMoveEffects?.release()
        showdownMoveEffects = null
        primaryFrame = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0 && isConfirmButton(keyCode)) return true
        return if (handleControllerKey(keyCode)) true else super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return if (handleControllerMotionEvent(event)) true else super.onGenericMotionEvent(event)
    }

    private fun handleControllerMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) return false
        val horizontal = axisDirection(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X)
        val vertical = axisDirection(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)
        if (horizontal != controllerHorizontal || vertical != controllerVertical) {
            controllerHorizontal = horizontal
            controllerVertical = vertical
            if (horizontal != 0 || vertical != 0) navigateController(horizontal, vertical)
        }
        return true
    }

    override fun onPause() {
        activityResumed = false
        battleScene?.setPlaybackPaused(true)
        pauseReplayForLifecycle()
        pauseLivePlaybackForLifecycle()
        if (::battleAudio.isInitialized) {
            battleAudio.pauseBattleCues()
            battleAudio.pauseMusic()
        }
        super.onPause()
    }

    override fun onStop() {
        activityResumed = false
        pauseReplayForLifecycle()
        pauseLivePlaybackForLifecycle()
        if (::session.isInitialized && shouldMaintainConnection) persistLobbyState(flushToDisk = true)
        window.decorView.removeCallbacks(secondaryDisplayRetry)
        dismissSecondaryDisplay()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        resumeReplayForLifecycle()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        configureWindow()
        showSecondaryDisplay()
        battleScene?.setPlaybackPaused(false)
        if (::battleAudio.isInitialized && ::session.isInitialized) battleAudio.updateOptions(session)
        resumeReplayForLifecycle()
        resumeLivePlaybackForLifecycle()
        if (::battleAudio.isInitialized) battleAudio.resumeBattleCues()
        if (::session.isInitialized && shouldMaintainConnection && showdownConnection == null && !isFinishing) connectLobbySocket()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureWindow()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun createPrimaryScreen(): View {
        val frame = FrameLayout(this).also { primaryFrame = it }
        battleScene = BattleSceneView(this, session, spriteCache)
        battleScene?.setPlaybackSpeed(replaySpeed)
        frame.addView(battleScene, FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    private fun ensureShowdownMoveEffects(): ShowdownMoveEffectsView? {
        showdownMoveEffects?.let { return it }
        val frame = primaryFrame ?: return null
        val effects = ShowdownMoveEffectsView(
            this,
            battleAudio::playBattleCue,
            battleAudio::resetBattleCues,
            protocolHistoryProvider = { session.protocolHistory() },
            audioMoveResetter = battleAudio::beginBattleMove,
            battleLogListener = { value, generation ->
                runOnUiThread { session.appendShowdownBattleLog(value, generation) }
            },
            battleMarkupListener = { key, value, generation ->
                runOnUiThread { session.replaceShowdownBattleMarkup(key, value, generation) }
            },
            battleLogSyncListener = { generation ->
                runOnUiThread { session.markNativeBattleLogSynchronized(generation) }
            }
        )
        showdownMoveEffects = effects
        frame.addView(effects, FrameLayout.LayoutParams(-1, -1))
        effects.setPerspective(session.battlePlayerSlot())
        effects.setPlaybackSpeed(replaySpeed)
        effects.setPlaybackPaused(replayPaused || replayPausedForLifecycle || livePlaybackPausedForLifecycle)
        effects.seed(session.protocolHistory())
        return effects
    }

    private fun showSecondaryDisplay() {
        if (isFinishing || displayManager == null) return
        secondaryPresentationRequested = true
        if (!activityResumed) return
        secondaryPresentation?.let { presentation ->
            presentation.requestControllerFocus()
            return
        }
        val display = findThorDisplay()
        if (display == null) {
            scheduleSecondaryDisplayRetry()
            return
        }
        val presentation = ThorPresentation(this, display)
        secondaryPresentation = presentation
        presentation.setOnDismissListener {
            if (secondaryPresentation !== presentation) return@setOnDismissListener
            secondaryPresentation = null
            if (secondaryPresentationRequested && !isFinishing) scheduleSecondaryDisplayRetry()
        }
        try {
            presentation.show()
        } catch (_: WindowManager.BadTokenException) {
            if (secondaryPresentation === presentation) secondaryPresentation = null
            scheduleSecondaryDisplayRetry()
            return
        } catch (_: WindowManager.InvalidDisplayException) {
            if (secondaryPresentation === presentation) secondaryPresentation = null
            scheduleSecondaryDisplayRetry()
            return
        }
        configurePresentationWindow(presentation.window)
        presentation.requestControllerFocus()
        window.decorView.postDelayed({
            if (secondaryPresentation === presentation && presentation.isShowing) {
                presentation.requestControllerFocus()
            }
        }, 250)
    }

    private fun scheduleSecondaryDisplayRetry() {
        if (!secondaryPresentationRequested || !activityResumed || isFinishing) return
        window.decorView.removeCallbacks(secondaryDisplayRetry)
        window.decorView.postDelayed(secondaryDisplayRetry, 500)
    }

    private fun findThorDisplay(): Display? {
        return displayManager?.displays?.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
                ThorDisplayProfile.isThorLowerDisplay(
                    display.mode.physicalWidth,
                    display.mode.physicalHeight
                )
        }
    }

    private fun dismissSecondaryDisplay() {
        window.decorView.removeCallbacks(secondaryDisplayRetry)
        secondaryPresentationRequested = false
        secondaryPresentation?.dismiss()
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
            KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                if (!session.canShift()) return false
                session.selectShiftWithTouch()
                battleAudio.playConfirm()
            }
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
        }
        displayRefreshScheduler.request()
    }

    private fun applyBattleProtocolToEffects(lines: List<String>) {
        val effectsAlreadyCreated = showdownMoveEffects != null
        if (lines.any { it.startsWith("|init|battle") }) battleScene?.resetBattleFeed()
        if (lines.any { it.startsWith("|init|battle") }) ensureShowdownMoveEffects()
        if (!effectsAlreadyCreated && lines.any { it.startsWith("|init|battle") }) return
        showdownMoveEffects?.setPerspective(session.battlePlayerSlot())
        showdownMoveEffects?.applyProtocol(lines, session.battleLogGeneration())
    }

    private fun flushBattlePlayback() {
        if ((replayPaused && session.isReplayMode()) || (livePlaybackPausedForLifecycle && !session.isReplayMode())) return
        if (battlePacketPlaybackScheduled || pendingBattlePackets.isEmpty()) {
            if (!battlePacketPlaybackScheduled && pendingBattlePackets.isEmpty()) {
                replayStatus?.let {
                    replayStatus = null
                    session.setConnectionStatus(it)
                }
            }
            return
        }
        val packet = pendingBattlePackets.removeFirst()
        if (packet.connection != null && showdownConnection !== packet.connection) {
            flushBattlePlayback()
            return
        }
        session.applyProtocolPacket(packet.lines)
        handleAppliedBattlePacket(packet)
        scheduleBattlePlayback(BattlePlaybackTiming.pauseAfter(packet.lines))
    }

    private fun scheduleBattlePlayback(pauseMillis: Long) {
        battlePacketPlaybackScheduled = true
        playbackScheduledPauseMillis = pauseMillis
        playbackScheduledAtMillis = SystemClock.elapsedRealtime()
        playbackScheduledSpeed = replaySpeed
        battleEventHandler.postDelayed(
            playbackAdvanceRunnable,
            BattlePlaybackTiming.scaledPause(pauseMillis, playbackScheduledSpeed)
        )
    }

    private fun setReplayPaused(value: Boolean) {
        if (!session.isReplayMode() || replayPaused == value) return
        replayPaused = value
        showdownMoveEffects?.setPlaybackPaused(value)
        if (value) {
            if (battlePacketPlaybackScheduled) {
                val elapsedMillis = (SystemClock.elapsedRealtime() - playbackScheduledAtMillis).coerceAtLeast(0L)
                val consumedMillis = (elapsedMillis * playbackScheduledSpeed).toLong()
                playbackPausedRemainingMillis = (playbackScheduledPauseMillis - consumedMillis).coerceAtLeast(0L)
            }
            battleEventHandler.removeCallbacks(playbackAdvanceRunnable)
            battlePacketPlaybackScheduled = false
        } else {
            playbackPausedRemainingMillis?.let { remainingMillis ->
                playbackPausedRemainingMillis = null
                scheduleBattlePlayback(remainingMillis)
            } ?: flushBattlePlayback()
        }
        updateReplayStatus()
    }

    private fun setReplaySpeed(value: Float) {
        val nextSpeed = value.coerceIn(0.25f, 4f)
        if (nextSpeed == replaySpeed) return
        if (battlePacketPlaybackScheduled && !replayPaused) {
            val elapsedMillis = (SystemClock.elapsedRealtime() - playbackScheduledAtMillis).coerceAtLeast(0L)
            val consumedMillis = (elapsedMillis * playbackScheduledSpeed).toLong()
            val remainingMillis = (playbackScheduledPauseMillis - consumedMillis).coerceAtLeast(0L)
            battleEventHandler.removeCallbacks(playbackAdvanceRunnable)
            replaySpeed = nextSpeed
            scheduleBattlePlayback(remainingMillis)
        } else {
            replaySpeed = nextSpeed
        }
        showdownMoveEffects?.setPlaybackSpeed(replaySpeed)
        battleScene?.setPlaybackSpeed(replaySpeed)
        getSharedPreferences("showdown", MODE_PRIVATE).edit().putFloat("battle_speed", replaySpeed).apply()
        if (session.isReplayMode()) updateReplayStatus() else session.setConnectionStatus("Battle playback speed: ${replaySpeed.trimTrailingZero()}×")
    }

    private fun updateReplayStatus() {
        val title = replayStatus ?: return
        val state = if (replayPaused) "Paused" else "${replaySpeed.trimTrailingZero()}×"
        session.setConnectionStatus("$title · $state")
    }

    private fun Float.trimTrailingZero(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private fun enqueueBattlePlayback(connection: ShowdownConnection?, roomId: String?, lines: List<String>, resetOnBattleInit: Boolean = true) {
        if (lines.isEmpty()) return
        if (resetOnBattleInit && lines.any { it.startsWith("|init|battle") }) clearBattlePlayback()
        BattlePlaybackTiming.chunks(lines).forEach { chunk ->
            if (chunk.isEmpty()) return@forEach
            val packet = PendingBattlePacket(connection, roomId, chunk)
            if (connection != null && !session.isReplayMode() && BattlePlaybackTiming.isDecisionChunk(chunk)) {
                session.applyProtocolPacket(chunk)
                handleAppliedBattlePacket(packet)
            } else {
                pendingBattlePackets.addLast(packet)
            }
        }
        flushBattlePlayback()
    }

    private fun clearBattlePlayback() {
        battleEventHandler.removeCallbacksAndMessages(null)
        displayRefreshScheduler.cancel()
        pendingBattlePackets.clear()
        battlePacketPlaybackScheduled = false
        replayPaused = false
        replayPausedForLifecycle = false
        livePlaybackPausedRemainingMillis = null
        if (!livePlaybackPausedForLifecycle) showdownMoveEffects?.setPlaybackPaused(false)
        showdownMoveEffects?.setPlaybackSpeed(replaySpeed)
        playbackScheduledPauseMillis = 0L
        playbackScheduledAtMillis = 0L
        playbackScheduledSpeed = 1f
        playbackPausedRemainingMillis = null
        replayStatus = null
    }

    private fun pauseReplayForLifecycle() {
        if (::session.isInitialized && session.isReplayMode() && !replayPaused) {
            replayPausedForLifecycle = true
            setReplayPaused(true)
        }
    }

    private fun resumeReplayForLifecycle() {
        if (::session.isInitialized && session.isReplayMode() && replayPausedForLifecycle) {
            replayPausedForLifecycle = false
            setReplayPaused(false)
        }
    }

    private fun pauseLivePlaybackForLifecycle() {
        if (!::session.isInitialized || session.isReplayMode() || livePlaybackPausedForLifecycle) return
        livePlaybackPausedForLifecycle = true
        showdownMoveEffects?.setPlaybackPaused(true)
        if (battlePacketPlaybackScheduled) {
            livePlaybackPausedRemainingMillis = remainingPlaybackMillis()
            battleEventHandler.removeCallbacks(playbackAdvanceRunnable)
            battlePacketPlaybackScheduled = false
        }
    }

    private fun resumeLivePlaybackForLifecycle() {
        if (!livePlaybackPausedForLifecycle) return
        livePlaybackPausedForLifecycle = false
        if (::session.isInitialized && !session.isReplayMode()) {
            showdownMoveEffects?.setPlaybackPaused(false)
            livePlaybackPausedRemainingMillis?.let { remainingMillis ->
                livePlaybackPausedRemainingMillis = null
                scheduleBattlePlayback(remainingMillis)
            } ?: flushBattlePlayback()
        }
    }

    private fun remainingPlaybackMillis(): Long {
        val elapsedMillis = (SystemClock.elapsedRealtime() - playbackScheduledAtMillis).coerceAtLeast(0L)
        val consumedMillis = (elapsedMillis * playbackScheduledSpeed).toLong()
        return (playbackScheduledPauseMillis - consumedMillis).coerceAtLeast(0L)
    }

    private fun handleAppliedBattlePacket(packet: PendingBattlePacket) {
        val roomId = packet.roomId ?: return
        if (packet.connection == null || showdownConnection !== packet.connection) return
        if (session.isBattleFinished()) {
            lobbyState.clearBattle(roomId)
            completedBattleRoomId = roomId
            activeBattleRoomId = null
            battleIsSpectator = false
            pendingBattleSearchFormat = null
            pendingBattleSearchLabel = null
            pendingBattleSearchUsesRandomTeams = null
            pendingBattleSearchTeamPacked = null
            battleProtocolReady = false
            pendingDecisionCommand = null
            pendingDecisionSentConnection = null
            clearPersistedLobbyState()
            session.setLiveBattleActive(false)
            return
        }
        persistLobbyState()
        if (activeBattleRoomId == roomId && battleProtocolReady && !session.isSpectatorMode() && session.decisionAvailable && pendingDecisionSentConnection !== packet.connection) {
            pendingDecisionCommand?.let { command ->
                if (packet.connection.send(roomId, command)) pendingDecisionSentConnection = packet.connection
            }
        }
    }

    private fun reconcilePendingDecisionCommand(lines: List<String>) {
        val command = pendingDecisionCommand ?: return
        if (!ShowdownDecisionDelivery.shouldClearPendingCommand(command, lines)) return
        pendingDecisionCommand = null
        pendingDecisionSentConnection = null
        persistLobbyState(flushToDisk = true)
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

    private fun beginBattleSearch(
        searchFormat: String? = null,
        searchTeamPacked: String? = null,
        searchLabel: String? = null,
        searchUsesRandomTeams: Boolean? = null
    ) {
        val format = searchFormat?.let { id ->
            val normalizedId = id.trim()
            session.availableMatchFormats().firstOrNull { it.id.trim().equals(normalizedId, true) }
                ?.let { advertised ->
                    advertised.copy(
                        id = advertised.id.trim(),
                        label = ShowdownTeamLibraryQuery.displayFormat(normalizedId, session.availableMatchFormats())
                    )
                }
                ?: BattleSession.MatchFormat.defaults.firstOrNull { it.id.trim().equals(normalizedId, true) }
                ?: BattleSession.MatchFormat(
                    id = normalizedId,
                    label = searchLabel?.trim().takeUnless { it.isNullOrBlank() } ?: ShowdownTeamLibraryQuery.displayFormat(normalizedId),
                    menuLabel = searchLabel?.trim().takeUnless { it.isNullOrBlank() } ?: ShowdownTeamLibraryQuery.displayFormat(normalizedId),
                    usesRandomTeams = searchUsesRandomTeams ?: BattleSession.MatchFormat.usesRandomTeamsFor(normalizedId),
                    canSearch = false
                )
        } ?: session.matchFormat
        val searchableFormat = ensureSearchableMatchFormat(format)
        val teamOptions = ShowdownTeamLibraryQuery.matchingFormat(teamLibrary.teams(), searchableFormat.id)
        val usesRandomTeams = BattleSession.MatchFormat.usesRandomTeams(searchableFormat)
        if (!usesRandomTeams && searchTeamPacked.isNullOrBlank() && teamOptions.isEmpty()) {
            session.setConnectionStatus("Save a ${readableFormatLabel(searchableFormat.id)} team before searching.")
            showTeamLibrary()
            return
        }
        if (!usesRandomTeams && searchTeamPacked.isNullOrBlank() && teamOptions.size > 1) {
            showTeamPicker(teamOptions) {
                pendingSearchTeamPacked = it.packed
                startLobbyConnection()
            }
            return
        }
        pendingSearchTeamPacked = searchTeamPacked
            ?: teamOptions.firstOrNull()?.packed?.takeUnless { usesRandomTeams }
        startLobbyConnection()
    }

    private fun showBattleRoomPicker() {
        val battles = lobbyState.battles.entries.toList()
        val labels = listOf("Find a new battle") + battles.map { (roomId, description) -> "$description\n$roomId" }
        ShowdownDialogBuilder(this)
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

    private fun showRoomList() {
        if (!authenticated) {
            session.setConnectionStatus("Connect to Showdown to browse public rooms.")
            return
        }
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before browsing rooms.")
            return
        }
        roomListPending = true
        renderRoomListDialog()
        val sent = showdownConnection?.sendGlobal("/cmd rooms") == true && showdownConnection?.sendGlobal("/cmd roomlist") == true
        if (!sent) {
            roomListPending = false
            roomListDialog?.dismiss()
            roomListDialog = null
            session.setConnectionStatus("Showdown connection is not ready yet.")
        }
    }

    private fun renderRoomListDialog() {
        val rooms = lobbyState.rooms
        val selections = lobbyState.battleRooms.take(32).map { battle ->
            RoomSelection(
                battle.id,
                "Watch ${battle.playerOne} vs ${battle.playerTwo}",
                battle.minimumElo.takeIf { it.isNotBlank() }?.let { "Rated · $it+ Elo" } ?: "Live battle",
                false
            )
        } + rooms.map { room ->
            val users = room.userCount.takeIf { it >= 0 }?.let { " · $it online" }.orEmpty()
            RoomSelection(room.id, "${room.title}$users", room.description.ifBlank { room.section }, true)
        }
        val previous = roomListDialog
        roomListDialog = null
        previous?.dismiss()
        val density = resources.displayMetrics.density
        val roomRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (selections.isEmpty()) {
            roomRows.addView(TextView(this).apply {
                text = "Loading public rooms…"
                setTextSize(17f)
                setTextColor(0xffdceff2.toInt())
                setPadding((16f * density).toInt(), (18f * density).toInt(), (16f * density).toInt(), (18f * density).toInt())
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            selections.forEach { room ->
                roomRows.addView(Button(this).apply {
                    text = "${room.title}\n${room.subtitle}"
                    isAllCaps = false
                    setOnClickListener {
                        roomListPending = false
                        roomListDialog?.dismiss()
                        roomListDialog = null
                        if (room.chatRoom) {
                            pendingChatRoomId = room.id
                            if (showdownConnection?.sendGlobal(ShowdownLobbyState.joinBattleCommand(room.id)) == true) {
                                session.setConnectionStatus("Joining ${room.title}…")
                            } else {
                                pendingChatRoomId = null
                                session.setConnectionStatus("Could not join ${room.title}.")
                            }
                        } else {
                            pendingChatRoomId = null
                            startLobbyConnection(
                                listOf(ShowdownLobbyState.joinBattleCommand(room.id)),
                                "Joining ${room.title}…"
                            )
                        }
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, (8f * density).toInt()) })
            }
        }
        val roomScroll = ScrollView(this).apply {
            addView(roomRows, -1, -2)
        }
        val dialogViewport = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val roomScrollHeight = (dialogViewport * 0.42f).toInt()
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val tournaments = Button(this@MainActivity).apply {
                text = "Tournaments"
                setOnClickListener {
                    roomListPending = false
                    roomListDialog?.dismiss()
                    showTournamentDirectory()
                }
            }
            val ladder = Button(this@MainActivity).apply {
                text = "Ladder"
                setOnClickListener {
                    roomListPending = false
                    roomListDialog?.dismiss()
                    showLadderDialog()
                }
            }
            val message = Button(this@MainActivity).apply {
                text = "Message"
                setOnClickListener {
                    roomListPending = false
                    roomListDialog?.dismiss()
                    showPrivateMessageComposer()
                }
            }
            val lobby = Button(this@MainActivity).apply {
                text = "Lobby"
                setOnClickListener {
                    roomListPending = false
                    roomListDialog?.dismiss()
                    showLobbyChatDialog()
                }
            }
            listOf(tournaments, ladder, message, lobby).forEach { button ->
                addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins((3f * density).toInt(), 0, (3f * density).toInt(), 0) })
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(roomScroll, LinearLayout.LayoutParams(-1, roomScrollHeight))
            addView(tools, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Showdown rooms")
            .setView(root)
            .setNegativeButton("Close") { _, _ -> roomListPending = false }
            .create()
        dialog.setOnDismissListener {
            if (roomListDialog === dialog) {
                roomListDialog = null
                roomListPending = false
            }
        }
        roomListDialog = dialog
        dialog.show()
    }

    private fun showTournamentDirectory() {
        if (!authenticated) {
            session.setConnectionStatus("Connect to Showdown to browse tournaments.")
            return
        }
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before browsing tournaments.")
            return
        }
        tournamentDirectoryState.clear()
        val density = resources.displayMetrics.density
        val content = TextView(this).apply {
            setTextSize(17f)
            setTextColor(0xffdceff2.toInt())
            setTextIsSelectable(true)
            setPadding((10f * density).toInt(), (8f * density).toInt(), (10f * density).toInt(), (8f * density).toInt())
        }
        val links = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val refresh = Button(this).apply {
            text = "Refresh"
            setOnClickListener { requestTournamentDirectory() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(refresh, LinearLayout.LayoutParams(-1, -2))
            addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
            addView(links, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
        }
        tournamentDirectoryDialog?.dismiss()
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Tournaments")
            .setView(ScrollView(this).apply { addView(root, -1, -2) })
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (tournamentDirectoryDialog === dialog) {
                tournamentDirectoryDialog = null
                tournamentDirectoryContentView = null
                tournamentDirectoryLinks = null
                tournamentDirectoryState.clear()
            }
        }
        tournamentDirectoryDialog = dialog
        tournamentDirectoryContentView = content
        tournamentDirectoryLinks = links
        dialog.show()
        requestTournamentDirectory()
    }

    private fun requestTournamentDirectory() {
        if (showdownConnection?.sendGlobal(ShowdownTournamentDirectoryState.pageCommand()) != true) {
            session.setConnectionStatus("Tournament connection is not ready yet.")
        }
    }

    private fun updateTournamentDirectoryDialog() {
        val snapshot = tournamentDirectoryState.snapshot
        tournamentDirectoryDialog?.setTitle(snapshot.title)
        tournamentDirectoryContentView?.text = snapshot.error ?: snapshot.text
        val links = tournamentDirectoryLinks ?: return
        links.removeAllViews()
        val density = resources.displayMetrics.density
        snapshot.tournaments.forEach { tournament ->
            val button = Button(this).apply {
                text = buildString {
                    append(tournament.roomName)
                    append("\n")
                    append(readableFormatLabel(tournament.format))
                    if (tournament.generator.isNotBlank()) append(" · ${tournament.generator}")
                    if (tournament.started) append(" · Started")
                    tournament.playerCount?.let { append(" · $it players") }
                }
                isAllCaps = false
                setOnClickListener {
                    tournamentDirectoryDialog?.dismiss()
                    pendingChatRoomId = tournament.roomId
                    if (showdownConnection?.sendGlobal(ShowdownTournamentDirectoryState.joinCommand(tournament.roomId)) == true) {
                        session.setConnectionStatus("Joining ${tournament.roomName}…")
                    } else {
                        pendingChatRoomId = null
                        session.setConnectionStatus("Could not join ${tournament.roomName}.")
                    }
                }
            }
            styleDynamicDialogButton(button)
            links.addView(button, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, (6f * density).toInt(), 0, 0) })
        }
    }

    private fun styleDynamicDialogButton(button: Button) {
        val density = resources.displayMetrics.density
        button.isAllCaps = false
        button.setTextColor(0xffe5fcf8.toInt())
        button.setTextSize(15f)
        button.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        button.minHeight = (50f * density).toInt()
        button.minimumHeight = (50f * density).toInt()
        button.setPadding((14f * density).toInt(), 0, (14f * density).toInt(), 0)
        button.background = GradientDrawable().apply {
            setColor(0xff187c81.toInt())
            setStroke((1f * density).toInt(), 0xff79dad3.toInt())
            cornerRadius = 14f * density
        }
    }

    private fun showLadderDialog(format: BattleSession.MatchFormat = session.matchFormat) {
        if (!authenticated || !serverUserNamed) {
            session.setConnectionStatus("Sign in to view the ladder.")
            return
        }
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before viewing the ladder.")
            return
        }
        ladderFormatId = format.id
        lobbyState.clearLadder()
        renderLadderDialog()
        requestLadder(format)
    }

    private fun renderLadderDialog() {
        val format = session.availableMatchFormats().firstOrNull { it.id.trim().equals(ladderFormatId?.trim(), true) }
            ?: ladderFormatId?.let { BattleSession.MatchFormat(it, it) }
            ?: session.matchFormat
        val entries = lobbyState.ladder
        val labels = if (entries.isEmpty()) {
            arrayOf("Loading ${readableFormatLabel(format.id)} ladder…")
        } else {
            entries.mapIndexed { index, entry ->
                val glicko = "${entry.rpr.toInt()} ± ${entry.rprd.toInt()}"
                "${index + 1}. ${entry.username}\nElo ${entry.elo.toInt()} · GXE ${entry.gxe.toInt()}% · Glicko $glicko"
            }.toTypedArray()
        }
        val previous = ladderDialog
        ladderDialog = null
        previous?.dismiss()
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Ladder · ${readableFormatLabel(format.id)}")
            .setItems(labels) { _, _ -> }
            .setNegativeButton("Close") { _, _ -> ladderFormatId = null }
            .setNeutralButton("Format") { _, _ -> showLadderFormatPicker() }
            .setPositiveButton("Refresh") { _, _ -> requestLadder(format) }
            .create()
        dialog.setOnDismissListener {
            if (ladderDialog === dialog) {
                ladderDialog = null
                ladderFormatId = null
            }
        }
        ladderDialog = dialog
        dialog.show()
    }

    private fun requestLadder(format: BattleSession.MatchFormat) {
        ladderFormatId = format.id
        if (showdownConnection?.sendGlobal("/cmd laddertop ${format.id}") == true) {
            session.setConnectionStatus("Loading ${readableFormatLabel(format.id)} ladder…")
        } else {
            session.setConnectionStatus("Could not request the Showdown ladder.")
        }
    }

    private fun showLadderFormatPicker() {
        val formats = session.availableMatchFormats().filter { it.canSearch }.ifEmpty { session.availableMatchFormats() }
        ShowdownDialogBuilder(this)
            .setTitle("Ladder format")
            .setSingleChoiceItems(formats.map { readableFormatLabel(it.id) }.toTypedArray(), formats.indexOfFirst { it.id.trim().equals(ladderFormatId?.trim(), true) }) { _, selected ->
                val format = formats[selected]
                showLadderDialog(format)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPrivateMessageComposer() {
        if (!authenticated || !serverUserNamed) {
            session.setConnectionStatus("Sign in to message another player.")
            return
        }
        val targetInput = EditText(this).apply {
            hint = "Username"
            setSingleLine(true)
        }
        ShowdownDialogBuilder(this)
            .setTitle("Message player")
            .setView(targetInput)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open") { dialog, _ ->
                val target = targetInput.text.toString().trim()
                if (target.isBlank()) {
                    session.setConnectionStatus("Enter a username to message.")
                } else {
                    dialog.dismiss()
                    showPrivateMessageDialog(target)
                }
            }
            .show()
    }

    private fun showFindUserComposer() {
        if (!authenticated || !serverUserNamed) {
            session.setConnectionStatus("Sign in to look up another player.")
            return
        }
        val targetInput = EditText(this).apply {
            hint = "Username"
            setSingleLine(true)
        }
        ShowdownDialogBuilder(this)
            .setTitle("Find a user")
            .setView(targetInput)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Find") { dialog, _ ->
                val target = targetInput.text.toString().trim()
                if (target.isBlank()) {
                    session.setConnectionStatus("Enter a username to find.")
                } else {
                    dialog.dismiss()
                    requestUserDetails(target)
                }
            }
            .show()
    }

    private fun requestUserDetails(target: String) {
        pendingUserDetailsId = normalizeShowdownId(target)
        userDetailsDialog?.dismiss()
        val density = resources.displayMetrics.density
        val loading = TextView(this).apply {
            text = "Loading ${target.trim()}…"
            setTextSize(18f)
            setTextColor(0xffd5e9ed.toInt())
            setPadding((24f * density).toInt(), (22f * density).toInt(), (24f * density).toInt(), (22f * density).toInt())
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Player profile")
            .setView(loading)
            .setNegativeButton("Close") { _, _ -> pendingUserDetailsId = null }
            .create()
        dialog.setOnDismissListener {
            if (userDetailsDialog === dialog) {
                userDetailsDialog = null
                pendingUserDetailsId = null
            }
        }
        userDetailsDialog = dialog
        dialog.show()
        if (showdownConnection?.sendGlobal(ShowdownUserDetails.queryCommand(target)) != true) {
            dialog.dismiss()
            session.setConnectionStatus("Showdown connection is not ready yet.")
        }
    }

    private fun renderUserDetails(profile: ShowdownUserDetails.Profile) {
        userDetailsDialog?.dismiss()
        val density = resources.displayMetrics.density
        val summary = TextView(this).apply {
            setTextSize(17f)
            setTextColor(0xffdceff2.toInt())
            setTextIsSelectable(true)
            setPadding((10f * density).toInt(), (8f * density).toInt(), (10f * density).toInt(), (8f * density).toInt())
            text = buildUserDetailsSummary(profile)
        }
        val addFriend = Button(this).apply {
            text = if (profile.friended) "Already friends" else "Add friend"
            isEnabled = !profile.friended
            setOnClickListener {
                if (showdownConnection?.sendGlobal(ShowdownUserDetails.addFriendCommand(profile.name)) == true) {
                    session.setConnectionStatus("Friend request sent to ${profile.name}.")
                    isEnabled = false
                    text = "Request sent"
                } else {
                    session.setConnectionStatus("Showdown connection is not ready yet.")
                }
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@MainActivity).apply {
                addView(summary, -1, -2)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(addFriend, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle(profile.name)
            .setView(root)
            .setNegativeButton("Close", null)
            .setNeutralButton("Message") { _, _ -> showPrivateMessageDialog(profile.name) }
            .setPositiveButton("Challenge") { _, _ -> showChallengeComposer(profile.name) }
            .create()
        dialog.setOnDismissListener {
            if (userDetailsDialog === dialog) {
                userDetailsDialog = null
                pendingUserDetailsId = null
            }
        }
        userDetailsDialog = dialog
        dialog.show()
    }

    private fun buildUserDetailsSummary(profile: ShowdownUserDetails.Profile): String = buildList {
        add(if (profile.online) "Online" else "Offline")
        profile.status.takeIf { it.isNotBlank() }?.let { add("Status: $it") }
        profile.group.takeIf { it.isNotBlank() }?.let { add("Role: $it") }
        profile.customGroup.takeIf { it.isNotBlank() }?.let { add(it) }
        if (profile.autoconfirmed) add("Autoconfirmed account")
        profile.avatar?.let { add("Avatar: $it") }
        if (profile.rooms.isNotEmpty()) {
            add("")
            add("Active rooms")
            profile.rooms.take(16).forEach { room ->
                val battle = listOf(room.playerOne, room.playerTwo).filter { it.isNotBlank() }.joinToString(" vs ")
                add(if (battle.isBlank()) room.id else "${room.id}: $battle")
            }
            if (profile.rooms.size > 16) add("…and ${profile.rooms.size - 16} more")
        }
    }.joinToString("\n")

    private fun normalizeShowdownId(value: String) = value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    private fun showPrivateMessageDialog(target: String) {
        privateMessageDialog?.dismiss()
        privateMessageTarget = target
        val density = resources.displayMetrics.density
        val messages = TextView(this).apply {
            setTextSize(17f)
            setTextColor(0xffe1f0f3.toInt())
            setPadding((10f * density).toInt(), (8f * density).toInt(), (10f * density).toInt(), (8f * density).toInt())
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(messages, -1, -2)
        }
        val input = EditText(this).apply {
            hint = "Message $target"
            setSingleLine(true)
            setTextSize(17f)
        }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { sendPrivateMessage() }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(send, LinearLayout.LayoutParams(-2, -2))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12f * density).toInt(), (8f * density).toInt(), (12f * density).toInt(), 0)
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Message · $target")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (privateMessageDialog === dialog) {
                privateMessageDialog = null
                privateMessageTarget = null
                privateMessageMessagesView = null
                privateMessageInput = null
                privateMessageScroll = null
            }
        }
        privateMessageDialog = dialog
        privateMessageMessagesView = messages
        privateMessageInput = input
        privateMessageScroll = scroll
        dialog.show()
        updatePrivateMessageDialog()
    }

    private fun updatePrivateMessageDialog() {
        val target = privateMessageTarget ?: return
        val content = privateMessageThreads[target].orEmpty().joinToString("\n\n").ifBlank { "No messages yet." }
        privateMessageMessagesView?.text = content
        privateMessageScroll?.post { privateMessageScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendPrivateMessage() {
        val target = privateMessageTarget ?: return
        val text = privateMessageInput?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        if (showdownConnection?.sendGlobal(ShowdownPrivateMessages.command(target, text)) == true) {
            privateMessageThreads.getOrPut(target) { mutableListOf() } += "You: $text"
            privateMessageInput?.setText("")
            updatePrivateMessageDialog()
        } else {
            session.setConnectionStatus("Private message connection is not ready.")
        }
    }

    private fun handlePrivateMessages(lines: List<String>) {
        val local = session.localUsername()
        if (local.isBlank()) return
        lines.mapNotNull(ShowdownPrivateMessages::parse).forEach { message ->
            if (message.sender.equals(local, true) || !message.recipient.equals(local, true)) return@forEach
            val challenge = ShowdownPrivateMessages.challenge(message)
            if (challenge != null) {
                if (lobbyState.incomingChallenges.keys.none { normalizeShowdownId(it) == normalizeShowdownId(message.sender) }) {
                    showIncomingChallengeIfNeeded(message.sender, challenge.format)
                }
                return@forEach
            }
            val target = message.sender
            privateMessageThreads.getOrPut(target) { mutableListOf() } += "${message.sender}: ${message.text}"
            if (privateMessageDialog != null && privateMessageTarget.equals(target, true)) {
                updatePrivateMessageDialog()
            } else {
                showPrivateMessageDialog(target)
            }
        }
    }

    private fun showChatRoomDialog() {
        val existing = chatRoomDialog
        if (existing != null) {
            updateChatRoomDialog()
            return
        }
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24f * density).toInt(), (8f * density).toInt(), (24f * density).toInt(), 0)
        }
        val messages = TextView(this).apply {
            setTextSize(18f)
            setTextColor(0xfff2f6ff.toInt())
            setPadding(0, (8f * density).toInt(), 0, (8f * density).toInt())
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(messages, -1, -2)
        }
        val input = EditText(this).apply {
            hint = "Message room"
            setSingleLine(true)
            setTextSize(18f)
        }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { sendChatRoomMessage() }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(send, LinearLayout.LayoutParams(-2, -2))
        }
        val tournament = Button(this).apply {
            text = "Tournament"
            setOnClickListener { showTournamentDialog() }
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        root.addView(tournament, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
        val dialog = ShowdownDialogBuilder(this)
            .setTitle(chatRoomState.title)
            .setView(root)
            .setNegativeButton("Leave", null)
            .create()
        dialog.setOnDismissListener {
            tournamentDialog?.dismiss()
            tournamentDialog = null
            val roomId = chatRoomState.roomId
            if (roomId != null && !isFinishing) showdownConnection?.send(roomId, "/leave")
            if (chatRoomDialog === dialog) {
                chatRoomDialog = null
                chatRoomMessagesView = null
                chatRoomInput = null
                chatRoomScroll = null
                pendingChatRoomId = null
                chatRoomState.clear()
            }
        }
        chatRoomDialog = dialog
        chatRoomMessagesView = messages
        chatRoomInput = input
        chatRoomScroll = scroll
        dialog.show()
        updateChatRoomDialog()
    }

    private fun updateChatRoomDialog() {
        val dialog = chatRoomDialog ?: return
        dialog.setTitle("${chatRoomState.title} · ${chatRoomState.users.size} online")
        val content = chatRoomState.messages.joinToString("\n") { message ->
            if (message.system) message.text else "${message.speaker}: ${message.text}"
        }.ifBlank { "No messages yet." }
        chatRoomMessagesView?.text = content
        chatRoomScroll?.post { chatRoomScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showLobbyChatDialog() {
        val existing = lobbyChatDialog
        if (existing != null) {
            updateLobbyChatDialog()
            return
        }
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before opening lobby chat.")
            return
        }
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24f * density).toInt(), (8f * density).toInt(), (24f * density).toInt(), 0)
        }
        val messages = TextView(this).apply {
            setTextSize(18f)
            setTextColor(0xfff2f6ff.toInt())
            setPadding(0, (8f * density).toInt(), 0, (8f * density).toInt())
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(messages, -1, -2)
        }
        val input = EditText(this).apply {
            hint = "Message lobby"
            setSingleLine(true)
            setTextSize(18f)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendLobbyChatMessage()
                    true
                } else {
                    false
                }
            }
        }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { sendLobbyChatMessage() }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(send, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = (8f * density).toInt() })
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Lobby chat")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (lobbyChatDialog === dialog) {
                lobbyChatDialog = null
                lobbyChatMessagesView = null
                lobbyChatInput = null
                lobbyChatScroll = null
            }
        }
        lobbyChatDialog = dialog
        lobbyChatMessagesView = messages
        lobbyChatInput = input
        lobbyChatScroll = scroll
        dialog.show()
        showdownConnection?.sendGlobal("/join lobby")
        updateLobbyChatDialog()
    }

    private fun updateLobbyChatDialog() {
        lobbyChatMessagesView?.text = lobbyChatState.messages.joinToString("\n") { message ->
            if (message.system) message.text else "${message.speaker}: ${message.text}"
        }.ifBlank { "No lobby messages yet." }
        lobbyChatScroll?.post { lobbyChatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendLobbyChatMessage() {
        val text = lobbyChatInput?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        if (showdownConnection?.send("lobby", text) == true) {
            lobbyChatInput?.setText("")
        } else {
            session.setConnectionStatus("Lobby chat connection is not ready.")
        }
    }

    private fun showTournamentDialog() {
        tournamentDialog?.let {
            updateTournamentDialog()
            return
        }
        val density = resources.displayMetrics.density
        val status = TextView(this).apply {
            setTextSize(17f)
            setTextColor(0xffc7e8e8.toInt())
            setPadding((8f * density).toInt(), (6f * density).toInt(), (8f * density).toInt(), (10f * density).toInt())
        }
        val details = TextView(this).apply {
            setTextSize(16f)
            setTextColor(0xffe1f0f3.toInt())
            setPadding((8f * density).toInt(), (8f * density).toInt(), (8f * density).toInt(), (8f * density).toInt())
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(details, -1, -2)
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8f * density).toInt(), (8f * density).toInt(), (8f * density).toInt(), 0)
        }
        val join = Button(this).apply {
            text = "Join tournament"
            setOnClickListener { sendTournamentCommand(ShowdownTournamentState.joinCommand()) }
        }
        val leave = Button(this).apply {
            text = "Leave tournament"
            setOnClickListener { sendTournamentCommand(ShowdownTournamentState.leaveCommand()) }
        }
        val validate = Button(this).apply {
            text = "Validate team"
            setOnClickListener { validateTournamentTeam() }
        }
        val ready = Button(this).apply {
            text = "Ready for opponent"
            setOnClickListener { challengeTournamentOpponent() }
        }
        val accept = Button(this).apply {
            text = "Accept challenge"
            setOnClickListener { acceptTournamentChallenge() }
        }
        val cancel = Button(this).apply {
            text = "Cancel challenge"
            setOnClickListener { sendTournamentCommand(ShowdownTournamentState.cancelChallengeCommand()) }
        }
        listOf(join, leave, validate, ready, accept, cancel).forEach { button ->
            actions.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (6f * density).toInt() })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12f * density).toInt(), (4f * density).toInt(), (12f * density).toInt(), 0)
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(actions, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle(chatRoomState.tournament.title(::readableFormatLabel))
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (tournamentDialog === dialog) {
                tournamentDialog = null
                tournamentStatusView = null
                tournamentDetailsView = null
                tournamentJoinButton = null
                tournamentLeaveButton = null
                tournamentValidateButton = null
                tournamentReadyButton = null
                tournamentAcceptButton = null
                tournamentCancelButton = null
            }
        }
        tournamentDialog = dialog
        tournamentStatusView = status
        tournamentDetailsView = details
        tournamentJoinButton = join
        tournamentLeaveButton = leave
        tournamentValidateButton = validate
        tournamentReadyButton = ready
        tournamentAcceptButton = accept
        tournamentCancelButton = cancel
        dialog.show()
        updateTournamentDialog()
    }

    private fun updateTournamentDialog() {
        if (tournamentDialog == null) return
        val state = chatRoomState.tournament.snapshot
        val format = state.format.takeIf { it.isNotBlank() }?.let(::readableFormatLabel) ?: "Showdown"
        val cap = state.playerCap.takeIf { it > 0 }?.let { " · cap $it" }.orEmpty()
        val generator = state.generator.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        tournamentDialog?.setTitle(chatRoomState.tournament.title(::readableFormatLabel))
        tournamentStatusView?.text = "${chatRoomState.tournament.status()} · $format$generator$cap"
        val lines = buildList {
            if (state.isJoined) add("You are in this tournament.")
            state.teambuilderFormat.takeIf { it.isNotBlank() }?.let { add("Format: ${readableFormatLabel(it)}") }
            if (state.challenges.isNotEmpty()) add("Available opponents: ${state.challenges.joinToString(", ")}")
            if (state.challengeBys.isNotEmpty()) add("Waiting for: ${state.challengeBys.joinToString(", ")}")
            state.challenged?.let { add("Challenge received from $it.") }
            state.challenging?.let { add("Challenge sent to $it.") }
            val bracket = chatRoomState.tournament.bracketLines()
            if (bracket.isNotEmpty()) {
                add("Bracket")
                addAll(bracket)
            }
            if (state.events.isNotEmpty()) {
                add("Updates")
                addAll(state.events.asReversed())
            }
        }
        tournamentDetailsView?.text = lines.joinToString("\n").ifBlank { "No tournament details yet." }
        tournamentJoinButton?.visibility = if (state.isActive && !state.isStarted && !state.isJoined) View.VISIBLE else View.GONE
        tournamentLeaveButton?.visibility = if (state.isActive && !state.isStarted && state.isJoined) View.VISIBLE else View.GONE
        tournamentValidateButton?.visibility = if (state.isActive && state.isStarted && state.isJoined) View.VISIBLE else View.GONE
        tournamentReadyButton?.visibility = if (state.isActive && state.isStarted && state.isJoined && state.challenges.isNotEmpty() && state.challenged == null) View.VISIBLE else View.GONE
        tournamentAcceptButton?.visibility = if (state.isActive && state.isStarted && state.isJoined && state.challenged != null) View.VISIBLE else View.GONE
        tournamentCancelButton?.visibility = if (state.isActive && state.isStarted && state.isJoined && state.challenging != null) View.VISIBLE else View.GONE
        tournamentReadyButton?.text = state.challenges.firstOrNull()?.let { "Ready vs $it" } ?: "Ready for opponent"
    }

    private fun sendTournamentCommand(command: String) {
        val roomId = chatRoomState.roomId
        if (roomId == null || showdownConnection?.send(roomId, command) != true) {
            session.setConnectionStatus("Tournament connection is not ready.")
            return
        }
        session.setConnectionStatus("Sent tournament action.")
    }

    private fun challengeTournamentOpponent() {
        val opponents = chatRoomState.tournament.snapshot.challenges
        if (opponents.isEmpty()) return
        if (opponents.size == 1) {
            sendTournamentTeam(ShowdownTournamentState.challengeCommand(opponents.single()))
            return
        }
        ShowdownDialogBuilder(this)
            .setTitle("Choose tournament opponent")
            .setItems(opponents.toTypedArray()) { _, selected ->
                sendTournamentTeam(ShowdownTournamentState.challengeCommand(opponents[selected]))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun acceptTournamentChallenge() {
        if (chatRoomState.tournament.snapshot.challenged.isNullOrBlank()) return
        sendTournamentTeam(ShowdownTournamentState.acceptChallengeCommand())
    }

    private fun validateTournamentTeam() {
        sendTournamentTeam(ShowdownTournamentState.validateTeamCommand())
    }

    private fun sendTournamentTeam(command: String) {
        val tournament = chatRoomState.tournament.snapshot
        val formatId = tournament.teambuilderFormat.ifBlank { tournament.format }.trim()
        val format = session.availableMatchFormats().firstOrNull { it.id.trim().equals(formatId, true) }
        if (format != null && BattleSession.MatchFormat.usesRandomTeams(format)) {
            sendTournamentTeamCommand(null, command)
            return
        }
        if (format == null && BattleSession.MatchFormat.usesRandomTeamsFor(formatId)) {
            sendTournamentTeamCommand(null, command)
            return
        }
        val teams = ShowdownTeamLibraryQuery.matchingFormat(teamLibrary.teams(), formatId)
        if (teams.isEmpty()) {
            session.setConnectionStatus("Save a ${readableFormatLabel(formatId)} team before entering this tournament.")
            showTeamLibrary()
            return
        }
        if (teams.size == 1) {
            sendTournamentTeamCommand(teams.single().packed, command)
        } else {
            showTeamPicker(teams) { team -> sendTournamentTeamCommand(team.packed, command) }
        }
    }

    private fun sendTournamentTeamCommand(packedTeam: String?, command: String) {
        val roomId = chatRoomState.roomId
        val teamSent = showdownConnection?.sendGlobal("/utm ${packedTeam?.takeIf { it.isNotBlank() } ?: "null"}") == true
        if (roomId == null || !teamSent || showdownConnection?.send(roomId, command) != true) {
            session.setConnectionStatus("Could not send the tournament team.")
            return
        }
        session.setConnectionStatus("Tournament action sent.")
    }

    private fun sendChatRoomMessage() {
        val roomId = chatRoomState.roomId ?: return
        val message = chatRoomInput?.text?.toString()?.trim().orEmpty()
        if (message.isBlank()) return
        if (showdownConnection?.send(roomId, message) == true) {
            chatRoomInput?.setText("")
        } else {
            session.setConnectionStatus("Chat room connection is not ready.")
        }
    }

    private fun showTeamPicker(teams: List<ShowdownTeam>, onSelected: (ShowdownTeam) -> Unit) {
        ShowdownDialogBuilder(this)
            .setTitle("Choose a team")
            .setSingleChoiceItems(teams.map { "${it.name} · ${readableFormatLabel(it.format)}" }.toTypedArray(), -1) { dialog, selected ->
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
        showdownConnection?.close()
        showdownConnection = null
        authenticated = false
        serverUserNamed = false
        battleWasRegistered = false
        battleWasParticipant = false
        battleIsSpectator = false
        pendingRegistration = null
        clearPersistedLobbyState()
        session.setConnectionStatus("Battle search cancelled.")
    }

    private fun startLobbyConnection(lobbyCommands: List<String>? = null, lobbyStatus: String? = null) {
        dismissConnectionTransitionDialogs()
        replayLoadRequest = null
        activeReplayLink = null
        pendingBattleJoinRoomId = null
        pendingBattleSearchFormat = null
        pendingBattleSearchLabel = null
        pendingBattleSearchUsesRandomTeams = null
        pendingBattleSearchTeamPacked = null
        chatRoomDialog?.dismiss()
        tournamentDialog?.dismiss()
        tournamentDialog = null
        chatRoomState.clear()
        pendingChatRoomId = null
        session.setReplayMode(false)
        session.prepareForLobby()
        activeBattleRoomId = null
        battleWasRegistered = false
        battleWasParticipant = false
        battleIsSpectator = false
        completedBattleRoomId = null
        battleProtocolReady = false
        pendingDecisionCommand = null
        clearBattlePlayback()
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
        sessionRestorePending = false
        registrationInFlight = false
        latestChallenge = null
        authenticated = false
        serverUserNamed = false
        persistLobbyState(flushToDisk = true)
        connectLobbySocket()
    }

    private fun dismissConnectionTransitionDialogs() {
        roomListDialog?.dismiss()
        roomListDialog = null
        roomListPending = false
        tournamentDirectoryDialog?.dismiss()
        tournamentDirectoryDialog = null
        tournamentDirectoryContentView = null
        tournamentDirectoryLinks = null
        tournamentDialog?.dismiss()
        tournamentDialog = null
        ladderDialog?.dismiss()
        ladderDialog = null
        chatRoomDialog?.dismiss()
        chatRoomDialog = null
        chatRoomMessagesView = null
        chatRoomInput = null
        chatRoomScroll = null
        lobbyChatDialog?.dismiss()
        lobbyChatDialog = null
        lobbyChatMessagesView = null
        lobbyChatInput = null
        lobbyChatScroll = null
        privateMessageDialog?.dismiss()
        privateMessageDialog = null
        userDetailsDialog?.dismiss()
        userDetailsDialog = null
        friendsDialog?.dismiss()
        friendsDialog = null
        friendsContentView = null
        friendsInput = null
        teamRemoteDialog?.dismiss()
        teamRemoteDialog = null
        teamRemoteContentView = null
        teamRemoteLinks = null
        teamLibraryDialog?.dismiss()
        teamLibraryDialog = null
        teamEditorDialog?.dismiss()
        teamEditorDialog = null
        teamEditorShareView = null
        pokedexDialog?.dismiss()
        pokedexDialog = null
        accountDialog?.dismiss()
        accountDialog = null
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
        val hasSavedBattleIdentity = savedInstanceState?.containsKey("battle_participant") == true || preferences.contains("battle_participant")
        battleWasRegistered = if (savedInstanceState?.containsKey("battle_registered") == true) {
            savedInstanceState.getBoolean("battle_registered")
        } else {
            preferences.getBoolean("battle_registered", credentialsStore.load() != null || loginClient.hasSession())
        }
        battleWasParticipant = if (savedInstanceState?.containsKey("battle_participant") == true) {
            savedInstanceState.getBoolean("battle_participant")
        } else {
            preferences.getBoolean("battle_participant", false)
        }
        battleIsSpectator = if (savedInstanceState?.containsKey("battle_spectator") == true) {
            savedInstanceState.getBoolean("battle_spectator")
        } else {
            preferences.getBoolean("battle_spectator", false)
        }
        if (activeBattleRoomId != null && !hasSavedBattleIdentity && !battleWasRegistered && !battleIsSpectator) battleWasParticipant = true
        pendingBattleSearchFormat = savedInstanceState?.getString("pending_battle_search_format")
            ?: preferences.getString("pending_battle_search_format", null)
        pendingBattleSearchLabel = savedInstanceState?.getString("pending_battle_search_label")
            ?: preferences.getString("pending_battle_search_label", null)
        pendingBattleSearchUsesRandomTeams = if (savedInstanceState?.containsKey("pending_battle_search_random") == true) {
            savedInstanceState.getBoolean("pending_battle_search_random")
        } else {
            preferences.getBoolean("pending_battle_search_random", false)
        }.takeIf { pendingBattleSearchFormat != null }
        pendingBattleSearchTeamPacked = savedInstanceState?.getString("pending_battle_search_team")
            ?: preferences.getString("pending_battle_search_team", null)
        val battlePlayerSlot = savedInstanceState?.getString("battle_player_slot") ?: preferences.getString("battle_player_slot", null)
        val recoveryMode = ShowdownBattleRecovery.mode(activeBattleRoomId, battleWasRegistered, battleWasParticipant, battleIsSpectator)
        if (recoveryMode == ShowdownBattleRecovery.Mode.GUEST_SPECTATOR) {
            battleIsSpectator = true
            battleWasParticipant = false
        }
        if (activeBattleRoomId != null) session.restoreBattlePlayerSlot(battlePlayerSlot)
        completedBattleRoomId = savedInstanceState?.getString("completed_battle_room")
        pendingDecisionCommand = savedInstanceState?.getString("pending_decision_command")
            ?: preferences.getString("pending_decision_command", null)
        pendingDecisionSentConnection = null
        battleProtocolReady = false
        session.setLiveBattleActive(false)
        if (battleIsSpectator) session.setSpectatorMode(true)
        connectLobbySocket()
    }

    private fun persistLobbyState(flushToDisk: Boolean = false) {
        rememberBattleIdentity()
        val editor = getSharedPreferences("showdown_live", MODE_PRIVATE).edit()
            .putBoolean("maintain_connection", shouldMaintainConnection)
            .putBoolean("pending_search", pendingSearch)
            .putString("pending_search_team", pendingSearchTeamPacked)
            .putString("pending_lobby_status", pendingLobbyStatus)
            .putString("pending_lobby_commands", encodeLobbyCommands(pendingLobbyCommands))
            .putString("reconnect_lobby_commands", encodeLobbyCommands(reconnectLobbyCommands))
            .putString("active_search_format", activeSearchFormat)
            .putString("active_battle_room", activeBattleRoomId)
            .putBoolean("battle_registered", battleWasRegistered)
            .putBoolean("battle_participant", battleWasParticipant)
            .putBoolean("battle_spectator", battleIsSpectator)
            .putString("pending_battle_search_format", pendingBattleSearchFormat)
            .putString("pending_battle_search_label", pendingBattleSearchLabel)
            .putBoolean("pending_battle_search_random", pendingBattleSearchUsesRandomTeams == true)
            .putString("pending_battle_search_team", pendingBattleSearchTeamPacked)
            .putString("battle_player_slot", activeBattleRoomId?.let { session.battlePlayerSlot() })
            .putString("pending_decision_command", pendingDecisionCommand)
        if (flushToDisk) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    private fun clearPersistedLobbyState() {
        getSharedPreferences("showdown_live", MODE_PRIVATE).edit().clear().commit()
    }

    private fun clearBattleRoomState() {
        activeBattleRoomId = null
        battleWasRegistered = false
        battleWasParticipant = false
        battleIsSpectator = false
        completedBattleRoomId = null
        pendingBattleJoinRoomId = null
        pendingBattleSearchFormat = null
        pendingBattleSearchLabel = null
        pendingBattleSearchUsesRandomTeams = null
        pendingBattleSearchTeamPacked = null
        battleProtocolReady = false
        pendingDecisionCommand = null
        pendingDecisionSentConnection = null
        activeSearchFormat = null
        pendingSearch = false
        pendingSearchTeamPacked = null
        pendingLobbyCommands = null
        pendingLobbyStatus = null
        reconnectLobbyCommands = null
        clearPersistedLobbyState()
        clearBattlePlayback()
        session.prepareForLobby()
    }

    private fun rememberBattleIdentity() {
        if (activeBattleRoomId == null) return
        battleWasRegistered = battleWasRegistered || serverUserNamed
        if (!battleIsSpectator) battleWasParticipant = battleWasParticipant || session.isBattleParticipant()
    }

    private fun battleProtocolIdentifiesLocalPlayer(lines: List<String>): Boolean = battleProtocolPlayerSlot(lines) != null

    private fun battleProtocolPlayerSlot(lines: List<String>): String? {
        val localUsername = session.localUsername().trim()
        if (localUsername.isBlank()) return null
        return lines.asSequence()
            .map { it.split('|') }
            .firstOrNull { fields ->
                fields.getOrNull(1) == "player" && fields.getOrNull(3)?.trim()?.equals(localUsername, true) == true
            }
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf { it.matches(Regex("p[1-4]")) }
    }

    private fun downgradeBattleRecoveryToGuest() {
        if (activeBattleRoomId != null) {
            battleWasRegistered = false
            battleWasParticipant = false
            battleIsSpectator = true
            pendingDecisionCommand = null
            pendingDecisionSentConnection = null
            session.setSpectatorMode(true)
        }
        persistLobbyState(flushToDisk = true)
    }

    private fun encodeLobbyCommands(commands: List<String>?) = commands?.joinToString("\u0000")

    private fun decodeLobbyCommands(commands: String?) = commands?.split('\u0000')?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }

    private fun connectLobbySocket() {
        val previousConnection = showdownConnection
        showdownConnection = null
        latestChallenge = null
        reconnectHandler.removeCallbacks(sessionRestoreTimeout)
        loginInFlight = false
        sessionRestorePending = loginClient.hasSession() && credentialsStore.load() == null
        registrationInFlight = false
        previousConnection?.close()
        lateinit var connection: ShowdownConnection
        connection = ShowdownConnection(serverEndpoint, object : ShowdownConnection.Listener {
            override fun onConnectionStateChanged(state: ShowdownConnection.State, detail: String) {
                runOnUiThread {
                    if (showdownConnection !== connection) return@runOnUiThread
                    if (state == ShowdownConnection.State.DISCONNECTED || state == ShowdownConnection.State.FAILED) {
                        reconnectHandler.removeCallbacks(sessionRestoreTimeout)
                        sessionRestorePending = false
                        val preserveBattleSurface = activeBattleRoomId != null &&
                            shouldMaintainConnection &&
                            !session.isBattleFinished()
                        battleProtocolReady = false
                        pendingDecisionSentConnection = null
                        if (!preserveBattleSurface) {
                            pendingDecisionCommand = null
                            session.setLiveBattleActive(false)
                        }
                        pendingTeamValidationFormat = null
                        latestChallenge = null
                        registrationInFlight = false
                        serverUserNamed = false
                        chatRoomDialog?.dismiss()
                        chatRoomState.clear()
                        pendingChatRoomId = null
                        lobbyChatDialog?.dismiss()
                        lobbyChatState.clear()
                    }
                    val status = when (state) {
                        ShowdownConnection.State.CONNECTING -> "Connecting to ${serverEndpoint.displayName}…"
                        ShowdownConnection.State.CONNECTED -> {
                            reconnectAttempt = 0
                            reconnectScheduled = false
                            if (pendingRegistration != null) "Preparing account creation on ${serverEndpoint.displayName}…"
                            else if (credentialsStore.load() == null) "Joining ${serverEndpoint.displayName}…"
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
                    if (tournamentDirectoryState.applyProtocol(roomId, lines)) updateTournamentDirectoryDialog()
                    if (friendsState.applyProtocol(roomId, lines)) updateFriendsDialog()
                    if (teamRemoteState.applyProtocol(roomId, lines) && teamRemoteDialog != null) updateTeamRemoteDialog()
                    lines.mapNotNull(ShowdownAuthentication::userUpdate).firstOrNull()?.let { update ->
                        session.setLocalUsername(update.username)
                        serverUserNamed = update.named
                        val hasPendingLobbyWork = pendingSearch ||
                            activeSearchFormat != null ||
                            activeBattleRoomId != null ||
                            pendingLobbyCommands?.isNotEmpty() == true ||
                            reconnectLobbyCommands?.isNotEmpty() == true
                        if (sessionRestorePending && update.named && credentialsStore.load() == null) {
                            sessionRestorePending = false
                            authenticated = true
                            sendPendingLobbyCommands(connection)
                        } else if (!sessionRestorePending && (credentialsStore.load() == null || update.named)) {
                            authenticated = true
                            sendPendingLobbyCommands(connection)
                            if (!hasPendingLobbyWork) {
                                session.setConnectionStatus(
                                    if (update.named) "Signed in as ${update.username}." else "Ready for a battle."
                                )
                            }
                        }
                    }
                    lines.mapNotNull(ShowdownAuthentication::challenge).firstOrNull()?.let { challenge ->
                        latestChallenge = challenge
                        pendingRegistration?.takeUnless { registrationInFlight }?.let { registration ->
                            submitRegistration(connection, registration, challenge)
                        }
                        if (loginClient.hasSession() && credentialsStore.load() == null && !loginInFlight && !registrationInFlight && pendingRegistration == null) {
                            sessionRestorePending = true
                            loginInFlight = true
                            loginClient.upkeep(serverEndpoint, challenge) { result ->
                                runOnUiThread {
                                    if (showdownConnection !== connection) return@runOnUiThread
                                    loginInFlight = false
                                    result.onSuccess { upkeep ->
                                        val restored = upkeep?.username?.takeIf { it.isNotBlank() }
                                            ?.let { username ->
                                                upkeep.assertion?.takeIf { assertion ->
                                                    assertion.isNotBlank() && !assertion.startsWith(";")
                                                }?.let { assertion -> username to assertion }
                                            }
                                        if (restored == null) {
                                            sessionRestorePending = false
                                            loginClient.clearSession()
                                            downgradeBattleRecoveryToGuest()
                                            authenticated = true
                                            sendPendingLobbyCommands(connection)
                                            session.setConnectionStatus("Joining ${serverEndpoint.displayName}…")
                                        } else if (connection.sendGlobal(ShowdownAuthentication.renameCommand(restored.first, restored.second))) {
                                            reconnectHandler.removeCallbacks(sessionRestoreTimeout)
                                            reconnectHandler.postDelayed(sessionRestoreTimeout, SESSION_RESTORE_TIMEOUT_MILLIS)
                                            session.setConnectionStatus("Restoring your Showdown session…")
                                        } else {
                                            sessionRestorePending = false
                                            loginClient.clearSession()
                                            downgradeBattleRecoveryToGuest()
                                            authenticated = true
                                            sendPendingLobbyCommands(connection)
                                            session.setConnectionStatus("Joining ${serverEndpoint.displayName}…")
                                        }
                                    }
                                    result.onFailure {
                                        sessionRestorePending = false
                                        loginClient.clearSession()
                                        downgradeBattleRecoveryToGuest()
                                        authenticated = true
                                        sendPendingLobbyCommands(connection)
                                        session.setConnectionStatus("Joining ${serverEndpoint.displayName}…")
                                    }
                                }
                            }
                        }
                        credentialsStore.load()?.takeUnless { loginInFlight || registrationInFlight || pendingRegistration != null }?.let { credentials ->
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
                    val deletedTeamId = lines.mapNotNull(ShowdownTeamRemote::parseDeleted).firstOrNull()
                    val privacyUpdate = lines.mapNotNull(ShowdownTeamRemote::parsePrivacyUpdate).firstOrNull()
                    val upload = lines.mapNotNull(ShowdownTeamRemote::parseUpload).firstOrNull()
                    val replayUrl = lines.mapNotNull(ShowdownReplayImporter::uploadUrl).firstOrNull()
                    val validationResult = pendingTeamValidationFormat?.let { format ->
                        ShowdownTeamValidation.response(lines)?.let { format to it }
                    }
                    val teamResponseHandled = deletedTeamId != null || privacyUpdate != null || upload != null || replayUrl != null || validationResult != null
                    deletedTeamId?.let { remoteId ->
                        pendingTeamDelete?.takeIf { it.remoteId == remoteId }?.let { pending ->
                            teamLibrary.remove(pending.localId)
                            pendingTeamDelete = null
                            teamEditorDialog?.dismiss()
                            session.setConnectionStatus("Remote team deleted.")
                        }
                    }
                    privacyUpdate?.let { update ->
                        pendingTeamPrivacy?.takeIf { it.remoteId == update.remoteId }?.let { pending ->
                            teamLibrary.markPrivacy(update.remoteId, update.privateKey)
                            teamEditorShareView?.text = "Share URL: ${ShowdownTeamRemote.shareUrl(update.remoteId, update.privateKey)}"
                            teamPrivacyButton?.text = if (update.privateKey == null) "Make private" else "Make public"
                            teamPrivacyButton?.isEnabled = true
                            teamPrivacyButton = null
                            pendingTeamPrivacy = null
                            session.setConnectionStatus("Team privacy updated.")
                        }
                    }
                    upload?.let { result ->
                        pendingTeamUpload?.let { pending ->
                            teamLibrary.markUploaded(pending.localId, result.remoteId, result.privateKey, pending.packed)
                            teamEditorShareView?.text = "Share URL: ${ShowdownTeamRemote.shareUrl(result.remoteId, result.privateKey)}"
                            session.setConnectionStatus("Team uploaded: ${ShowdownTeamRemote.shareUrl(result.remoteId, result.privateKey)}")
                            pendingTeamUpload = null
                            teamUploadButtons.forEach { it.isEnabled = true }
                            teamUploadButtons = emptyList()
                        }
                    }
                    validationResult?.let { (format, result) ->
                        pendingTeamValidationFormat = null
                        showTeamValidationResult(format, result)
                    }
                    replayUrl?.let(::showReplayUploaded)
                    lines.mapNotNull(ShowdownAuthentication::serverError).firstOrNull()
                        ?.takeUnless { teamResponseHandled }
                        ?.let { error ->
                            if (sessionRestorePending) {
                                sessionRestorePending = false
                                reconnectHandler.removeCallbacks(sessionRestoreTimeout)
                                loginInFlight = false
                                loginClient.clearSession()
                                downgradeBattleRecoveryToGuest()
                                authenticated = true
                                sendPendingLobbyCommands(connection)
                                session.setConnectionStatus("Joining ${serverEndpoint.displayName}…")
                                return@let
                            }
                            loginInFlight = false
                            registrationInFlight = false
                            pendingRegistration = null
                            pendingSearch = false
                            pendingLobbyCommands = null
                            pendingLobbyStatus = null
                            reconnectLobbyCommands = null
                            pendingTeamUpload = null
                            pendingTeamPrivacy = null
                            pendingTeamDelete = null
                            pendingTeamValidationFormat = null
                            teamUploadButtons.forEach { it.isEnabled = true }
                            teamUploadButtons = emptyList()
                            teamPrivacyButton?.isEnabled = true
                            teamPrivacyButton = null
                            session.setConnectionStatus(error)
                        }
                    if (roomId == null || roomId == "lobby") {
                        if (lobbyChatState.applyProtocol("lobby", lines) && lobbyChatDialog != null) updateLobbyChatDialog()
                        session.applyLobbyChat(lines)
                        session.applyServerFormats(lines)
                        getSharedPreferences("showdown", MODE_PRIVATE).edit()
                            .putString("match_format", session.matchFormat.id)
                            .putString("match_format_label", readableFormatLabel(session.matchFormat.id))
                            .apply()
                        val previousChallenges = lobbyState.incomingChallenges
                        val challengesUpdated = lines.any { it.startsWith("|updatechallenges|") }
                        lobbyState.applyProtocol(lines)
                        handlePrivateMessages(lines)
                        lines.mapNotNull(ShowdownUserDetails::parse).firstOrNull()?.let { profile ->
                            val requested = pendingUserDetailsId
                            if (requested != null && (normalizeShowdownId(profile.userid) == requested || normalizeShowdownId(profile.name) == requested)) {
                                pendingUserDetailsId = null
                                renderUserDetails(profile)
                            }
                        }
                        if (roomListPending && lines.any { it.startsWith("|queryresponse|rooms|") || it.startsWith("|queryresponse|roomlist|") }) renderRoomListDialog()
                        if (ladderDialog != null && lines.any { it.startsWith("|queryresponse|laddertop|") }) renderLadderDialog()
                        if (lines.any { it.startsWith("|updatesearch|") }) {
                            lobbyState.battleForReconnect(
                                activeBattleRoomId,
                                pendingBattleJoinRoomId,
                                pendingBattleSearchFormat != null
                            )?.let { matchedRoomId ->
                                joinMatchedBattle(connection, matchedRoomId)
                            }
                        }
                        lobbyState.incomingChallenges.keys.firstOrNull { it !in previousChallenges }?.let { username ->
                            val format = lobbyState.incomingChallenges[username].orEmpty()
                            showIncomingChallengeIfNeeded(username, format)
                        }
                        if (challengesUpdated && lobbyState.incomingChallenges.isEmpty()) displayedIncomingChallenge = null
                        val outgoingChallenge = lobbyState.outgoingChallenge
                        if (outgoingChallenge != null && outgoingChallenge != displayedOutgoingChallenge) {
                            displayedOutgoingChallenge = outgoingChallenge
                            showOutgoingChallenge(outgoingChallenge)
                        } else if (outgoingChallenge == null) {
                            displayedOutgoingChallenge = null
                        }
                        if (lobbyState.isSearching(session.matchFormat.id)) {
                            session.setConnectionStatus("Searching ${readableFormatLabel(session.matchFormat.id)}…")
                        }
                    }
                    if (roomId?.startsWith("battle-") == true) {
                        val noInitMessage = ShowdownLobbyState.noInitReason(lines)
                        if (noInitMessage != null && roomId != leftBattleRoomId &&
                            (roomId == activeBattleRoomId || roomId == pendingBattleJoinRoomId)
                        ) {
                            val searchFormat = pendingBattleSearchFormat
                            val searchLabel = pendingBattleSearchLabel
                            val searchUsesRandomTeams = pendingBattleSearchUsesRandomTeams
                            val searchTeam = pendingBattleSearchTeamPacked
                            reconnectHandler.removeCallbacks(battleRejoinTimeout)
                            lobbyState.clearBattle(roomId)
                            leftBattleRoomId = roomId
                            clearBattleRoomState()
                            if (searchFormat != null) {
                                session.setConnectionStatus("That battle ended before you could join. Finding another battle…")
                                beginBattleSearch(searchFormat, searchTeam, searchLabel, searchUsesRandomTeams)
                            } else {
                                session.setConnectionStatus(noInitMessage)
                            }
                            return@runOnUiThread
                        }
                        val startsBattle = lines.any { it.startsWith("|init|battle") }
                        val rejectedJoin = lines.any { it.startsWith("|noinit|") }
                        if (rejectedJoin && pendingBattleJoinRoomId == roomId) pendingBattleJoinRoomId = null
                        val confirmedJoin = startsBattle && pendingBattleJoinRoomId == roomId
                        if (roomId == leftBattleRoomId) {
                            if (!confirmedJoin) return@runOnUiThread
                            leftBattleRoomId = null
                        }
                        if (confirmedJoin) {
                            pendingBattleJoinRoomId = null
                            pendingBattleSearchFormat = null
                            pendingBattleSearchLabel = null
                            pendingBattleSearchUsesRandomTeams = null
                            pendingBattleSearchTeamPacked = null
                        }
                        if (startsBattle) reconnectHandler.removeCallbacks(battleRejoinTimeout)
                        activeBattleRoomId = roomId
                        if (!battleIsSpectator) {
                            battleWasParticipant = battleWasParticipant || battleProtocolIdentifiesLocalPlayer(lines)
                        }
                        battleProtocolPlayerSlot(lines)?.let(session::restoreBattlePlayerSlot)
                        if (startsBattle) {
                            rememberBattleIdentity()
                            persistLobbyState(flushToDisk = true)
                        }
                        activeSearchFormat = null
                        reconnectLobbyCommands = null
                        if (startsBattle) battleProtocolReady = true
                        session.setLiveBattleActive(activeBattleRoomId == roomId && battleProtocolReady)
                        if (battleIsSpectator) session.setSpectatorMode(true)
                        reconcilePendingDecisionCommand(lines)
                        enqueueBattlePlayback(connection, roomId, lines)
                    }
                    if (roomId != null && !roomId.startsWith("battle-") && !roomId.startsWith("view-friends-") && !roomId.startsWith("view-tournaments") && !roomId.startsWith("view-teams-") && (roomId != "lobby" || lines.any { it == "|init|chat" || it.startsWith("|title|") })) {
                        val changed = chatRoomState.applyProtocol(roomId, lines)
                        if (changed && pendingChatRoomId == roomId) {
                            pendingChatRoomId = null
                            showChatRoomDialog()
                        } else if (changed && chatRoomDialog != null && chatRoomState.roomId == roomId) {
                            updateChatRoomDialog()
                        }
                        if (changed && tournamentDialog != null && chatRoomState.roomId == roomId) updateTournamentDialog()
                    }
                }
            }
        })
        showdownConnection = connection
        connection.connect()
    }

    private fun joinMatchedBattle(connection: ShowdownConnection, roomId: String) {
        if (activeBattleRoomId != null || !connection.sendGlobal(ShowdownLobbyState.joinBattleCommand(roomId))) return
        session.prepareForLobby()
        pendingBattleJoinRoomId = roomId
        pendingBattleSearchFormat = activeSearchFormat
        pendingBattleSearchLabel = activeSearchFormat?.let { readableFormatLabel(session.matchFormat.id) }
        pendingBattleSearchUsesRandomTeams = activeSearchFormat?.let { BattleSession.MatchFormat.usesRandomTeams(session.matchFormat) }
        pendingBattleSearchTeamPacked = pendingSearchTeamPacked
        activeSearchFormat?.let(lobbyState::clearSearch)
        activeSearchFormat = null
        pendingSearch = false
        pendingSearchTeamPacked = null
        pendingLobbyCommands = null
        pendingLobbyStatus = null
        reconnectLobbyCommands = null
        activeBattleRoomId = roomId
        battleWasRegistered = serverUserNamed || credentialsStore.load() != null || loginClient.hasSession()
        battleWasParticipant = true
        battleIsSpectator = false
        battleProtocolReady = false
        pendingDecisionCommand = null
        pendingDecisionSentConnection = null
        session.setLiveBattleActive(false)
        session.setConnectionStatus("Joining battle…")
        persistLobbyState(flushToDisk = true)
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
        val hasPendingSearchCommand = pendingLobbyCommands?.any { it.startsWith("/search ") } == true
        val searchFormat = if (pendingSearch || activeSearchFormat != null || hasPendingSearchCommand) {
            ensureSearchableMatchFormat()
        } else {
            session.matchFormat
        }
        val rawCommands = pendingLobbyCommands ?: when {
            pendingSearch -> {
                val usesRandomTeams = BattleSession.MatchFormat.usesRandomTeams(searchFormat)
                if (!usesRandomTeams && pendingSearchTeamPacked.isNullOrBlank()) {
                    pendingSearch = false
                    session.setConnectionStatus("Save a team for ${readableFormatLabel(searchFormat.id)} before searching.")
                    return
                }
                ShowdownLobbyState.searchCommands(searchFormat.id, pendingSearchTeamPacked)
            }
            activeSearchFormat != null -> {
                val serverFormat = session.availableMatchFormats()
                    .firstOrNull { it.id.trim().equals(activeSearchFormat?.trim(), true) && it.canSearch }
                ShowdownLobbyState.searchCommands(serverFormat?.id ?: searchFormat.id, pendingSearchTeamPacked)
            }
            activeBattleRoomId != null -> listOf(ShowdownLobbyState.joinBattleCommand(activeBattleRoomId!!))
            reconnectLobbyCommands != null -> reconnectLobbyCommands!!
            else -> return
        }
        val commands = rawCommands.map { command ->
            if (!command.startsWith("/search ")) {
                command
            } else {
                val requestedFormat = command.removePrefix("/search ").trim()
                session.availableMatchFormats()
                    .firstOrNull { it.id.trim().equals(requestedFormat.trim(), true) && it.canSearch }
                    ?.id
                    ?.let { "/search $it" }
                    ?: "/search ${searchFormat.id}"
            }
        }
        val rejoiningBattle = activeBattleRoomId != null && commands == listOf(ShowdownLobbyState.joinBattleCommand(activeBattleRoomId!!))
        val searching = commands.any { it.startsWith("/search ") }
        val sent = commands.all(connection::sendGlobal)
        if (!sent) {
            session.setConnectionStatus("Could not send the Showdown lobby command.")
            return
        }
        pendingLobbyCommands = null
        pendingBattleJoinRoomId = commands.firstOrNull { it.startsWith("/join battle-") }?.removePrefix("/join ")
        val status = pendingLobbyStatus
        pendingSearch = false
        pendingLobbyStatus = null
        if (status != null) {
            session.setConnectionStatus(status)
        } else if (searching) {
            activeSearchFormat = commands.first { it.startsWith("/search ") }.removePrefix("/search ")
            session.setConnectionStatus("Searching ${readableFormatLabel(session.matchFormat.id)}…")
        } else if (rejoiningBattle) {
            session.setConnectionStatus("Rejoining battle…")
            reconnectHandler.removeCallbacks(battleRejoinTimeout)
            reconnectHandler.postDelayed(battleRejoinTimeout, BATTLE_REJOIN_TIMEOUT_MILLIS)
        } else if (reconnectLobbyCommands != null) {
            session.setConnectionStatus("Restoring challenge…")
        }
        persistLobbyState(flushToDisk = rejoiningBattle)
    }

    private fun showChallengeComposer(prefilledUsername: String? = null) {
        val username = EditText(this).apply {
            hint = "Enter a username"
            setSingleLine(true)
            setText(prefilledUsername.orEmpty())
        }
        var selectedFormat = session.matchFormat
        val formatButton = Button(this).apply {
            text = readableFormatLabel(selectedFormat.id)
            isAllCaps = false
            setOnClickListener {
                showFormatPicker(selectedFormat, searchOnly = false) { format ->
                    selectedFormat = format
                    text = readableFormatLabel(format.id)
                }
            }
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "Battle format"
                setTextColor(0xffdceff2.toInt())
                setTextSize(16f)
                setPadding(0, 0, 0, (8f * resources.displayMetrics.density).toInt())
            }, LinearLayout.LayoutParams(-1, -2))
            addView(formatButton, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(this@MainActivity).apply {
                text = "Opponent username"
                setTextColor(0xffdceff2.toInt())
                setTextSize(16f)
                setPadding(0, (16f * resources.displayMetrics.density).toInt(), 0, (8f * resources.displayMetrics.density).toInt())
            }, LinearLayout.LayoutParams(-1, -2))
            addView(username, LinearLayout.LayoutParams(-1, -2))
        }
        ShowdownDialogBuilder(this)
            .setTitle("Challenge player")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Challenge") { _, _ -> beginChallenge(username.text.toString(), selectedFormat) }
            .show()
    }

    private fun beginChallenge(username: String, format: BattleSession.MatchFormat = session.matchFormat) {
        val target = username.trim()
        if (target.isBlank()) {
            session.setConnectionStatus("Enter a username to challenge.")
            return
        }
        val teams = ShowdownTeamLibraryQuery.matchingFormat(teamLibrary.teams(), format.id)
        if (BattleSession.MatchFormat.usesRandomTeams(format)) {
            startChallenge(target, format, null)
        } else if (teams.isEmpty()) {
            session.setConnectionStatus("Save a ${readableFormatLabel(format.id)} team before challenging.")
            showTeamLibrary()
        } else if (teams.size == 1) {
            startChallenge(target, format, teams.single().packed)
        } else {
            showTeamPicker(teams) { startChallenge(target, format, it.packed) }
        }
    }

    private fun startChallenge(username: String, format: BattleSession.MatchFormat, packedTeam: String?) {
        val normalizedFormat = format.copy(
            id = format.id.trim(),
            label = readableFormatLabel(format.id),
            menuLabel = format.menuLabel.trim().takeUnless { it.isBlank() || it.equals(format.id.trim(), true) }
                ?: readableFormatLabel(format.id)
        )
        startLobbyConnection(
            ShowdownLobbyState.challengeCommands(username, normalizedFormat.id, packedTeam),
            "${normalizedFormat.label} challenge sent to $username."
        )
    }

    private fun readableFormatLabel(format: String): String = ShowdownTeamLibraryQuery.displayFormat(
        format,
        session.availableMatchFormats()
    )

    private fun challengeMatchFormat(format: String): BattleSession.MatchFormat {
        return ShowdownTeamLibraryQuery.matchFormat(format, session.availableMatchFormats())
    }

    private fun showIncomingChallenge(username: String, format: String) {
        ShowdownDialogBuilder(this)
            .setTitle("Battle challenge")
            .setMessage("$username challenged you to ${readableFormatLabel(format)}.")
            .setNegativeButton("Reject") { _, _ -> sendLobbyCommand(ShowdownLobbyState.rejectChallengeCommand(username), "Challenge rejected.") }
            .setNeutralButton("Ignore", null)
            .setPositiveButton("Accept") { _, _ -> beginAcceptChallenge(username, format) }
            .show()
    }

    private fun showIncomingChallengeIfNeeded(username: String, format: String) {
        val challengeKey = "${normalizeShowdownId(username)}|${format.trim().lowercase()}"
        if (displayedIncomingChallenge == challengeKey) return
        displayedIncomingChallenge = challengeKey
        showIncomingChallenge(username, format)
    }

    private fun showOutgoingChallenge(challenge: ShowdownLobbyState.OutgoingChallenge) {
        ShowdownDialogBuilder(this)
            .setTitle("Challenge pending")
            .setMessage("Waiting for ${challenge.username} to accept your ${readableFormatLabel(challenge.format)} challenge.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Cancel challenge") { _, _ ->
                sendLobbyCommand(ShowdownLobbyState.cancelChallengeCommand(challenge.username), "Challenge cancelled.")
            }
            .show()
    }

    private fun beginAcceptChallenge(username: String, format: String) {
        val matchFormat = challengeMatchFormat(format)
        val teams = ShowdownTeamLibraryQuery.matchingFormat(teamLibrary.teams(), matchFormat.id)
        if (BattleSession.MatchFormat.usesRandomTeams(matchFormat)) {
            sendLobbyCommands(ShowdownLobbyState.acceptChallengeCommands(username, null), "Challenge accepted.")
        } else if (teams.isEmpty()) {
            session.setConnectionStatus("Save a ${readableFormatLabel(matchFormat.id)} team before accepting.")
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
        if (!authenticated || connection == null) {
            session.setConnectionStatus("Connect to Showdown before using lobby challenges.")
            return
        }
        if (!connection.isTransportReady()) {
            pendingLobbyCommands = commands
            when {
                commands.any { it.startsWith("/accept ") || it.startsWith("/challenge ") } -> reconnectLobbyCommands = commands
                commands.any { it.startsWith("/cancelchallenge ") || it.startsWith("/reject ") } -> reconnectLobbyCommands = null
            }
            pendingLobbyStatus = status
            persistLobbyState()
            session.setConnectionStatus("Waiting for the Showdown connection…")
            return
        }
        if (!commands.all(connection::sendGlobal)) {
            session.setConnectionStatus("Could not send the Showdown lobby command.")
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
        ShowdownDialogBuilder(this)
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
                    loginClient.clearSession()
                    sessionStore.clear()
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
        val density = resources.displayMetrics.density
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
            setPadding((56f * density).toInt(), (8f * density).toInt(), (56f * density).toInt(), 0)
            addView(username)
            addView(password)
        }
        val findUser = Button(this).apply {
            text = "Find a user"
            setOnClickListener {
                if (!authenticated || !serverUserNamed) {
                    session.setConnectionStatus("Sign in to look up another player.")
                } else {
                    accountDialog?.dismiss()
                    showFindUserComposer()
                }
            }
        }
        val register = Button(this).apply {
            text = "Create account"
            setOnClickListener {
                accountDialog?.dismiss()
                showRegistrationDialog()
            }
        }
        val resources = Button(this).apply {
            text = "Info & resources"
            setOnClickListener { accountDialog?.dismiss(); showResourcesDialog() }
        }
        val friends = Button(this).apply {
            text = "Friends"
            setOnClickListener {
                if (!authenticated || !serverUserNamed) {
                    session.setConnectionStatus("Sign in to use Friends.")
                } else {
                    accountDialog?.dismiss()
                    showFriendsDialog()
                }
            }
        }
        val changePassword = Button(this).apply {
            text = "Change password"
            isEnabled = authenticated && serverUserNamed
            setOnClickListener {
                accountDialog?.dismiss()
                showChangePasswordDialog()
            }
        }
        val accountTools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((56f * density).toInt(), (12f * density).toInt(), (56f * density).toInt(), 0)
            addView(register)
            addView(findUser)
            addView(resources)
            addView(friends)
            if (authenticated && serverUserNamed) addView(changePassword)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(fields)
            addView(accountTools)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, -1, -2)
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Showdown account")
            .setView(scroll)
            .setNeutralButton("Sign out") { _, _ -> signOut() }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save credentials") { _, _ ->
                val value = ShowdownCredentials(username.text.toString().trim(), password.text.toString())
                if (value.username.isBlank() || value.password.isBlank()) {
                    session.setConnectionStatus("Enter both a username and password.")
                } else {
                    credentialsStore.save(value)
                    session.setConnectionStatus("Showdown account saved. It will sign in when you connect.")
                }
            }
            .create()
        accountDialog = dialog
        dialog.setOnDismissListener {
            if (accountDialog === dialog) accountDialog = null
        }
        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val density = resources.displayMetrics.density
        val oldPassword = EditText(this).apply {
            hint = "Current password"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPassword = EditText(this).apply {
            hint = "New password"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmation = EditText(this).apply {
            hint = "Repeat new password"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((56f * density).toInt(), (8f * density).toInt(), (56f * density).toInt(), 0)
            addView(oldPassword)
            addView(newPassword)
            addView(confirmation)
        }
        ShowdownDialogBuilder(this)
            .setTitle("Change password")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Change") { _, _ ->
                changePassword(oldPassword.text.toString(), newPassword.text.toString(), confirmation.text.toString())
            }
            .show()
    }

    private fun changePassword(oldPassword: String, password: String, confirmation: String) {
        if (!authenticated || !serverUserNamed) {
            session.setConnectionStatus("Sign in to change your password.")
            return
        }
        if (oldPassword.isBlank() || password.isBlank() || confirmation.isBlank()) {
            session.setConnectionStatus("Enter your current password and both new-password fields.")
            return
        }
        if (password != confirmation) {
            session.setConnectionStatus("The new passwords do not match.")
            return
        }
        if (password.replace(Regex("\\s"), "").length < 5) {
            session.setConnectionStatus("Showdown passwords must be at least 5 characters.")
            return
        }
        session.setConnectionStatus("Changing your Showdown password…")
        loginClient.changePassword(serverEndpoint, oldPassword, password, confirmation) { result ->
            runOnUiThread {
                result.onSuccess {
                    credentialsStore.save(ShowdownCredentials(credentialsStore.load()?.username ?: session.localUsername(), password))
                    session.setConnectionStatus("Your Showdown password was changed.")
                }
                result.onFailure { error ->
                    session.setConnectionStatus(error.message ?: "Showdown password change failed.")
                }
            }
        }
    }

    private fun showRegistrationDialog() {
        val density = resources.displayMetrics.density
        val username = EditText(this).apply {
            hint = "Username"
            setSingleLine(true)
        }
        val password = EditText(this).apply {
            hint = "Password"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmation = EditText(this).apply {
            hint = "Repeat password"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val captcha = EditText(this).apply {
            hint = "Anti-spam answer · Pikachu"
            setSingleLine(true)
        }
        val details = TextView(this).apply {
            text = "Registered accounts keep your name, friends, teams, and ladder progress. The anti-spam answer is Pikachu."
            setTextColor(0xffdceff2.toInt())
            setTextSize(16f)
            setPadding(0, 0, 0, (12f * resources.displayMetrics.density).toInt())
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((56f * density).toInt(), (8f * density).toInt(), (56f * density).toInt(), 0)
            addView(details)
            addView(username)
            addView(password)
            addView(confirmation)
            addView(captcha)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(form, -1, -2)
        }
        ShowdownDialogBuilder(this)
            .setTitle("Create Showdown account")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                registerAccount(
                    username.text.toString(),
                    password.text.toString(),
                    confirmation.text.toString(),
                    captcha.text.toString()
                )
            }
            .show()
    }

    private fun registerAccount(usernameInput: String, password: String, confirmation: String, captcha: String) {
        val username = usernameInput.trim()
        if (username.isBlank()) {
            session.setConnectionStatus("Enter a username to create an account.")
            return
        }
        if (password.length < 5) {
            session.setConnectionStatus("Showdown passwords must be at least 5 characters.")
            return
        }
        if (password != confirmation) {
            session.setConnectionStatus("The passwords do not match.")
            return
        }
        if (captcha.trim().lowercase() != "pikachu") {
            session.setConnectionStatus("Answer the anti-spam question with Pikachu.")
            return
        }
        val registration = PendingRegistration(ShowdownCredentials(username, password), confirmation, captcha.trim())
        val connection = showdownConnection
        val challenge = latestChallenge
        if (connection != null && challenge != null && shouldMaintainConnection) {
            submitRegistration(connection, registration, challenge)
            return
        }
        pendingRegistration = registration
        shouldMaintainConnection = true
        pendingSearch = false
        pendingLobbyCommands = null
        pendingLobbyStatus = null
        reconnectLobbyCommands = null
        persistLobbyState()
        session.setConnectionStatus("Connecting to Showdown to create your account…")
        connectLobbySocket()
    }

    private fun submitRegistration(connection: ShowdownConnection, registration: PendingRegistration, challenge: String) {
        if (registrationInFlight) return
        registrationInFlight = true
        pendingRegistration = null
        session.setConnectionStatus("Creating Showdown account…")
        loginClient.register(
            serverEndpoint,
            registration.credentials,
            registration.confirmation,
            registration.captcha,
            challenge
        ) { result ->
            runOnUiThread {
                if (showdownConnection !== connection) return@runOnUiThread
                registrationInFlight = false
                result.onSuccess { assertion ->
                    credentialsStore.save(registration.credentials)
                    if (connection.sendGlobal(ShowdownAuthentication.renameCommand(registration.credentials.username, assertion))) {
                        session.setConnectionStatus("Account created. Finishing sign-in as ${registration.credentials.username}…")
                    } else {
                        session.setConnectionStatus("Account created, but Showdown sign-in could not reach the server.")
                    }
                }
                result.onFailure { error ->
                    session.setConnectionStatus(error.message ?: "Showdown account creation failed.")
                }
            }
        }
    }

    private fun showResourcesDialog() {
        val density = resources.displayMetrics.density
        val resources = TextView(this).apply {
            setTextSize(17f)
            setTextColor(0xffdceff2.toInt())
            setTextIsSelectable(true)
            text = listOf(
                "Pokémon Showdown is a competitive battle simulator.",
                "",
                "Battle help",
                "Use Find battle to enter matchmaking, or Challenge to play a named player.",
                "Team builder supports packed, readable, JSON, and backup imports.",
                "",
                "Support this project",
                "If Showdown DS is useful to you, sponsor development on GitHub.",
                "",
                "Useful commands",
                "/help for server commands",
                "/rules for room rules",
                "/data for Pokémon, move, item, and ability data",
                "/calc for damage calculations in supported rooms",
                "",
                "Community resources",
                "smogon.com/forums · smogon.com/dex · pokemonshowdown.com"
            ).joinToString("\n")
            setPadding((12f * density).toInt(), (8f * density).toInt(), (12f * density).toInt(), (8f * density).toInt())
        }
        val sponsorButton = Button(this).apply {
            text = "Sponsor on GitHub"
            styleDynamicDialogButton(this)
        }
        val pokedexButton = Button(this).apply {
            text = "Open Pokédex"
            styleDynamicDialogButton(this)
        }
        val changelogButton = Button(this).apply {
            text = "What's new"
            styleDynamicDialogButton(this)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@MainActivity).apply { addView(resources, -1, -2) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(changelogButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
            addView(sponsorButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
            addView(pokedexButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Info & resources")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        pokedexButton.setOnClickListener {
            dialog.dismiss()
            showPokedexDialog()
        }
        changelogButton.setOnClickListener {
            dialog.dismiss()
            showChangelogDialog()
        }
        dialog.show()
        sponsorButton.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/sponsors/castdrian")))
            }.onFailure {
                session.setConnectionStatus("Open github.com/sponsors/castdrian to support the project.")
            }
        }
    }

    private fun showChangelogDialog() {
        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12f * density).toInt(), (8f * density).toInt(), (12f * density).toInt(), (8f * density).toInt())
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().takeIf { it.isNotBlank() }?.let { "v$it" } ?: "Current build"
        ShowdownChangelog.entries(version).forEach { entry ->
            content.addView(TextView(this).apply {
                text = entry.version
                setTextColor(0xffa9f5ed.toInt())
                setTextSize(19f)
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                setPadding(0, (8f * density).toInt(), 0, (4f * density).toInt())
            })
            content.addView(TextView(this).apply {
                text = entry.changes.joinToString("\n") { "• $it" }
                setTextColor(0xffdceff2.toInt())
                setTextSize(17f)
                setLineSpacing(0f, 1.12f)
                setPadding(0, 0, 0, (8f * density).toInt())
            })
        }
        ShowdownDialogBuilder(this)
            .setTitle("What's new")
            .setView(ScrollView(this).apply { addView(content, -1, -2) })
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPokedexDialog() {
        pokedexDialog?.let {
            renderPokedexResults()
            return
        }
        val density = resources.displayMetrics.density
        val search = EditText(this).apply {
            hint = "Search Pokémon by name"
            setSingleLine(true)
            setTextSize(18f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    renderPokedexResults()
                }

                override fun afterTextChanged(editable: Editable?) = Unit
            })
        }
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val resultScroll = ScrollView(this).apply {
            addView(results, -1, -2)
        }
        val sprite = ShowdownPokedexSpriteView(this)
        val details = TextView(this).apply {
            setTextSize(17f)
            setTextColor(0xffe1f0f3.toInt())
            setLineSpacing(5f, 1f)
            setPadding((12f * density).toInt(), (10f * density).toInt(), (12f * density).toInt(), (10f * density).toInt())
            setTextIsSelectable(true)
        }
        val detailRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sprite, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.13f).toInt()))
            addView(details, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
        }
        val detailScroll = ScrollView(this).apply {
            addView(detailRoot, -1, -2)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(search, LinearLayout.LayoutParams(-1, -2))
            addView(resultScroll, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.22f).toInt()).apply { topMargin = (8f * density).toInt() })
            addView(detailScroll, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.30f).toInt()).apply { topMargin = (8f * density).toInt() })
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Pokédex")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (pokedexDialog === dialog) {
                pokedexDialog = null
                pokedexSearchInput = null
                pokedexResults = null
                pokedexDetails = null
                pokedexSprite = null
                selectedPokedexEntry = null
            }
        }
        pokedexDialog = dialog
        pokedexSearchInput = search
        pokedexResults = results
        pokedexDetails = details
        pokedexSprite = sprite
        details.text = "Search the live Showdown Pokédex by name."
        dialog.show()
        renderPokedexResults()
        loadPokedexData()
    }

    private fun loadPokedexData() {
        if (pokedex.isLoaded || pokedexLoading) return
        pokedexLoading = true
        renderPokedexResults()
        spriteCache.requestPokedex { file ->
            pokedex.load(file?.readText().orEmpty()) {
                pokedexLoading = false
                renderPokedexResults()
            }
        }
    }

    private fun renderPokedexResults() {
        val results = pokedexResults ?: return
        results.removeAllViews()
        val query = pokedexSearchInput?.text?.toString().orEmpty()
        if (!pokedex.isLoaded) {
            results.addView(TextView(this).apply {
                text = if (pokedexLoading) "Loading the live Pokédex…" else "Pokédex data is unavailable."
                setTextSize(17f)
                setTextColor(0xffdceff2.toInt())
                setPadding((12f * resources.displayMetrics.density).toInt(), (14f * resources.displayMetrics.density).toInt(), (12f * resources.displayMetrics.density).toInt(), (14f * resources.displayMetrics.density).toInt())
            })
            return
        }
        if (query.isBlank()) {
            results.addView(TextView(this).apply {
                text = "Type a Pokémon name to search."
                setTextSize(17f)
                setTextColor(0xffdceff2.toInt())
                setPadding((12f * resources.displayMetrics.density).toInt(), (14f * resources.displayMetrics.density).toInt(), (12f * resources.displayMetrics.density).toInt(), (14f * resources.displayMetrics.density).toInt())
            })
            return
        }
        val density = resources.displayMetrics.density
        val matches = pokedex.search(query)
        if (matches.isEmpty()) {
            results.addView(TextView(this).apply {
                text = "No Pokémon matched “$query”."
                setTextSize(17f)
                setTextColor(0xffdceff2.toInt())
                setPadding((12f * density).toInt(), (14f * density).toInt(), (12f * density).toInt(), (14f * density).toInt())
            })
            return
        }
        matches.forEach { entry ->
            val button = Button(this).apply {
                text = buildString {
                    append(entry.name)
                    if (entry.types.isNotEmpty()) append("\n${entry.types.joinToString(" · ")}")
                }
                isAllCaps = false
                setOnClickListener { selectPokedexEntry(entry) }
            }
            styleDynamicDialogButton(button)
            results.addView(button, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, (6f * density).toInt()) })
        }
    }

    private fun selectPokedexEntry(entry: ShowdownPokedex.Entry) {
        selectedPokedexEntry = entry
        pokedexDetails?.text = formatPokedexEntry(entry)
        pokedexSprite?.setSprite(null)
        spriteCache.requestDexSprite(entry.name) { asset ->
            if (selectedPokedexEntry?.id == entry.id) pokedexSprite?.setSprite(asset)
        }
    }

    private fun formatPokedexEntry(entry: ShowdownPokedex.Entry): String = buildString {
        entry.number?.let { append(String.format(Locale.ROOT, "#%03d  ", it)) }
        append(entry.name)
        if (entry.types.isNotEmpty()) append("\n${entry.types.joinToString(" · ")}")
        entry.tier.takeIf { it.isNotBlank() }?.let { append("\nTier: $it") }
        entry.generation?.let { append("\nGeneration: $it") }
        entry.abilities.takeIf { it.isNotEmpty() }?.let { append("\nAbilities: ${it.joinToString(", ")}") }
        if (entry.baseStats.isNotEmpty()) {
            val labels = linkedMapOf("hp" to "HP", "atk" to "Atk", "def" to "Def", "spa" to "SpA", "spd" to "SpD", "spe" to "Spe")
            val stats = labels.mapNotNull { (id, label) -> entry.baseStats[id]?.let { "$label $it" } }
            if (stats.isNotEmpty()) append("\nBase stats: ${stats.joinToString(" · ")}")
        }
        if (entry.heightMeters != null || entry.weightKg != null) {
            append("\n")
            entry.heightMeters?.let { append(String.format(Locale.ROOT, "Height %.1f m", it)) }
            if (entry.heightMeters != null && entry.weightKg != null) append(" · ")
            entry.weightKg?.let { append(String.format(Locale.ROOT, "Weight %.1f kg", it)) }
        }
        entry.eggGroups.takeIf { it.isNotEmpty() }?.let { append("\nEgg groups: ${it.joinToString(" · ")}") }
        entry.preEvolution?.let { append("\nEvolves from: $it") }
        entry.evolutions.takeIf { it.isNotEmpty() }?.let { append("\nEvolves into: ${it.joinToString(" · ")}") }
    }

    private fun showFriendsDialog() {
        if (!authenticated || !serverUserNamed) {
            session.setConnectionStatus("Sign in to use Friends.")
            return
        }
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before opening Friends.")
            return
        }
        friendsDialog?.dismiss()
        friendsState.clear()
        val density = resources.displayMetrics.density
        val content = TextView(this).apply {
            setTextSize(17f)
            setTextColor(0xffdceff2.toInt())
            setTextIsSelectable(true)
            setPadding((10f * density).toInt(), (8f * density).toInt(), (10f * density).toInt(), (8f * density).toInt())
        }
        val input = EditText(this).apply {
            hint = "Username for friend actions"
            setSingleLine(true)
        }
        val refresh = Button(this).apply {
            text = "Refresh"
            setOnClickListener { requestFriendsPage() }
        }
        fun pageButton(label: String, page: String) = Button(this).apply {
            text = label
            setOnClickListener { requestFriendsPage(page) }
        }
        val all = pageButton("All", "all")
        val sent = pageButton("Sent", "sent")
        val received = pageButton("Received", "received")
        val settings = pageButton("Settings", "settings")
        val spectate = pageButton("Spectate", "spectate")
        val help = pageButton("Help", "help")
        val add = Button(this).apply {
            text = "Add"
            setOnClickListener { sendFriendCommand(ShowdownFriendsState.addCommand(input.text.toString())) }
        }
        val remove = Button(this).apply {
            text = "Remove"
            setOnClickListener { sendFriendCommand(ShowdownFriendsState.removeCommand(input.text.toString())) }
        }
        val accept = Button(this).apply {
            text = "Accept"
            setOnClickListener { sendFriendCommand(ShowdownFriendsState.acceptCommand(input.text.toString())) }
        }
        val reject = Button(this).apply {
            text = "Reject"
            setOnClickListener { sendFriendCommand(ShowdownFriendsState.rejectCommand(input.text.toString())) }
        }
        val viewList = Button(this).apply {
            text = "View public list"
            setOnClickListener { requestPublicFriendsList() }
        }
        fun buttonRow(vararg buttons: Button) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEach { button ->
                addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins((3f * density).toInt(), 0, (3f * density).toInt(), 0) })
            }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(refresh, LinearLayout.LayoutParams(-1, -2))
            addView(buttonRow(all, sent, received), LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
            addView(buttonRow(settings, spectate, help), LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
            addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
            addView(buttonRow(add, remove), LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
            addView(buttonRow(accept, reject), LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
            addView(viewList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
        }
        val contentRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(-1, -2))
            addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
        }
        val root = ScrollView(this).apply {
            isFillViewport = true
            addView(contentRoot, -1, -2)
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Friends")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (friendsDialog === dialog) {
                friendsDialog = null
                friendsContentView = null
                friendsInput = null
                friendsState.clear()
            }
        }
        friendsDialog = dialog
        friendsContentView = content
        friendsInput = input
        dialog.show()
        updateFriendsDialog()
        requestFriendsPage()
    }

    private fun updateFriendsDialog() {
        val snapshot = friendsState.snapshot
        friendsDialog?.setTitle(snapshot.title)
        friendsContentView?.text = snapshot.error ?: snapshot.text
    }

    private fun requestFriendsPage(page: String = "all") {
        if (showdownConnection?.sendGlobal(ShowdownFriendsState.pageCommand(page)) != true) {
            session.setConnectionStatus("Friends connection is not ready yet.")
        }
    }

    private fun requestPublicFriendsList() {
        val username = friendsInput?.text?.toString()?.trim().orEmpty()
        if (username.isBlank()) {
            session.setConnectionStatus("Enter a username first.")
            return
        }
        if (showdownConnection?.sendGlobal(ShowdownFriendsState.publicListCommand(username)) != true) {
            session.setConnectionStatus("Friends connection is not ready yet.")
        }
    }

    private fun sendFriendCommand(command: String) {
        val username = friendsInput?.text?.toString()?.trim().orEmpty()
        if (username.isBlank()) {
            session.setConnectionStatus("Enter a username first.")
            return
        }
        if (showdownConnection?.sendGlobal(command) == true) {
            friendsInput?.setText("")
            session.setConnectionStatus("Sent Friends action for $username.")
        } else {
            session.setConnectionStatus("Friends connection is not ready yet.")
        }
    }

    private fun signOut() {
        credentialsStore.clear()
        loginClient.clearSession()
        sessionStore.clear()
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
        serverUserNamed = false
        pendingRegistration = null
        registrationInFlight = false
        latestChallenge = null
        pendingTeamUpload = null
        pendingTeamPrivacy = null
        pendingTeamDelete = null
        teamLibrary.clearRemoteMetadata()
        teamUploadButtons.forEach { it.isEnabled = true }
        teamUploadButtons = emptyList()
        teamPrivacyButton?.isEnabled = true
        teamPrivacyButton = null
        activeBattleRoomId = null
        battleWasRegistered = false
        battleWasParticipant = false
        battleIsSpectator = false
        completedBattleRoomId = null
        pendingBattleJoinRoomId = null
        pendingBattleSearchFormat = null
        pendingBattleSearchLabel = null
        pendingBattleSearchUsesRandomTeams = null
        pendingBattleSearchTeamPacked = null
        pendingDecisionCommand = null
        displayedOutgoingChallenge = null
        displayedIncomingChallenge = null
        privateMessageDialog?.dismiss()
        privateMessageDialog = null
        privateMessageTarget = null
        privateMessageThreads.clear()
        userDetailsDialog?.dismiss()
        userDetailsDialog = null
        pendingUserDetailsId = null
        friendsDialog?.dismiss()
        friendsDialog = null
        friendsContentView = null
        friendsInput = null
        friendsState.clear()
        teamRemoteDialog?.dismiss()
        teamRemoteDialog = null
        teamRemoteContentView = null
        teamRemoteLinks = null
        teamRemoteState.clear()
        clearPersistedLobbyState()
        session.prepareForLobby()
        session.setConnectionStatus("Signed out of Showdown.")
    }

    private fun showTeamValidationResult(format: String, result: String) {
        ShowdownDialogBuilder(this)
            .setTitle("Team validation · ${readableFormatLabel(format)}")
            .setMessage(result)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTeamLibrary() {
        if (teamLibraryDialog?.isShowing == true) return
        val teams = teamLibrary.teams()
        val density = resources.displayMetrics.density
        val search = EditText(this).apply {
            hint = "Search teams, Pokémon, or moves"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val folderBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val formatBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val resultSummary = TextView(this).apply {
            setTextSize(16f)
            setTextColor(0xffa9e8e2.toInt())
            setPadding(0, (8f * density).toInt(), 0, (8f * density).toInt())
        }
        val resultList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        var activeFolder: String? = null
        var activeFormat: String? = null
        fun formatLabel(format: String): String = readableFormatLabel(format)
        var teamDialog: ShowdownDialog? = null
        fun styleTeamButton(button: Button, compact: Boolean = false, selected: Boolean = false) = button.apply {
            isAllCaps = false
            setTextColor(if (selected) Color.rgb(229, 252, 248) else Color.rgb(137, 221, 215))
            setTextSize(if (compact) 14f else 15f)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            minHeight = (if (compact) 38f else 44f).times(density).toInt()
            minimumHeight = minHeight
            setPadding((14f * density).toInt(), 0, (14f * density).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(if (selected) Color.rgb(24, 124, 129) else Color.rgb(15, 50, 67))
                setStroke((1f * density).toInt(), if (selected) Color.rgb(121, 218, 211) else Color.rgb(53, 117, 127))
                cornerRadius = 14f * density
            }
        }
        fun renderResults() {
            val visibleTeams = ShowdownTeamLibraryQuery.filter(
                teams,
                ShowdownTeamLibraryFilter(
                    query = search.text.toString(),
                    folder = activeFolder,
                    format = activeFormat
                )
            )
            val countLabel = "${visibleTeams.size} team${if (visibleTeams.size == 1) "" else "s"}"
            resultSummary.text = when {
                activeFolder != null && activeFormat != null -> {
                    val folderName = activeFolder.takeUnless { it.isNullOrBlank() } ?: "Unfiled"
                    "${formatLabel(activeFormat.orEmpty())} · $folderName · $countLabel"
                }
                activeFolder != null -> {
                    val folderName = activeFolder.takeUnless { it.isNullOrBlank() } ?: "Unfiled"
                    "$folderName · $countLabel"
                }
                activeFormat != null -> "${formatLabel(activeFormat.orEmpty())} · $countLabel"
                else -> "${visibleTeams.size} of ${teams.size} teams"
            }
            resultList.removeAllViews()
            if (visibleTeams.isEmpty()) {
                resultList.addView(TextView(this).apply {
                    text = if (teams.isEmpty()) "No teams yet. Add a team or import a backup." else "No teams match this search."
                    setTextSize(17f)
                    setTextColor(0xffcfe1e8.toInt())
                    setPadding((12f * density).toInt(), (12f * density).toInt(), (12f * density).toInt(), (12f * density).toInt())
                })
            } else {
                visibleTeams.forEach { team ->
                    val remoteState = when {
                        team.remoteNeedsUpload -> " · Upload needed"
                        team.remoteId != null -> " · Uploaded"
                        else -> ""
                    }
                    resultList.addView(styleTeamButton(Button(this)).apply {
                        text = buildString {
                            append(team.name)
                            append("\n${formatLabel(team.format)}")
                            team.folder.trim().takeIf(String::isNotBlank)?.let { append(" · $it") }
                            append(remoteState)
                        }
                        gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                        setOnClickListener {
                            teamDialog?.dismiss()
                            showTeamEditor(team)
                        }
                    }, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = (8f * density).toInt()
                    })
                }
            }
            teamDialog?.setTitle("Team library · ${visibleTeams.size}/${teams.size}")
        }
        var renderFolders: () -> Unit = {}
        fun renderFormats() {
            formatBar.removeAllViews()
            val options = buildList<Pair<String?, String>> {
                add(null to "All formats")
                ShowdownTeamLibraryQuery.formats(teams).forEach { add(it to formatLabel(it)) }
            }
            options.forEach { (format, label) ->
                val selected = activeFormat.equals(format, true)
                formatBar.addView(styleTeamButton(Button(this), compact = true, selected = selected).apply {
                    text = if (selected) "✓ $label" else label
                    isSelected = selected
                    setOnClickListener {
                        activeFormat = format
                        renderFormats()
                        renderFolders()
                        renderResults()
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply {
                    setMargins(0, 0, (8f * density).toInt(), 0)
                })
            }
        }
        renderFolders = {
            folderBar.removeAllViews()
            val options = buildList<Pair<String?, String>> {
                add(null to "All teams")
                add("" to "Unfiled")
                ShowdownTeamLibraryQuery.folders(teams).forEach { add(it to it) }
            }
            options.forEach { (folder, label) ->
                val selected = activeFolder.equals(folder, true)
                folderBar.addView(styleTeamButton(Button(this), compact = true, selected = selected).apply {
                    text = if (selected) "✓ $label" else label
                    isSelected = selected
                    setOnClickListener {
                        activeFolder = folder
                        renderFormats()
                        renderFolders()
                        renderResults()
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply {
                    setMargins(0, 0, (8f * density).toInt(), 0)
                })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = renderResults()
            override fun afterTextChanged(editable: Editable?) = Unit
        })
        val addButton = styleTeamButton(Button(this)).apply {
            text = "Add team"
            setOnClickListener {
                teamDialog?.dismiss()
                showTeamEditor(
                    initialFolder = activeFolder.orEmpty(),
                    initialFormat = activeFormat
                )
            }
        }
        val remoteButton = styleTeamButton(Button(this)).apply {
            text = "Browse remote teams"
            setOnClickListener {
                teamDialog?.dismiss()
                showTeamRemoteLibrary()
            }
        }
        val filterBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(folderBar, LinearLayout.LayoutParams(-2, -2))
            addView(formatBar, LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = (8f * density).toInt()
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(search, LinearLayout.LayoutParams(-1, -2))
            addView(HorizontalScrollView(this@MainActivity).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(filterBar, LinearLayout.LayoutParams(-2, -2))
            }, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (8f * density).toInt()
            })
            addView(resultSummary, LinearLayout.LayoutParams(-1, -2))
            addView(resultList, LinearLayout.LayoutParams(-1, -2))
            addView(addButton, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (8f * density).toInt()
            })
            addView(remoteButton, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (8f * density).toInt()
            })
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, LinearLayout.LayoutParams(-1, -2))
        }
        teamDialog = ShowdownDialogBuilder(this)
            .setTitle("Team library · 0/${teams.size}")
            .setView(scroll)
            .setNeutralButton("Export backup") { _, _ -> copyTeamBackup() }
            .setPositiveButton("Import backup") { _, _ -> showTeamBackupImport(returnToTeamLibrary = true) }
            .setNegativeButton("Close", null)
            .create()
        teamLibraryDialog = teamDialog
        teamDialog?.setOnDismissListener {
            if (teamLibraryDialog === teamDialog) teamLibraryDialog = null
        }
        teamDialog?.setOnShowListener {
            styleTeamButton(addButton)
            styleTeamButton(remoteButton)
            renderFormats()
            renderFolders()
            renderResults()
        }
        teamDialog?.show()
    }

    private fun showTeamRemoteLibrary(initialCommand: String = ShowdownTeamRemoteState.ownTeamsCommand()) {
        if (!authenticated || !serverUserNamed) {
            session.setConnectionStatus("Sign in to Showdown to browse remote teams.")
            return
        }
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before browsing remote teams.")
            return
        }
        teamRemoteDialog?.dismiss()
        teamRemoteState.clear()
        val density = resources.displayMetrics.density
        val content = TextView(this).apply {
            setTextSize(16f)
            setTextColor(0xffdceff2.toInt())
            setTextIsSelectable(true)
            setPadding((10f * density).toInt(), (8f * density).toInt(), (10f * density).toInt(), (8f * density).toInt())
        }
        val own = Button(this).apply {
            text = "My uploaded teams"
            setOnClickListener { requestTeamRemotePage(ShowdownTeamRemoteState.ownTeamsCommand()) }
        }
        val browse = Button(this).apply {
            text = "Browse public teams"
            setOnClickListener { requestTeamRemotePage(ShowdownTeamRemoteState.browseCommand()) }
        }
        val search = Button(this).apply {
            text = "Search public teams"
            setOnClickListener { showRemoteTeamSearch() }
        }
        val links = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(own)
            addView(browse, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
            addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6f * density).toInt() })
            addView(links, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
        }
        val root = ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(content, LinearLayout.LayoutParams(-1, -2))
                addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8f * density).toInt() })
            }, -1, -2)
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Remote teams")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (teamRemoteDialog === dialog) {
                teamRemoteDialog = null
                teamRemoteContentView = null
                teamRemoteLinks = null
                teamRemoteState.clear()
            }
        }
        teamRemoteDialog = dialog
        teamRemoteContentView = content
        teamRemoteLinks = links
        dialog.show()
        updateTeamRemoteDialog()
        requestTeamRemotePage(initialCommand)
    }

    private fun showRemoteTeamSearch() {
        val format = EditText(this).apply {
            hint = "Format ID, for example gen9ou"
            setSingleLine(true)
            setText(session.matchFormat.id)
        }
        val pokemon = EditText(this).apply {
            hint = "Pokémon, comma separated"
            setSingleLine(true)
        }
        val moves = EditText(this).apply {
            hint = "Moves, comma separated"
            setSingleLine(true)
        }
        val ability = EditText(this).apply {
            hint = "Ability"
            setSingleLine(true)
        }
        val generation = EditText(this).apply {
            hint = "Generation, for example 9"
            setSingleLine(true)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(format)
            addView(pokemon)
            addView(moves)
            addView(ability)
            addView(generation)
        }
        val dialog = ShowdownDialogBuilder(this)
            .setTitle("Search public teams")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ ->
                val command = ShowdownTeamRemoteState.searchCommand(
                    format.text.toString(),
                    pokemon.text.toString(),
                    moves.text.toString(),
                    ability.text.toString(),
                    generation.text.toString()
                )
                teamRemoteDialog?.dismiss()
                showTeamRemoteLibrary(command)
            }
            .create()
        dialog.show()
    }

    private fun requestTeamRemotePage(command: String) {
        if (showdownConnection?.sendGlobal(command) != true) {
            session.setConnectionStatus("Remote team connection is not ready.")
        }
    }

    private fun updateTeamRemoteDialog() {
        val snapshot = teamRemoteState.snapshot
        teamRemoteDialog?.setTitle(snapshot.title)
        val summary = when {
            snapshot.error != null -> snapshot.error
            snapshot.selectedTeam != null && !snapshot.packed.isNullOrBlank() -> "${snapshot.selectedTeam.name}\n${readableFormatLabel(snapshot.selectedTeam.formatLabel)} · ${snapshot.selectedTeam.owner}\n\nReady to import this team."
            snapshot.teams.isNotEmpty() -> "Choose a team below to view its full export."
            else -> snapshot.text.ifBlank { "Choose a remote team list." }
        }
        teamRemoteContentView?.text = summary
        val links = teamRemoteLinks ?: return
        links.removeAllViews()
        snapshot.teams.forEach { team ->
            val teamButton = Button(this).apply {
                text = "${team.name}\n${readableFormatLabel(team.formatLabel)} · ${team.owner}"
                setOnClickListener {
                    requestTeamRemotePage(ShowdownTeamRemoteState.viewCommand(team))
                    session.setConnectionStatus("Loading ${team.name}…")
                }
            }
            styleDynamicDialogButton(teamButton)
            links.addView(teamButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, (6f * resources.displayMetrics.density).toInt()) })
        }
        val selectedTeam = snapshot.selectedTeam
        val packed = snapshot.packed
        if (selectedTeam != null && !packed.isNullOrBlank()) {
            val importButton = Button(this).apply {
                text = "Import ${selectedTeam.name}"
                setOnClickListener {
                    val format = ShowdownTeamRemoteState.resolveFormatId(selectedTeam.formatLabel, session.availableMatchFormats())
                    if (format == null) {
                        session.setConnectionStatus("Showdown has not advertised ${readableFormatLabel(selectedTeam.formatLabel)}; refresh formats before importing.")
                        return@setOnClickListener
                    }
                    teamLibrary.save(selectedTeam.name, format, packed)
                    session.setConnectionStatus("Imported ${selectedTeam.name} into the team library.")
                    teamRemoteDialog?.dismiss()
                    showTeamLibrary()
                }
            }
            styleDynamicDialogButton(importButton)
            links.addView(importButton)
        }
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

    private fun showTeamBackupImport(returnToTeamLibrary: Boolean = false) {
        val input = EditText(this).apply {
            hint = "Paste exported teams, a PokePaste URL, or a Gist URL"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setMinLines(8)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipboardValue = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
            if (ShowdownTeamUrlImporter.isLikelyTeamText(clipboardValue)) {
                setText(clipboardValue)
            }
        }
        ShowdownDialogBuilder(this)
            .setTitle("Import team backup")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ ->
                val source = input.text.toString().trim()
                if (ShowdownTeamUrlImporter.normalize(source) != null) {
                    session.setConnectionStatus("Fetching team from URL…")
                    teamUrlFetcher.fetch(source) { result ->
                        result.onSuccess { payload ->
                            val imported = importTeamBackup(payload.text, payload.name, payload.format)
                            refreshTeamLibraryAfterImport(imported, returnToTeamLibrary)
                        }
                            .onFailure { session.setConnectionStatus("Could not fetch that team URL. Paste the team export instead.") }
                    }
                } else {
                    val imported = importTeamBackup(source)
                    refreshTeamLibraryAfterImport(imported, returnToTeamLibrary)
                }
            }
            .show()
    }

    private fun importTeamBackup(value: String, fallbackName: String = "Imported team", fallbackFormat: String = "gen9"): List<ShowdownTeam> {
        val payload = ShowdownTeamUrlImporter.payload(value, fallbackName, fallbackFormat)
        val imported = teamLibrary.importBackup(payload.text, payload.name.ifBlank { fallbackName }, payload.format.ifBlank { fallbackFormat })
        session.setConnectionStatus(
            if (imported.isEmpty()) "No valid Showdown teams were found in that backup."
            else "Imported ${imported.size} team${if (imported.size == 1) "" else "s"}."
        )
        return imported
    }

    private fun refreshTeamLibraryAfterImport(imported: List<ShowdownTeam>, returnToTeamLibrary: Boolean) {
        if (!ShowdownTeamImportRefresh.shouldRefresh(teamLibraryDialog?.isShowing == true, returnToTeamLibrary, imported)) return
        teamLibraryDialog?.dismiss()
        window.decorView.post {
            if (!isFinishing) showTeamLibrary()
        }
    }

    private fun showTeamEditor(existing: ShowdownTeam? = null, initialFolder: String = "", initialFormat: String? = null) {
        val localId = existing?.id ?: java.util.UUID.randomUUID().toString()
        val name = EditText(this).apply {
            hint = "Team name"
            setSingleLine(true)
            setText(existing?.name.orEmpty())
        }
        val format = EditText(this).apply {
            hint = "Format ID, for example gen9ou"
            setSingleLine(true)
            setText(existing?.format ?: ShowdownTeamEditorDefaults.format(initialFormat, session.matchFormat, session.availableMatchFormats()))
        }
        val formatPicker = Button(this).apply {
            text = "Choose format from Showdown"
            setOnClickListener { showTeamFormatPicker(format) }
        }
        val folder = EditText(this).apply {
            hint = "Folder (optional)"
            setSingleLine(true)
            setText(existing?.folder ?: initialFolder)
        }
        val packed = EditText(this).apply {
            hint = "Packed or Showdown export"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setMinLines(2)
            setText(existing?.packed.orEmpty())
        }
        val shareView = TextView(this).apply {
            setTextSize(16f)
            setTextColor(0xffa9e8e2.toInt())
            setTextIsSelectable(true)
            text = existing?.let { team ->
                team.remoteId?.let {
                    buildString {
                        append("Share URL: ${ShowdownTeamRemote.shareUrl(it, team.remotePrivateKey)}")
                        if (team.remoteNeedsUpload) append("\nLocal edits not uploaded.")
                    }
                }
            }.orEmpty()
        }
        val copyShareButton = existing?.let { team ->
            team.remoteId?.let { remoteId ->
                Button(this).apply {
                    text = "Copy share URL"
                    setOnClickListener {
                        val url = ShowdownTeamRemote.shareUrl(remoteId, team.remotePrivateKey)
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Showdown team share URL", url))
                        session.setConnectionStatus("Share URL copied to the clipboard.")
                    }
                }
            }
        }
        val privacyButton = existing?.let { team ->
            team.remoteId?.let { remoteId ->
                Button(this).apply {
                    text = if (team.remotePrivateKey == null) "Make private" else "Make public"
                    setOnClickListener {
                        if (pendingTeamPrivacy != null) {
                            session.setConnectionStatus("Finish the current privacy update first.")
                            return@setOnClickListener
                        }
                        if (!authenticated || !serverUserNamed || showdownConnection == null) {
                            session.setConnectionStatus("Sign in to Showdown before changing team privacy.")
                            return@setOnClickListener
                        }
                        pendingTeamPrivacy = PendingTeamPrivacy(localId, remoteId)
                        val makePrivate = team.remotePrivateKey == null
                        if (showdownConnection?.sendGlobal(ShowdownTeamRemote.privacyCommand(remoteId, makePrivate)) != true) {
                            pendingTeamPrivacy = null
                            session.setConnectionStatus("The team privacy update could not reach Showdown.")
                        } else {
                            teamPrivacyButton = this
                            isEnabled = false
                            session.setConnectionStatus("Updating team privacy…")
                        }
                    }
                }
            }
        }
        val sets = existing?.let { ShowdownTeamCodec.unpack(it.packed) }.orEmpty().ifEmpty { listOf(ShowdownTeamSet()) }
        val setEditors = mutableListOf<TeamSetEditor>()
        val firstExpandedIndex = sets.indexOfFirst {
            it.nickname.isNotBlank() || it.species.isNotBlank() || it.item.isNotBlank() || it.ability.isNotBlank() ||
                it.moves.isNotEmpty() || it.nature.isNotBlank()
        }.takeIf { it >= 0 } ?: 0
        val setFields = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val density = resources.displayMetrics.density
            setPadding((24f * density).toInt(), (8f * density).toInt(), (24f * density).toInt(), 0)
            for (index in 0 until 6) {
                setEditors += createTeamSetEditor(
                    this,
                    index,
                    sets.getOrNull(index) ?: ShowdownTeamSet(),
                    index == firstExpandedIndex
                )
            }
        }
        fun refreshTeamSetOrderControls() {
            setEditors.forEachIndexed { index, editor ->
                editor.index = index
                editor.moveUpButton.isEnabled = index > 0
                editor.moveDownButton.isEnabled = index < setEditors.lastIndex
                editor.moveUpButton.alpha = if (editor.moveUpButton.isEnabled) 1f else 0.45f
                editor.moveDownButton.alpha = if (editor.moveDownButton.isEnabled) 1f else 0.45f
                updateTeamSetSummary(editor)
            }
        }
        fun moveTeamSet(index: Int, direction: Int) {
            val reordered = ShowdownTeamOrder.move(setEditors, index, direction)
            if (reordered === setEditors) return
            val editor = setEditors[index]
            setEditors.clear()
            setEditors.addAll(reordered)
            setFields.removeView(editor.section)
            setFields.addView(editor.section, setEditors.indexOf(editor))
            refreshTeamSetOrderControls()
        }
        setEditors.forEach { editor ->
            editor.moveUpButton.setOnClickListener {
                moveTeamSet(setEditors.indexOf(editor), -1)
            }
            editor.moveDownButton.setOnClickListener {
                moveTeamSet(setEditors.indexOf(editor), 1)
            }
        }
        refreshTeamSetOrderControls()
        fun readTeamDraft(): TeamDraft {
            val editedSets = setEditors.map(::readTeamSetEditor)
            val editedPacked = ShowdownTeamCodec.pack(editedSets)
            val importedSets = ShowdownTeamCodec.parse(packed.text.toString())
            val teamPacked = editedPacked.ifBlank { ShowdownTeamCodec.pack(importedSets) }
            val validation = if (editedPacked.isNotBlank()) {
                ShowdownTeamCodec.validate(editedSets)
            } else {
                ShowdownTeamCodec.validate(importedSets)
            }
            return TeamDraft(teamPacked, validation.firstOrNull())
        }
        fun readValidatedTeamDraft(action: String): Pair<String, TeamDraft>? {
            val teamFormat = format.text.toString().trim()
            if (teamFormat.isBlank()) {
                session.setConnectionStatus("Enter a format ID before $action.")
                return null
            }
            val draft = readTeamDraft()
            when {
                draft.packed.isBlank() -> {
                    session.setConnectionStatus("Add at least one Pokémon before $action.")
                    return null
                }
                draft.error != null -> {
                    session.setConnectionStatus(draft.error)
                    return null
                }
            }
            return teamFormat to draft
        }
        fun validateDraftOnShowdown() {
            val request = readValidatedTeamDraft("validating the team") ?: return
            val (teamFormat, draft) = request
            if (showdownConnection == null) {
                session.setConnectionStatus("Connect to Showdown before validating the team.")
                return
            }
            pendingTeamValidationFormat = teamFormat
            val teamSent = showdownConnection?.sendGlobal(ShowdownTeamValidation.setTeamCommand(draft.packed)) == true
            val validationSent = teamSent && showdownConnection?.sendGlobal(ShowdownTeamValidation.validateCommand(teamFormat)) == true
            if (!validationSent) {
                pendingTeamValidationFormat = null
                session.setConnectionStatus("The team validation request could not reach Showdown.")
            } else {
                session.setConnectionStatus("Validating the team for $teamFormat…")
            }
        }
        val revertButton = existing?.takeIf { it.remoteNeedsUpload && it.uploadedPacked != null }?.let { team ->
            Button(this).apply {
                text = "Revert local edits"
                setOnClickListener {
                    val reverted = teamLibrary.revertToUploaded(localId)
                    if (reverted == null) {
                        session.setConnectionStatus("The uploaded team version is unavailable.")
                    } else {
                        name.setText(reverted.name)
                        format.setText(reverted.format)
                        folder.setText(reverted.folder)
                        packed.setText(reverted.packed)
                        val revertedSets = ShowdownTeamCodec.unpack(reverted.packed)
                        setEditors.forEachIndexed { index, editor ->
                            populateTeamSetEditor(editor, revertedSets.getOrNull(index) ?: ShowdownTeamSet())
                        }
                        shareView.text = "Share URL: ${ShowdownTeamRemote.shareUrl(reverted.remoteId.orEmpty(), reverted.remotePrivateKey)}"
                        session.setConnectionStatus("Reverted ${team.name} to the uploaded version.")
                    }
                }
            }
        }
        fun uploadDraft(privateTeam: Boolean) {
            if (pendingTeamUpload != null) {
                session.setConnectionStatus("Finish the current team upload first.")
                return
            }
            if (!authenticated || !serverUserNamed || showdownConnection == null) {
                session.setConnectionStatus("Sign in to Showdown before uploading a team.")
                return
            }
            val request = readValidatedTeamDraft("uploading the team") ?: return
            val (teamFormat, draft) = request
            val saved = teamLibrary.save(name.text.toString(), teamFormat, draft.packed, localId, folder.text.toString())
            val action = if (saved.remoteId == null) "save" else "update"
            val command = ShowdownTeamRemote.command(action, saved.remoteId, saved.name, saved.format, privateTeam, saved.packed)
            pendingTeamUpload = PendingTeamUpload(localId, saved.packed)
            if (showdownConnection?.sendGlobal(command) != true) {
                pendingTeamUpload = null
                session.setConnectionStatus("The team upload could not reach Showdown.")
            } else {
                teamUploadButtons.forEach { it.isEnabled = false }
                session.setConnectionStatus("Uploading ${saved.name}…")
            }
        }
        moveDex.load {
            setEditors.filter { it.details.visibility == View.VISIBLE }.forEach(::updateTeamEditorSuggestions)
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
        val validateButton = Button(this).apply {
            text = "Validate on Showdown"
            setOnClickListener { validateDraftOnShowdown() }
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
        val uploadPrivateButton = Button(this).apply {
            text = "Upload private team"
            setOnClickListener { uploadDraft(true) }
        }
        val uploadPublicButton = Button(this).apply {
            text = "Upload public team"
            setOnClickListener { uploadDraft(false) }
        }
        teamUploadButtons = listOf(uploadPrivateButton, uploadPublicButton)
        teamUploadButtons.forEach { it.isEnabled = pendingTeamUpload == null }
        val duplicateButton = existing?.let { team ->
            Button(this).apply {
                text = "Duplicate team"
                setOnClickListener {
                    teamLibrary.duplicate(team.id)?.let { copy ->
                        session.setConnectionStatus("Duplicated ${team.name} as ${copy.name}.")
                        teamEditorDialog?.dismiss()
                    }
                }
            }
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(format)
            addView(formatPicker)
            addView(folder)
            addView(packed)
            addView(importButton)
            addView(setFields)
            addView(validateButton)
            addView(copyButton)
            addView(copyTextButton)
            addView(copyJsonButton)
            addView(uploadPrivateButton)
            addView(uploadPublicButton)
            addView(shareView)
            copyShareButton?.let(::addView)
            privacyButton?.let(::addView)
            revertButton?.let(::addView)
            duplicateButton?.let(::addView)
        }
        val scroll = ScrollView(this).apply {
            addView(fields)
        }
        val builder = ShowdownDialogBuilder(this)
            .setTitle(if (existing == null) "Add team" else "Edit team")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                val remoteId = existing.remoteId
                if (remoteId == null) {
                    teamLibrary.remove(existing.id)
                    session.setConnectionStatus("Deleted ${existing.name}.")
                } else if (!authenticated || !serverUserNamed || showdownConnection == null) {
                    session.setConnectionStatus("Sign in to Showdown before deleting the uploaded team.")
                } else if (pendingTeamDelete != null) {
                    session.setConnectionStatus("Finish the current team deletion first.")
                } else {
                    pendingTeamDelete = PendingTeamDelete(existing.id, remoteId)
                    if (showdownConnection?.sendGlobal(ShowdownTeamRemote.deleteCommand(remoteId)) == true) {
                        session.setConnectionStatus("Deleting ${existing.name}…")
                        teamEditorDialog?.dismiss()
                    } else {
                        pendingTeamDelete = null
                        session.setConnectionStatus("The uploaded team could not be deleted.")
                    }
                }
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(ShowdownDialog.BUTTON_POSITIVE)?.setOnClickListener {
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
                        teamLibrary.save(name.text.toString(), teamFormat, teamPacked, localId, folder.text.toString())
                        session.setConnectionStatus("Saved ${name.text.toString().trim().ifBlank { "Untitled team" }}.")
                        dialog.dismiss()
                    }
                }
            }
        }
        teamEditorDialog = dialog
        teamEditorShareView = shareView
        dialog.setOnDismissListener {
            if (teamEditorDialog === dialog) {
                teamEditorDialog = null
                teamEditorShareView = null
                if (pendingTeamUpload == null) teamUploadButtons = emptyList()
                if (pendingTeamPrivacy == null) teamPrivacyButton = null
            }
        }
        dialog.show()
    }

    private fun showTeamFormatPicker(target: EditText) {
        val typedFormat = target.text.toString().trim()
        val formats = (session.availableMatchFormats() + typedFormat.takeIf { it.isNotBlank() }?.let { BattleSession.MatchFormat(it, it) })
            .filterNotNull()
            .distinctBy { it.id.trim().lowercase() }
        val labels = formats.map { format -> readableFormatLabel(format.id) }
        ShowdownDialogBuilder(this)
            .setTitle("Choose team format")
            .setSingleChoiceItems(labels.toTypedArray(), formats.indexOfFirst { it.id.trim().equals(typedFormat.trim(), true) }) { dialog, selected ->
                target.setText(formats[selected].id.trim())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private data class TeamStatEditor(
        val container: LinearLayout,
        val fields: List<EditText>
    )

    private data class TeamSetEditor(
        var index: Int,
        val section: LinearLayout,
        val slotHeader: Button,
        val moveUpButton: Button,
        val moveDownButton: Button,
        val details: LinearLayout,
        val nickname: EditText,
        val species: EditText,
        val item: EditText,
        val ability: EditText,
        val moves: List<AutoCompleteTextView>,
        val movesContainer: LinearLayout,
        val nature: EditText,
        val evs: TeamStatEditor,
        val gender: EditText,
        val ivs: TeamStatEditor,
        val shiny: CheckBox,
        val level: EditText,
        val happiness: EditText,
        val pokeBall: EditText,
        val hiddenPowerType: EditText,
        val gigantamax: CheckBox,
        val dynamaxLevel: EditText,
        val teraType: EditText,
        val advancedFields: LinearLayout,
        val advancedToggle: Button
    )

    private fun createTeamSetEditor(parent: LinearLayout, index: Int, set: ShowdownTeamSet, expanded: Boolean): TeamSetEditor {
        val density = resources.displayMetrics.density
        val slotHeader = Button(this).apply {
            isAllCaps = false
            minLines = 2
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setPadding((18f * density).toInt(), (8f * density).toInt(), (18f * density).toInt(), (8f * density).toInt())
        }
        val moveUpButton = Button(this).apply {
            isAllCaps = false
            text = "↑ Move up"
            setTextSize(14f)
            minHeight = (44f * density).toInt()
            minimumHeight = minHeight
            setPadding((10f * density).toInt(), 0, (10f * density).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(15, 50, 67))
                setStroke((1f * density).toInt(), Color.rgb(53, 117, 127))
                cornerRadius = 12f * density
            }
        }
        val moveDownButton = Button(this).apply {
            isAllCaps = false
            text = "↓ Move down"
            setTextSize(14f)
            minHeight = (44f * density).toInt()
            minimumHeight = minHeight
            setPadding((10f * density).toInt(), 0, (10f * density).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(15, 50, 67))
                setStroke((1f * density).toInt(), Color.rgb(53, 117, 127))
                cornerRadius = 12f * density
            }
        }
        val orderBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(moveUpButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = (6f * density).toInt()
            })
            addView(moveDownButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = (6f * density).toInt()
            })
        }
        val nickname = teamField("Nickname", set.nickname)
        val species = teamAutocompleteField("Species", set.species, emptyList())
        val item = teamAutocompleteField("Item", set.item, emptyList())
        val ability = teamAutocompleteField("Ability", set.ability, emptyList())
        val moves = (0 until 4).map { moveIndex ->
            teamAutocompleteField("Move ${moveIndex + 1}", set.moves.getOrNull(moveIndex).orEmpty(), emptyList())
        }
        val movesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8f * density).toInt(), 0, (4f * density).toInt())
            addView(TextView(this@MainActivity).apply {
                text = "Moves · choose up to four"
                setTextSize(15f)
                setTextColor(0xffa9e8e2.toInt())
                setPadding((2f * density).toInt(), 0, 0, (6f * density).toInt())
            }, LinearLayout.LayoutParams(-1, -2))
            moves.forEachIndexed { index, field ->
                addView(field, LinearLayout.LayoutParams(-1, -2).apply {
                    if (index > 0) topMargin = (6f * density).toInt()
                })
            }
        }
        val nature = teamAutocompleteField("Nature", set.nature, emptyList())
        val evs = teamStatEditor("EVs · max 252 each / 510 total", set.evs, 0)
        val gender = teamField("Gender M or F", set.gender)
        val ivs = teamStatEditor("IVs · max 31", set.ivs, 31)
        val shiny = CheckBox(this).apply { text = "Shiny"; isChecked = set.shiny }
        val level = teamField("Level", set.level.takeUnless { it == 100 }?.toString().orEmpty())
        val happiness = teamField("Happiness", set.happiness.takeUnless { it == 255 }?.toString().orEmpty())
        val pokeBall = teamField("Poké Ball", set.pokeBall)
        val hiddenPowerType = teamAutocompleteField("Hidden Power type", set.hiddenPowerType, emptyList())
        val gigantamax = CheckBox(this).apply { text = "Gigantamax"; isChecked = set.gigantamax }
        val dynamaxLevel = teamField("Dynamax level", set.dynamaxLevel.takeUnless { it == 10 }?.toString().orEmpty())
        val teraType = teamAutocompleteField("Tera type", set.teraType, emptyList())
        val advancedFields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (set.hasAdvancedDetails()) View.VISIBLE else View.GONE
        }
        val advancedToggle = Button(this).apply {
            isAllCaps = false
            text = if (set.hasAdvancedDetails()) "Hide advanced details" else "Show advanced details"
            setOnClickListener {
                val visible = advancedFields.visibility != View.VISIBLE
                advancedFields.visibility = if (visible) View.VISIBLE else View.GONE
                text = if (visible) "Hide advanced details" else "Show advanced details"
            }
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expanded) View.VISIBLE else View.GONE
            setPadding((12f * density).toInt(), (4f * density).toInt(), (12f * density).toInt(), (4f * density).toInt())
        }
        val editor = TeamSetEditor(
            index = index,
            section = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.rgb(12, 37, 52))
                    setStroke((1f * density).toInt(), Color.rgb(45, 110, 123))
                    cornerRadius = 18f * density
                }
            },
            slotHeader = slotHeader,
            moveUpButton = moveUpButton,
            moveDownButton = moveDownButton,
            details = details,
            nickname = nickname,
            species = species,
            item = item,
            ability = ability,
            moves = moves,
            movesContainer = movesContainer,
            nature = nature,
            evs = evs,
            gender = gender,
            ivs = ivs,
            shiny = shiny,
            level = level,
            happiness = happiness,
            pokeBall = pokeBall,
            hiddenPowerType = hiddenPowerType,
            gigantamax = gigantamax,
            dynamaxLevel = dynamaxLevel,
            teraType = teraType,
            advancedFields = advancedFields,
            advancedToggle = advancedToggle
        )
        listOf(
            editor.gender,
            editor.shiny,
            editor.level,
            editor.happiness,
            editor.pokeBall,
            editor.hiddenPowerType,
            editor.gigantamax,
            editor.dynamaxLevel,
            editor.teraType
        ).forEach(advancedFields::addView)
        listOf(
            editor.nickname,
            editor.species,
            editor.item,
            editor.ability,
            editor.movesContainer,
            editor.nature,
            editor.evs.container,
            editor.ivs.container,
            orderBar,
            editor.advancedToggle,
            editor.advancedFields
        ).forEach(details::addView)
        editor.section.addView(slotHeader, LinearLayout.LayoutParams(-1, -2))
        editor.section.addView(details, LinearLayout.LayoutParams(-1, -2))
        slotHeader.setOnClickListener {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (details.visibility == View.VISIBLE) moveDex.load { updateTeamEditorSuggestions(editor) }
        }
        val summaryFields = listOf<EditText>(
            nickname,
            species,
            item,
            ability,
            nature,
            gender,
            level,
            happiness,
            pokeBall,
            hiddenPowerType,
            dynamaxLevel,
            teraType
        ) + moves + evs.fields + ivs.fields
        summaryFields
            .forEach { field ->
                field.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = updateTeamSetSummary(editor)
                    override fun afterTextChanged(editable: Editable?) = Unit
                })
            }
        shiny.setOnCheckedChangeListener { _, _ -> updateTeamSetSummary(editor) }
        gigantamax.setOnCheckedChangeListener { _, _ -> updateTeamSetSummary(editor) }
        updateTeamSetSummary(editor)
        parent.addView(editor.section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (10f * density).toInt() })
        return editor
    }

    private fun ShowdownTeamSet.hasAdvancedDetails() = gender.isNotBlank() || shiny || level != 100 || happiness != 255 ||
        pokeBall.isNotBlank() || hiddenPowerType.isNotBlank() || gigantamax || dynamaxLevel != 10 || teraType.isNotBlank()

    private fun updateTeamSetSummary(editor: TeamSetEditor) {
        val set = readTeamSetEditor(editor)
        val subject = set.species.trim().ifBlank { set.nickname.trim() }
        if (subject.isBlank()) {
            editor.slotHeader.text = "Pokémon ${editor.index + 1}\nEmpty slot · tap to edit"
            return
        }
        val details = buildList {
            set.item.takeIf(String::isNotBlank)?.let(::add)
            set.moves.size.takeIf { it > 0 }?.let { add("$it move${if (it == 1) "" else "s"}") }
            set.nature.takeIf(String::isNotBlank)?.let(::add)
            set.teraType.takeIf(String::isNotBlank)?.let { add("Tera $it") }
        }
        val nickname = set.nickname.trim().takeIf { it.isNotBlank() && !it.equals(subject, true) }
        val title = nickname?.let { "$it · $subject" } ?: subject
        editor.slotHeader.text = buildString {
            append("Pokémon ${editor.index + 1} · $title")
            if (details.isNotEmpty()) append("\n${details.joinToString(" · ")}")
            else append("\nTap to edit this slot")
        }
    }

    private fun teamField(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        setText(value)
    }

    private fun teamStatEditor(title: String, values: List<Int>, default: Int): TeamStatEditor {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8f * density).toInt(), 0, (4f * density).toInt())
        }
        container.addView(TextView(this).apply {
            text = title
            setTextSize(15f)
            setTextColor(0xffa9e8e2.toInt())
            setPadding((2f * density).toInt(), 0, 0, (6f * density).toInt())
        }, LinearLayout.LayoutParams(-1, -2))
        val fields = mutableListOf<EditText>()
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(0 until 3, 3 until 6).forEach { rowRange ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowRange.forEach { index ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    setPadding((8f * density).toInt(), (6f * density).toInt(), (8f * density).toInt(), (8f * density).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(9, 31, 44))
                        setStroke((1f * density).toInt(), Color.rgb(54, 130, 143))
                        cornerRadius = 12f * density
                    }
                }
                cell.addView(TextView(this).apply {
                    text = TEAM_STAT_NAMES[index]
                    setTextSize(14f)
                    setTextColor(0xffd6f3f0.toInt())
                    gravity = android.view.Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2))
                val field = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    setSingleLine(true)
                    gravity = android.view.Gravity.CENTER
                    setTextSize(17f)
                    hint = default.toString()
                    setText(values.getOrNull(index)?.takeUnless { it == default }?.toString().orEmpty())
                    setPadding((4f * density).toInt(), 0, (4f * density).toInt(), 0)
                    minHeight = (44f * density).toInt()
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(15, 50, 67))
                        setStroke((1f * density).toInt(), Color.rgb(45, 110, 123))
                        cornerRadius = 10f * density
                    }
                }
                fields += field
                cell.addView(field, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (4f * density).toInt() })
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index % 3 != 0) leftMargin = (6f * density).toInt()
                    if (index % 3 != 2) rightMargin = (6f * density).toInt()
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                if (rowRange.first != 0) topMargin = (8f * density).toInt()
            })
        }
        container.addView(grid, LinearLayout.LayoutParams(-1, -2))
        return TeamStatEditor(container, fields)
    }

    private fun teamAutocompleteField(hint: String, value: String, suggestions: List<String>): AutoCompleteTextView = AutoCompleteTextView(this).apply {
        this.hint = hint
        setSingleLine(true)
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        threshold = 1
        styleTeamSuggestions(this, suggestions)
        setText(value)
    }

    private fun updateTeamSuggestions(field: EditText, suggestions: List<String>) {
        (field as? AutoCompleteTextView)?.let { styleTeamSuggestions(it, suggestions) }
    }

    private fun updateTeamEditorSuggestions(editor: TeamSetEditor) {
        val pokemonNames = moveDex.pokemonNames()
        val itemNames = moveDex.itemNames()
        val abilityNames = moveDex.abilityNames()
        val moveNames = moveDex.moveNames()
        val natureNames = moveDex.natureNames()
        val typeNames = ShowdownMoveDex.typeNames()
        val teraTypeNames = ShowdownMoveDex.teraTypeNames()
        updateTeamSuggestions(editor.species, pokemonNames)
        updateTeamSuggestions(editor.item, itemNames)
        updateTeamSuggestions(editor.ability, abilityNames)
        editor.moves.forEach { updateTeamSuggestions(it, moveNames) }
        updateTeamSuggestions(editor.nature, natureNames)
        updateTeamSuggestions(editor.hiddenPowerType, typeNames)
        updateTeamSuggestions(editor.teraType, teraTypeNames)
    }

    private fun styleTeamSuggestions(field: AutoCompleteTextView, suggestions: List<String>) {
        field.setAdapter(ShowdownSuggestionAdapter(this, suggestions))
        field.setDropDownBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.rgb(9, 29, 44))
            setStroke((resources.displayMetrics.density).toInt(), Color.rgb(54, 130, 143))
            cornerRadius = 18f * resources.displayMetrics.density
        })
        field.dropDownVerticalOffset = (6f * resources.displayMetrics.density).toInt()
    }

    private fun populateTeamSetEditor(editor: TeamSetEditor, set: ShowdownTeamSet) {
        editor.nickname.setText(set.nickname)
        editor.species.setText(set.species)
        editor.item.setText(set.item)
        editor.ability.setText(set.ability)
        editor.moves.forEachIndexed { index, field -> field.setText(set.moves.getOrNull(index).orEmpty()) }
        editor.nature.setText(set.nature)
        populateTeamStatEditor(editor.evs.fields, set.evs, 0)
        editor.gender.setText(set.gender)
        populateTeamStatEditor(editor.ivs.fields, set.ivs, 31)
        editor.shiny.isChecked = set.shiny
        editor.level.setText(set.level.takeUnless { it == 100 }?.toString().orEmpty())
        editor.happiness.setText(set.happiness.takeUnless { it == 255 }?.toString().orEmpty())
        editor.pokeBall.setText(set.pokeBall)
        editor.hiddenPowerType.setText(set.hiddenPowerType)
        editor.gigantamax.isChecked = set.gigantamax
        editor.dynamaxLevel.setText(set.dynamaxLevel.takeUnless { it == 10 }?.toString().orEmpty())
        editor.teraType.setText(set.teraType)
        val visible = set.hasAdvancedDetails()
        editor.advancedFields.visibility = if (visible) View.VISIBLE else View.GONE
        editor.advancedToggle.text = if (visible) "Hide advanced details" else "Show advanced details"
        updateTeamSetSummary(editor)
    }

    private fun readTeamSetEditor(editor: TeamSetEditor): ShowdownTeamSet = ShowdownTeamSet(
        nickname = editor.nickname.text.toString(),
        species = editor.species.text.toString(),
        item = editor.item.text.toString(),
        ability = editor.ability.text.toString(),
        moves = editor.moves.map { it.text.toString().trim() }.filter(String::isNotBlank),
        nature = editor.nature.text.toString(),
        evs = editorValues(editor.evs.fields, 0),
        gender = editor.gender.text.toString(),
        ivs = editorValues(editor.ivs.fields, 31),
        shiny = editor.shiny.isChecked,
        level = editor.level.text.toString().toIntOrNull() ?: 100,
        happiness = editor.happiness.text.toString().toIntOrNull() ?: 255,
        pokeBall = editor.pokeBall.text.toString(),
        hiddenPowerType = editor.hiddenPowerType.text.toString(),
        gigantamax = editor.gigantamax.isChecked,
        dynamaxLevel = editor.dynamaxLevel.text.toString().toIntOrNull() ?: 10,
        teraType = editor.teraType.text.toString()
    )

    private fun populateTeamStatEditor(fields: List<EditText>, values: List<Int>, default: Int) {
        fields.forEachIndexed { index, field ->
            field.setText(values.getOrNull(index)?.takeUnless { it == default }?.toString().orEmpty())
        }
    }

    private fun editorValues(fields: List<EditText>, default: Int): List<Int> = (0 until 6).map { index ->
        fields.getOrNull(index)?.text?.toString()?.trim()?.toIntOrNull() ?: default
    }

    private fun showChatComposer() {
        if (showdownConnection == null) {
            session.setConnectionStatus("Connect to Showdown before opening chat.")
            return
        }
        if (activeBattleRoomId == null) {
            showLobbyChatDialog()
            return
        }
        val input = EditText(this).apply {
            hint = "Send a message"
            setSingleLine(true)
        }
        ShowdownDialogBuilder(this)
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

    private fun showReplayActions() {
        ShowdownDialogBuilder(this)
            .setTitle("Battle replay")
            .setItems(arrayOf("Copy transcript", "Load replay URL")) { _, selected ->
                if (selected == 0) copyBattleTranscript() else showReplayUrlDialog()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveBattleReplay() {
        val roomId = activeBattleRoomId ?: completedBattleRoomId
        if (roomId == null) {
            session.setConnectionStatus("There is no completed battle to save.")
            return
        }
        if (showdownConnection?.send(roomId, "/savereplay") == true) {
            session.setConnectionStatus("Saving the battle replay…")
        } else {
            session.setConnectionStatus("The battle replay could not be saved.")
        }
    }

    private fun showReplayUploaded(url: String) {
        ShowdownDialogBuilder(this)
            .setTitle("Replay saved")
            .setMessage(url)
            .setNegativeButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Showdown replay", url))
                session.setConnectionStatus("Replay URL copied to the clipboard.")
            }
            .setPositiveButton("Open") { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { session.setConnectionStatus("Open $url in a browser.") }
            }
            .show()
    }

    private fun showReplayControls() {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.5f)
        val labels = mutableListOf<String>()
        if (session.isReplayMode()) labels += if (replayPaused) "Resume replay" else "Pause replay"
        labels += speeds.map { "Set battle speed to ${it.trimTrailingZero()}×" }
        ShowdownDialogBuilder(this)
            .setTitle("Battle controls · ${replaySpeed.trimTrailingZero()}×")
            .setItems(labels.toTypedArray()) { _, selected ->
                if (session.isReplayMode() && selected == 0) {
                    setReplayPaused(!replayPaused)
                } else {
                    val speedIndex = selected - if (session.isReplayMode()) 1 else 0
                    speeds.getOrNull(speedIndex)?.let(::setReplaySpeed)
                }
            }
            .setNeutralButton("More actions") { _, _ -> showReplayActions() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showReplayUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://replay.pokemonshowdown.com/..."
            setSingleLine(true)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipboardValue = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
            if (ShowdownReplayImporter.normalize(clipboardValue) != null) setText(clipboardValue)
        }
        ShowdownDialogBuilder(this)
            .setTitle("Load Showdown replay")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Load") { _, _ ->
                val source = input.text.toString().trim()
                val normalized = ShowdownReplayImporter.normalize(source)
                if (normalized == null) {
                    session.setConnectionStatus("Paste a replay.pokemonshowdown.com URL.")
                } else {
                    loadReplay(normalized)
                }
            }
            .show()
    }

    private fun handleIncomingIntent(intent: Intent): Boolean {
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val replaySource = ShowdownReplayImporter.intentSource(
            intent.action,
            intent.dataString,
            sharedText
        )
        if (replaySource != null) {
            val normalized = ShowdownReplayImporter.normalize(replaySource) ?: return false
            loadReplay(normalized)
            return true
        }
        val teamSource = ShowdownTeamUrlImporter.intentSource(intent.action, intent.dataString, sharedText) ?: return false
        importIncomingTeam(teamSource)
        return true
    }

    private fun importIncomingTeam(source: String) {
        val normalized = ShowdownTeamUrlImporter.normalize(source)
        if (normalized != null) {
            session.setConnectionStatus("Fetching shared team…")
            teamUrlFetcher.fetch(source) { result ->
                result.onSuccess { payload ->
                    val imported = importTeamBackup(payload.text, payload.name, payload.format)
                    refreshTeamLibraryAfterImport(imported, returnToTeamLibrary = true)
                }.onFailure {
                    session.setConnectionStatus("Could not fetch that team URL. Paste the team export instead.")
                }
            }
        } else {
            val imported = importTeamBackup(source, "Shared team", loadMatchFormat().id)
            refreshTeamLibraryAfterImport(imported, returnToTeamLibrary = true)
        }
    }

    private fun loadReplay(normalized: String) {
        if (replayLoadRequest == normalized) return
        if (activeReplayLink == normalized && session.isReplayMode()) return
        replayLoadRequest = normalized
        session.setConnectionStatus("Loading replay…")
        replayFetcher.fetch(normalized) { result ->
            if (replayLoadRequest != normalized) return@fetch
            replayLoadRequest = null
            result.onSuccess {
                activeReplayLink = normalized
                showReplay(it)
            }
                .onFailure {
                    session.setConnectionStatus("That replay could not be loaded.")
                }
        }
    }

    private fun showReplay(replay: ShowdownReplayPayload) {
        if (isFinishing) return
        activeBattleRoomId = null
        battleIsSpectator = false
        completedBattleRoomId = null
        pendingBattleJoinRoomId = null
        pendingBattleSearchFormat = null
        pendingBattleSearchLabel = null
        pendingBattleSearchUsesRandomTeams = null
        pendingBattleSearchTeamPacked = null
        battleProtocolReady = false
        pendingDecisionCommand = null
        activeSearchFormat = null
        pendingSearch = false
        pendingLobbyCommands = null
        pendingLobbyStatus = null
        reconnectLobbyCommands = null
        shouldMaintainConnection = false
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectScheduled = false
        showdownConnection?.close()
        showdownConnection = null
        clearPersistedLobbyState()
        chatRoomDialog?.dismiss()
        tournamentDialog?.dismiss()
        tournamentDialog = null
        chatRoomState.clear()
        pendingChatRoomId = null
        clearBattlePlayback()
        session.prepareForLobby()
        replay.players.firstOrNull()?.let(session::setLocalUsername)
        session.setReplayMode(true)
        session.setLiveBattleActive(true)
        val replayStartsPaused = restoredReplayPaused
        replayPaused = false
        replayPausedForLifecycle = false
        replaySpeed = restoredReplaySpeed.coerceIn(0.25f, 4f)
        ensureShowdownMoveEffects()
        showdownMoveEffects?.setPlaybackSpeed(replaySpeed)
        battleScene?.setPlaybackSpeed(replaySpeed)
        showdownMoveEffects?.setPlaybackPaused(false)
        enqueueBattlePlayback(null, null, replay.log.lines(), resetOnBattleInit = false)
        if (replayStartsPaused) setReplayPaused(true)
        replayStatus = "Replay: ${replay.title}"
        if (replayPaused) updateReplayStatus()
        restoredReplayPaused = false
        restoredReplaySpeed = 1f
    }

    private fun showFormatPicker(
        initialFormat: BattleSession.MatchFormat = session.matchFormat,
        searchOnly: Boolean = true,
        onSelected: (BattleSession.MatchFormat) -> Unit = { format ->
            if (activeSearchFormat != null || pendingSearch) cancelActiveSearch()
            session.setMatchFormat(format)
            getSharedPreferences("showdown", MODE_PRIVATE).edit()
                .putString("match_format", format.id)
                .putString("match_format_label", format.label)
                .apply()
        }
    ) {
        val formats = session.availableMatchFormats()
            .filter { if (searchOnly) it.canSearch else it.canChallenge }
            .ifEmpty { session.availableMatchFormats() }
        val selectedIndex = formats.indexOfFirst { it.id.trim().equals(initialFormat.id.trim(), true) }.coerceAtLeast(0)
        ShowdownDialogBuilder(this)
            .setTitle("Battle format")
            .setSingleChoiceItems(formats.map { readableFormatLabel(it.id) }.toTypedArray(), selectedIndex) { _, selected ->
                val format = ShowdownTeamLibraryQuery.matchFormat(formats[selected].id, session.availableMatchFormats())
                onSelected(format)
            }
            .show()
    }

    private fun ensureSearchableMatchFormat(preferred: BattleSession.MatchFormat = session.matchFormat): BattleSession.MatchFormat {
        val advertisedFormats = session.availableMatchFormats()
        val format = advertisedFormats.firstOrNull { it.id.trim().equals(preferred.id.trim(), true) && it.canSearch }
            ?: advertisedFormats.firstOrNull { it.id.trim().equals(BattleSession.MatchFormat.GEN9_RANDOM.id.trim(), true) && it.canSearch }
            ?: advertisedFormats.firstOrNull { it.canSearch }
            ?: preferred.takeIf { it.canSearch }
            ?: BattleSession.MatchFormat.GEN9_RANDOM
        val normalizedId = format.id.trim()
        val advertised = advertisedFormats.firstOrNull { it.id.trim().equals(normalizedId, true) }
        val normalizedFormat = format.copy(
            id = normalizedId,
            label = advertised?.let { ShowdownTeamLibraryQuery.displayFormat(normalizedId, advertisedFormats) }
                ?: format.label.trim().ifBlank { ShowdownTeamLibraryQuery.displayFormat(normalizedId) },
            menuLabel = format.menuLabel.trim().takeUnless { it.isBlank() || it.equals(normalizedId, true) }
                ?: format.label.trim().ifBlank { ShowdownTeamLibraryQuery.displayFormat(normalizedId) }
        )
        if (normalizedFormat.id != session.matchFormat.id || normalizedFormat.label != session.matchFormat.label || normalizedFormat.menuLabel != session.matchFormat.menuLabel) {
            session.setMatchFormat(normalizedFormat)
            getSharedPreferences("showdown", MODE_PRIVATE).edit()
                .putString("match_format", normalizedFormat.id)
                .putString("match_format_label", normalizedFormat.label)
                .apply()
        }
        return normalizedFormat
    }

    private fun confirmForfeit() {
        val roomId = activeBattleRoomId
        if (roomId == null) {
            session.setConnectionStatus("There is no live battle to forfeit.")
            return
        }
        ShowdownDialogBuilder(this)
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

    private fun leaveBattle() {
        val roomId = activeBattleRoomId
        if (roomId == null) {
            session.setConnectionStatus("There is no battle room to leave.")
            return
        }
        if (showdownConnection?.send(roomId, "/leave") != true) {
            session.setConnectionStatus("Unable to leave the battle room.")
            return
        }
        leftBattleRoomId = roomId
        clearBattleRoomState()
        session.setConnectionStatus("Left the battle room.")
    }

    private fun toggleBattleTimer() {
        val roomId = activeBattleRoomId
        if (roomId == null || !session.isLiveBattleActive() || session.isReplayMode() || session.isSpectatorMode()) {
            session.setConnectionStatus("There is no live battle timer to change.")
            return
        }
        val command = if (session.isBattleTimerEnabled()) "/timer off" else "/timer on"
        if (showdownConnection?.send(roomId, command) == true) {
            session.setConnectionStatus(if (command.endsWith("on")) "Starting the battle timer…" else "Stopping the battle timer…")
        } else {
            session.setConnectionStatus("Unable to change the battle timer.")
        }
    }

    private fun cancelChoice() {
        val roomId = activeBattleRoomId
        if (roomId == null || !session.canCancelChoice() || !session.isBattleParticipant() || session.isReplayMode() || session.isSpectatorMode()) {
            session.setConnectionStatus("There is no cancellable battle choice.")
            return
        }
        if (showdownConnection?.send(roomId, "/undo") == true) {
            session.setConnectionStatus("Cancelling your last choice…")
        } else {
            session.setConnectionStatus("Unable to cancel your last choice.")
        }
    }

    private fun loadServerEndpoint(): ShowdownServerEndpoint {
        val saved = getSharedPreferences("showdown", MODE_PRIVATE).getString("server_endpoint", null)
        return saved?.let(ShowdownServerEndpoint::fromInput) ?: ShowdownServerEndpoint.playShowdown
    }

    private fun loadMatchFormat(): BattleSession.MatchFormat {
        val preferences = getSharedPreferences("showdown", MODE_PRIVATE)
        val saved = preferences.getString("match_format", null)
        val normalizedSaved = saved?.trim()
        val savedLabel = preferences.getString("match_format_label", null)?.trim()
        val normalizedSavedLabel = savedLabel.orEmpty().lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        if (normalizedSaved?.lowercase(Locale.ROOT) == "hdmatchup" || normalizedSavedLabel == "hdmatchup") {
            val defaultFormat = BattleSession.MatchFormat.GEN9_RANDOM
            preferences.edit()
                .putString("match_format", defaultFormat.id)
                .putString("match_format_label", defaultFormat.label)
                .apply()
            return defaultFormat
        }
        return BattleSession.MatchFormat.defaults.firstOrNull { it.id.trim().equals(normalizedSaved, true) }
            ?: saved?.let {
                BattleSession.MatchFormat(
                    id = it.trim(),
                    label = savedLabel.takeUnless { label -> label.isNullOrBlank() } ?: it.trim(),
                    canSearch = false
                )
            }
            ?: BattleSession.MatchFormat.GEN9_RANDOM
    }

    private fun loadUserPreferences() {
        val preferences = getSharedPreferences("showdown", MODE_PRIVATE)
        replaySpeed = preferences.getFloat("battle_speed", DEFAULT_BATTLE_SPEED).coerceIn(0.25f, 4f)
        val runtimeSpriteStyle = BattleSession.SpriteStyle.MODERN_3D
        preferences.edit()
            .putString("sprite_style", runtimeSpriteStyle.name)
            .putBoolean("sprite_style_migrated", true)
            .apply()
        session.applyUserPreferences(
            soundEffects = preferences.getBoolean("sound_effects", true),
            music = preferences.getBoolean("music", true),
            haptics = preferences.getBoolean("haptics", true)
        )
    }

    private fun persistUserPreferences() {
        getSharedPreferences("showdown", MODE_PRIVATE).edit()
            .putBoolean("sound_effects", session.soundEffectsEnabled)
            .putBoolean("music", session.musicEnabled)
            .putBoolean("haptics", session.hapticsEnabled)
            .putString("sprite_style", session.spriteStyle.name)
            .putBoolean("sprite_style_migrated", true)
            .apply()
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
        private lateinit var controllerFrame: FrameLayout

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount > 0 && isConfirmButton(event.keyCode)) return true
                if (handleControllerKey(event.keyCode)) return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            return if (handleControllerMotionEvent(event)) true else super.dispatchGenericMotionEvent(event)
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setCancelable(false)
            configurePresentationWindow(window)
            val presentationContext = getContext()
            val frame = FrameLayout(presentationContext)
            controllerFrame = frame
            commandDeck = CommandDeckView(presentationContext, session, spriteCache, object : CommandDeckView.InteractionListener {
                override fun onNavigation() {
                    battleAudio.playNavigation()
                }

                override fun onConfirmation() {
                    battleAudio.playConfirm()
                }

                override fun onCancelChoice() {
                    cancelChoice()
                    battleAudio.playCancel()
                }
            })
            frame.addView(commandDeck, FrameLayout.LayoutParams(-1, -1))
            frame.isFocusable = true
            frame.isFocusableInTouchMode = true
            setContentView(frame)
            configurePresentationWindow(window)
            window?.decorView?.isFocusable = true
            window?.decorView?.isFocusableInTouchMode = true
            window?.takeKeyEvents(true)
            window?.decorView?.requestFocus()
            frame.requestFocus()
            frame.requestFocusFromTouch()
        }

        fun requestControllerFocus() {
            window?.decorView?.post {
                if (!isShowing || !::controllerFrame.isInitialized) return@post
                configurePresentationWindow(window)
                window?.decorView?.isFocusable = true
                window?.decorView?.isFocusableInTouchMode = true
                window?.takeKeyEvents(true)
                window?.decorView?.requestFocusFromTouch()
                controllerFrame.requestFocusFromTouch()
            }
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            if (hasFocus) requestControllerFocus()
        }
    }

    private fun configurePresentationWindow(presentationWindow: Window?) {
        presentationWindow ?: return
        presentationWindow.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
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
        const val SESSION_RESTORE_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_BATTLE_SPEED = 0.75f
        private val TEAM_STAT_NAMES = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
    }
}
