package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecycleContractTest {
    @Test
    fun liveEffectsPauseAndResumeWithTheActivityLifecycle() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val audioSource = File("src/main/kotlin/dev/adrian/showdown/BattleAudio.kt").readText()
        val pauseIndex = source.indexOf("pauseLivePlaybackForLifecycle()")
        val resumeIndex = source.indexOf("resumeLivePlaybackForLifecycle()")

        assertTrue(pauseIndex >= 0)
        assertTrue(resumeIndex > pauseIndex)
        assertTrue(source.contains("livePlaybackPausedForLifecycle = true"))
        assertTrue(source.contains("livePlaybackPausedForLifecycle = false"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(true)"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(false)"))
        assertTrue(source.contains("battleScene?.setPlaybackPaused(true)"))
        assertTrue(source.contains("battleScene?.setPlaybackPaused(false)"))
        assertTrue(source.contains("displayRefreshScheduler.cancel()"))
        assertTrue(source.contains("battleAudio.pauseBattleCues()"))
        assertTrue(source.contains("battleAudio.resumeBattleCues()"))
        assertTrue(audioSource.contains("activeBattleStreamIds.forEach(battleSoundPool::pause)"))
        assertTrue(audioSource.contains("activeBattleStreamIds.forEach(battleSoundPool::resume)"))
    }

    @Test
    fun replaySpeedIsPropagatedToBattleAudio() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("battleAudio.setPlaybackSpeed(replaySpeed)"))
        assertTrue(source.contains("replaySpeed = BattlePlaybackSpeed.coerce(restoredReplaySpeed)"))
    }

    @Test
    fun resumeRestoresTheThorPresentationIfAndroidDismissedIt() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val resume = source.substringAfter("override fun onResume() {").substringBefore("override fun onWindowFocusChanged")

        assertTrue(resume.contains("showSecondaryDisplay()"))
        assertTrue(source.contains("secondaryPresentationRequested = true"))
        assertTrue(source.contains("secondaryPresentation?.let { presentation ->"))
        assertTrue(source.contains("presentation.requestControllerFocus()"))
        assertTrue(source.contains("if (secondaryPresentation !== presentation)"))
        assertFalse(source.contains("if (secondaryPresentation?.isShowing == false) secondaryPresentation = null"))
    }

    @Test
    fun clearsAStalePresentationWhenTheDisplayDisappearsDuringShow() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val show = source.substringAfter("private fun showSecondaryDisplay()").substringBefore("private fun findThorDisplay")

        assertTrue(show.contains("catch (_: WindowManager.BadTokenException)"))
        assertTrue(show.contains("catch (_: WindowManager.InvalidDisplayException)"))
        assertTrue(show.contains("if (secondaryPresentation === presentation) secondaryPresentation = null"))
    }

    @Test
    fun keepsTheThorPresentationFocusableAndTouchableAfterItsContentIsAttached() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val presentation = source.substringAfter("private inner class ThorPresentation")
            .substringBefore("private fun configurePresentationWindow")

        assertTrue(presentation.contains("setContentView(frame)"))
        assertTrue(presentation.contains("val presentationContext = getContext()"))
        assertTrue(presentation.contains("configurePresentationWindow(window)"))
        assertTrue(presentation.contains("window?.takeKeyEvents(true)"))
        assertTrue(presentation.contains("frame.requestFocusFromTouch()"))
        assertTrue(presentation.contains("controllerFrame.requestFocusFromTouch()"))
        assertTrue(source.contains("FLAG_NOT_FOCUSABLE or"))
        assertTrue(source.contains("FLAG_NOT_TOUCHABLE"))
    }

    @Test
    fun clearsSwitchHitRegionsAcrossDecisionModeTransitions() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("private var lastRenderedTeamDecision = false"))
        assertTrue(source.contains("if (teamDecision != lastRenderedTeamDecision || decisionKind != lastRenderedDecisionKind)"))
        assertTrue(source.contains("resetDecisionTransitionState()"))
        assertTrue(source.contains("if (isTeamDecision() != lastRenderedTeamDecision || session.decisionKind != lastRenderedDecisionKind)"))
        assertTrue(source.contains("teamBounds.fill(null)"))
        assertTrue(source.contains("moveBounds.fill(null)"))
        assertTrue(source.contains("targetBounds.fill(null)"))
    }

    @Test
    fun clearsTransientMoveStateWhenTheDecisionKindChanges() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("private var lastRenderedDecisionKind: BattleSession.DecisionKind? = null"))
        assertTrue(source.contains("decisionKind != lastRenderedDecisionKind"))
        assertTrue(source.contains("lastRenderedDecisionKind = decisionKind"))
        assertTrue(source.contains("pressedMoveIndex = null"))
        assertTrue(source.contains("releasedMoveIndex = null"))
    }

    @Test
    fun refreshesCanvasHitRegionsBeforeHandlingRapidStateTransitions() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("refreshTouchBoundsForCurrentState()"))
        assertTrue(source.contains("private fun layoutMoveTouchBounds"))
        assertTrue(source.contains("private fun layoutTeamTouchBounds"))
        assertTrue(source.contains("private fun layoutMenuTouchBounds"))
        assertTrue(source.contains("private fun layoutTargetTouchBounds"))
    }

    @Test
    fun decisionTeamPanelDoesNotFallBackToTheEmptyPanelDuringBattleStateTransition() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()
        val teamRenderer = source.substringAfter("private fun drawTeam(").substringBefore("val visibleTeam")

        assertTrue(teamRenderer.contains("if (!decisionLayout && !session.isLiveBattleActive() && !session.isBattleFinished())"))
    }

    @Test
    fun teamSpritesAreRerequestedWhenTheSpriteStyleChanges() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("private val requestedTeamSprites = mutableMapOf<String, BattleSpriteRequest>()"))
        assertTrue(source.contains("val requestedSpecies = species.ifBlank { displayName }"))
        assertTrue(source.contains("val request = BattleSpriteRequest.forOpponent(requestedSpecies, session.spriteStyle, shiny)"))
        assertTrue(source.contains("if (requestedTeamSprites[displayName] == request) return"))
        assertTrue(source.contains("teamSprites.remove(displayName)"))
    }

    @Test
    fun spectatorDeckKeepsBattleFeedOnTheUpperScreen() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()
        val movesRenderer = source.substringAfter("private fun drawMoves(")
        val spectatorPanel = movesRenderer.substringAfter("if (session.isSpectatorMode() && !session.isBattleFinished())")
            .substringBefore("if (!session.isLiveBattleActive()")

        assertFalse(spectatorPanel.contains("session.latestBattleFeedEntry()"))
        assertTrue(spectatorPanel.contains("Battle action appears on the upper screen."))
    }

    @Test
    fun visibleTeamSpritesKeepAnimatedArtworkRepainting() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("val visibleAnimatedTeamSprite = (teamDecision || session.panel == BattleSession.Panel.TEAM)"))
        assertTrue(source.contains("teamSprites.values.any { it.isAnimated }"))
        assertTrue(source.contains("|| visibleAnimatedTeamSprite)"))
        assertTrue(source.contains("postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)"))
    }

    @Test
    fun onStopDismissesTheThorPresentationBeforeTheActivityLeavesTheScreen() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val stop = source.substringAfter("override fun onStop() {").substringBefore("override fun onStart")

        assertTrue(stop.contains("dismissSecondaryDisplay()"))
    }

    @Test
    fun displayCallbacksCannotCreateAThorPresentationWhileTheActivityIsPaused() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val show = source.substringAfter("private fun showSecondaryDisplay()").substringBefore("private fun findThorDisplay")
        val pause = source.substringAfter("override fun onPause() {").substringBefore("override fun onStop")
        val resume = source.substringAfter("override fun onResume() {").substringBefore("override fun onWindowFocusChanged")

        assertTrue(source.contains("private var activityResumed = false"))
        assertTrue(show.contains("!activityResumed"))
        assertTrue(pause.contains("activityResumed = false"))
        assertTrue(resume.contains("activityResumed = true"))
    }

    @Test
    fun defersTheAnimationWebViewUntilBattlePlaybackStarts() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val screenFactory = source.substringAfter("private fun createPrimaryScreen()").substringBefore("private fun ensureShowdownMoveEffects()")

        assertTrue(screenFactory.contains("primaryFrame = it"))
        assertTrue(source.contains("private fun ensureShowdownMoveEffects(): ShowdownMoveEffectsView?"))
        assertTrue(source.contains("if (lines.any { it.startsWith(\"|init|battle\") }) ensureShowdownMoveEffects()"))
        assertTrue(source.contains("frame.addView(effects, FrameLayout.LayoutParams(1, 1))"))
        assertTrue(screenFactory.contains("frame.addView(battleScene, FrameLayout.LayoutParams(-1, -1))"))
        assertFalse(screenFactory.contains("ShowdownMoveEffectsView("))
    }

    @Test
    fun defersTheMoveDexUntilBattleOrTeamEditingNeedsIt() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val onCreate = source.substringAfter("override fun onCreate(savedInstanceState: Bundle?)").substringBefore("override fun onNewIntent")
        val listener = source.substringAfter("private val protocolListener").substringBefore("private val decisionListener")
        val teamEditor = source.substringAfter("private fun showTeamEditor").substringBefore("private fun showTeamFormatPicker")

        assertFalse(onCreate.contains("moveDex.load"))
        assertTrue(source.contains("private fun bindMoveDexResolvers()"))
        assertTrue(source.contains("private fun ensureMoveDexLoaded()"))
        assertTrue(listener.contains("ensureMoveDexLoaded()"))
        assertFalse(teamEditor.contains("moveDex.load"))
        assertTrue(source.contains("private fun ensureTeamEditorSuggestions(editor: TeamSetEditor)"))
        assertTrue(source.contains("field.setOnFocusChangeListener"))
        assertTrue(source.contains("if (details.visibility == View.VISIBLE) ensureTeamEditorSuggestions(editor)"))
    }

    @Test
    fun doesNotFeedInitialBattlePacketToFreshlySeededEffectsViewTwice() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val listener = source.substringAfter("private val protocolListener").substringBefore("private val decisionListener")

        assertTrue(listener.contains("runOnUiThread {"))
        assertTrue(listener.contains("applyBattleProtocolToEffects(lines)"))
        assertTrue(source.contains("val effectsAlreadyCreated = showdownMoveEffects != null"))
        assertTrue(source.contains("if (!effectsAlreadyCreated && lines.any { it.startsWith(\"|init|battle\") }) return"))
    }

    @Test
    fun keepsLiveChoicesUntilShowdownAcknowledgesThem() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("pendingDecisionCommand = command"))
        assertTrue(source.contains("pendingDecisionSentConnection = connection"))
        assertTrue(source.contains("pendingDecisionCommand = savedInstanceState?.getString(\"pending_decision_command\")"))
        assertTrue(source.contains("preferences.getString(\"pending_decision_command\", null)"))
        assertTrue(source.contains("reconcilePendingDecisionCommand(lines)"))
        assertFalse(source.contains("if (packet.connection.send(roomId, command)) pendingDecisionCommand = null"))
    }

    @Test
    fun flushesCriticalLiveRecoveryStateBeforeLifecycleOrProcessBoundaries() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val pause = source.substringAfter("override fun onPause() {").substringBefore("override fun onStop")
        val stop = source.substringAfter("override fun onStop() {").substringBefore("override fun onStart")
        val join = source.substringAfter("private fun joinMatchedBattle(").substringBefore("private fun scheduleReconnect")
        val start = source.substringAfter("private fun startLobbyConnection(").substringBefore("private fun dismissConnectionTransitionDialogs")
        val battleStart = source.substringAfter("if (startsBattle) {").substringBefore("activeSearchFormat = null")
        val persistence = source.substringAfter("private fun persistLobbyState(").substringBefore("private fun clearPersistedLobbyState")
        val clear = source.substringAfter("private fun clearPersistedLobbyState()").substringBefore("private fun clearBattleRoomState")

        assertTrue(source.contains("private fun persistLobbyState(flushToDisk: Boolean = false)"))
        assertTrue(persistence.contains("if (flushToDisk)"))
        assertTrue(persistence.contains("editor.commit()"))
        assertTrue(pause.contains("persistLobbyState(flushToDisk = true)"))
        assertTrue(stop.contains("persistLobbyState(flushToDisk = true)"))
        assertTrue(join.contains("persistLobbyState(flushToDisk = true)"))
        assertTrue(start.contains("persistLobbyState(flushToDisk = true)"))
        assertTrue(battleStart.contains("persistLobbyState(flushToDisk = true)"))
        assertTrue(source.contains("persistLobbyState(flushToDisk = true)"))
        assertTrue(clear.contains(".commit()"))
        assertTrue(source.contains("battleProtocolPlayerSlot(lines)?.let(session::restoreBattlePlayerSlot)"))
        assertTrue(source.contains("private fun downgradeBattleRecoveryToGuest()"))
        assertTrue(source.contains("downgradeBattleRecoveryToGuest()"))
        assertTrue(source.contains("battleIsSpectator = true"))
    }

    @Test
    fun restoresSavedShowdownSessionsBeforeRejoiningPersistedRooms() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("ShowdownSessionStore(this)"))
        assertTrue(source.contains("loginClient.upkeep(serverEndpoint, challenge)"))
        assertTrue(source.contains("sessionRestorePending = loginClient.hasSession()"))
        assertTrue(source.contains("!sessionRestorePending && (credentialsStore.load() == null || update.named)"))
        assertTrue(source.contains("if (update.named) \"Signed in as \${update.username}.\" else \"Ready for a battle.\""))
        assertTrue(source.contains("sessionStore.clear()"))
    }

    @Test
    fun preservesPersistedBattleRecoveryBeforeTheRestoredSnapshotArrives() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val preserveBattleSurfaceSource = source.substringAfter("val preserveBattleSurface =")
            .substringBefore("battleProtocolReady = false")

        assertTrue(preserveBattleSurfaceSource.contains("activeBattleRoomId != null"))
        assertTrue(preserveBattleSurfaceSource.contains("shouldMaintainConnection"))
        assertTrue(preserveBattleSurfaceSource.contains("!session.isBattleFinished()"))
    }

    @Test
    fun restoresAnAnonymousParticipantAsAReadOnlySpectatorAfterProcessDeath() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("ShowdownBattleRecovery.Mode.GUEST_SPECTATOR"))
        assertTrue(source.contains("session.setSpectatorMode(true)"))
        assertTrue(source.contains("battle_spectator"))
        assertFalse(source.contains("abandonUnrestorableGuestBattle()"))
        assertTrue(source.contains("battleWasParticipant = true"))
        assertTrue(source.contains("battleProtocolIdentifiesLocalPlayer(lines)"))
        assertTrue(source.contains("battleWasParticipant = battleWasParticipant || battleProtocolIdentifiesLocalPlayer(lines)"))
        assertTrue(source.contains("battleProtocolPlayerSlot(lines)?.let(session::restoreBattlePlayerSlot)"))
    }

    @Test
    fun roomListJoinsPublicBattlesAsReadOnlySpectators() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val roomSelection = source.substringAfter("selections.forEach { room ->")
            .substringBefore("val roomScroll = ScrollView(this)")

        assertTrue(roomSelection.contains("if (room.chatRoom)"))
        assertTrue(roomSelection.contains("joinSpectatorBattle(room.id)"))
        assertTrue(roomSelection.contains("pendingChatRoomId = room.id"))
    }

    @Test
    fun roomPickerJoinsPublicBattlesAsReadOnlySpectators() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val picker = source.substringAfter("private fun showBattleRoomPicker()")
            .substringBefore("private fun showRoomList()")

        assertTrue(picker.contains("joinSpectatorBattle(roomId)"))
        assertTrue(source.contains("private fun joinSpectatorBattle(roomId: String)"))
    }

    @Test
    fun closesStaleLobbyAndTeamDialogsBeforeStartingAnotherConnectionFlow() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val transition = source.substringAfter("private fun startLobbyConnection(")
            .substringBefore("private fun dismissConnectionTransitionDialogs")

        assertTrue(transition.contains("dismissConnectionTransitionDialogs()"))
        assertTrue(source.contains("private var teamLibraryDialog: ShowdownDialog? = null"))
        assertTrue(source.contains("teamLibraryDialog?.dismiss()"))
        assertTrue(source.contains("teamEditorDialog?.dismiss()"))
        assertTrue(source.contains("pokedexDialog?.dismiss()"))
    }

    @Test
    fun doesNotStackTeamLibraryDialogs() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val teamLibrary = source.substringAfter("private fun showTeamLibrary()").substringBefore("private fun showTeamRemoteLibrary")

        assertTrue(teamLibrary.contains("if (teamLibraryDialog?.isShowing == true) return"))
    }

    @Test
    fun remoteTeamBrowseKeepsTheLibraryAvailableWhenSignInIsRequired() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val teamLibrary = source.substringAfter("private fun showTeamLibrary()").substringBefore("private fun showTeamRemoteLibrary")
        val remoteLibrary = source.substringAfter("private fun showTeamRemoteLibrary(").substringBefore("private fun showRemoteTeamSearch")

        assertTrue(teamLibrary.contains("showTeamRemoteLibrary(sourceDialog = teamDialog)"))
        assertFalse(teamLibrary.contains("teamDialog?.dismiss()\n                showTeamRemoteLibrary()"))
        assertTrue(remoteLibrary.contains("sourceDialog?.dismiss()"))
        assertTrue(remoteLibrary.contains("showRemoteTeamAccessDialog"))
    }

    @Test
    fun controllerNavigationDismissesOpenCustomDialogsBeforeOpeningAnotherSurface() {
        val activitySource = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val dialogSource = File("src/main/kotlin/dev/adrian/showdown/ShowdownDialog.kt").readText()
        val listener = activitySource.substringAfter("private val clientActionListener")
            .substringBefore("private val displayListener")

        val dismissIndex = listener.indexOf("ShowdownDialog.dismissOpenDialogs(this)")
        val dispatchIndex = listener.indexOf("when (action)")

        assertTrue(dismissIndex >= 0)
        assertTrue(dismissIndex < dispatchIndex)
        assertTrue(dialogSource.contains("private val openDialogs = linkedSetOf<ShowdownDialog>()"))
        assertTrue(dialogSource.contains("fun dismissOpenDialogs(hostContext: Context)"))
        assertTrue(dialogSource.contains("openDialogs += this"))
        assertTrue(dialogSource.contains("openDialogs -= this"))
    }

    @Test
    fun keepsTeamEditorSlotsCompactAndExpandable() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("val firstExpandedIndex = sets.indexOfFirst"))
        assertTrue(source.contains("val slotHeader: Button"))
        assertTrue(source.contains("val details: LinearLayout"))
        assertTrue(source.contains("details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE"))
        assertTrue(source.contains("Pokémon \${editor.index + 1}"))
        assertTrue(source.contains("resolveTeamSetForEditor"))
        assertTrue(source.contains("moveDex.load"))
        assertTrue(source.indexOf("addView(setFields)") < source.indexOf("addView(validateButton)"))
    }

    @Test
    fun doesNotRestoreAnUnknownSavedFormatAsSearchable() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val loader = source.substringAfter("private fun loadMatchFormat()").substringBefore("private fun loadUserPreferences")

        assertTrue(loader.contains("BattleSession.MatchFormat.defaults.firstOrNull"))
        assertTrue(loader.contains("?: BattleSession.MatchFormat.GEN9_RANDOM"))
        assertTrue(loader.contains("equals(canonicalId, true)"))
        assertTrue(loader.contains("saved?.let"))
        assertTrue(loader.contains("canSearch = false"))
        assertTrue(loader.contains("ShowdownFormatCompatibility.isLegacyHdMatchup"))
        assertTrue(loader.contains("match_format_label"))
    }

    @Test
    fun normalizesPersistedSearchCommandsAgainstTheAdvertisedFormatCapabilities() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val sender = source.substringAfter("private fun sendPendingLobbyCommands").substringBefore("private fun showChallengeComposer")

        assertTrue(sender.contains("hasPendingSearchCommand"))
        assertTrue(sender.contains("startsWith(\"/search \")"))
        assertTrue(sender.contains("&& it.canSearch"))
        assertTrue(sender.contains("?: \"/search \${searchFormat.id}\""))
    }

    @Test
    fun opensSavedReplaysInsideTheClient() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val replayDialog = source.substringAfter("private fun showReplayUploaded").substringBefore("private fun showReplayControls")
        val replay = source.substringAfter("private fun showReplay(replay: ShowdownReplayPayload)").substringBefore("private fun showFormatPicker")

        assertTrue(replayDialog.contains("ShowdownReplayImporter.normalize(url)"))
        assertTrue(replayDialog.contains("loadReplay(normalized)"))
        assertFalse(replayDialog.contains("startActivity(Intent(Intent.ACTION_VIEW"))
        assertTrue(replay.contains("session.prepareForReplay()"))
        assertTrue(replay.contains("session.setConnectionStatus(\"Loading replay…\")"))
        assertTrue(replay.contains("listOf(\"|init|battle\") + lines"))
    }
}
