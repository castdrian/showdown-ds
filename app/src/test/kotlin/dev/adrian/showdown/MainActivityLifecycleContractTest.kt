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
        assertTrue(source.contains("private fun pauseBattleAudio()"))
        assertTrue(source.contains("private fun resumeBattleAudioIfActive()"))
        assertTrue(source.contains("if (!activityResumed) return"))
        assertTrue(source.contains("if (replayPaused || replayPausedForLifecycle || livePlaybackPausedForLifecycle) return"))
        assertTrue(source.contains("pauseBattleAudio()"))
        assertTrue(source.contains("resumeBattleAudioIfActive()"))
        assertTrue(source.contains("battleScene?.setPlaybackPaused(true)"))
        assertTrue(source.contains("battleScene?.setPlaybackPaused(replayPaused || replayPausedForLifecycle || livePlaybackPausedForLifecycle)"))
        assertTrue(source.contains("displayRefreshScheduler.cancel()"))
        assertTrue(source.contains("battleAudio.pauseBattleCues()"))
        assertTrue(source.contains("battleAudio.resumeBattleCues()"))
        assertTrue(audioSource.contains("pauseActiveStreams(battleSoundPool, activeBattleStreams)"))
        assertTrue(audioSource.contains("resumeActiveStreams(battleSoundPool, activeBattleStreams)"))
    }

    @Test
    fun keepsTheUpperBattleFeedPausedWhenAReplayWasManuallyPaused() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val resume = source.substringAfter("override fun onResume() {").substringBefore("override fun onWindowFocusChanged")

        assertTrue(resume.indexOf("resumeReplayForLifecycle()") < resume.indexOf("battleScene?.setPlaybackPaused(replayPaused || replayPausedForLifecycle || livePlaybackPausedForLifecycle)"))
        assertFalse(resume.contains("battleScene?.setPlaybackPaused(false)"))
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

        assertTrue(source.contains("private val requestedTeamSprites = mutableMapOf<Int, BattleSpriteRequest>()"))
        assertTrue(source.contains("val requestedSpecies = species.ifBlank { session.team().getOrNull(index).orEmpty() }"))
        assertTrue(source.contains("val request = BattleSpriteRequest.forOpponent(requestedSpecies, session.spriteStyle, shiny)"))
        assertTrue(source.contains("if (requestedTeamSprites[index] == request) return"))
        assertTrue(source.contains("teamSprites.remove(index)"))
    }

    @Test
    fun teamPreviewUsesSpeciesFallbackWithoutReplacingAnimatedArtwork() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("spriteCache.requestStaticDexSprite(requestedSpecies, request.shiny)"))
        assertTrue(source.contains("TEAM_STATIC_FALLBACK_DELAY_MILLIS"))
        assertTrue(source.indexOf("spriteCache.requestPokemon(request)") < source.indexOf("spriteCache.requestStaticDexSprite(requestedSpecies, request.shiny)"))
        assertTrue(source.contains("private fun acceptTeamSprite("))
        assertTrue(source.contains("sprite.isAnimated || teamSprites[index]?.isAnimated != true"))
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
        assertTrue(source.contains("frame.addView(effects, FrameLayout.LayoutParams(-1, -1))"))
        assertTrue(screenFactory.contains("frame.addView(battleScene, FrameLayout.LayoutParams(-1, -1))"))
        assertFalse(screenFactory.contains("ShowdownMoveEffectsView("))
    }

    @Test
    fun announcerCuesComeFromTheAnimationBridge() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val listener = source.substringAfter("private val protocolListener").substringBefore("private val decisionListener")

        assertFalse(listener.contains("BattleAnnouncerCueResolver.cuesForProtocol"))
        assertTrue(source.contains("announcerCueListener = battleAudio::playAnnouncerCue"))
        assertTrue(source.contains("announcerCueResetter = battleAudio::resetAnnouncerCues"))
    }

    @Test
    fun lowMemoryPlaybackKeepsTheAnnouncerProtocolFallback() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val lightweight = source.substringAfter("private fun applyLightweightBattleProtocol").substringBefore("private fun playLightweightDamageCue")

        assertTrue(lightweight.contains("BattleAnnouncerCueResolver.cuesForProtocol("))
        assertTrue(lightweight.contains("directDamageTargetsByLine.keys"))
        assertTrue(lightweight.contains("battleAudio::playAnnouncerCue"))
        assertTrue(lightweight.contains("impactCueByLine"))
        assertTrue(lightweight.contains("latestDamageLineIndex"))
        assertTrue(source.contains("setLightweightImpactSoundListener"))
        assertTrue(source.contains("battleAudio.playBattleCue(BattleAudioCue.GENERIC_DAMAGE)"))
    }

    @Test
    fun lowMemoryPlaybackKeepsEffectivenessCuesAcrossSplitDamagePackets() {
        val activitySource = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val sceneSource = File("src/main/kotlin/dev/adrian/showdown/BattleSceneView.kt").readText()

        assertTrue(activitySource.contains("lateListener = battleAudio::playBattleCue"))
        assertTrue(sceneSource.contains("lightweightLateImpactSoundCue"))
        assertTrue(sceneSource.contains("lightweightLateImpactSoundListener"))
        assertTrue(sceneSource.contains("\"-supereffective\", \"-resisted\""))
        assertTrue(sceneSource.contains("if (lightweightImpactSoundPending)"))
        assertTrue(sceneSource.contains("lightweightLateImpactSoundListener?.invoke(cue)"))
    }

    @Test
    fun releasesTheAnimationWebViewAfterFinishedPlaybackDrains() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val playback = source.substringAfter("private val playbackAdvanceRunnable").substringBefore("private var shouldMaintainConnection")

        assertTrue(playback.contains("pendingBattlePackets.isEmpty()"))
        assertTrue(playback.contains("session.isBattleFinished()"))
        assertTrue(playback.contains("releaseShowdownMoveEffects()"))
        assertTrue(source.contains("primaryFrame?.removeView(effects)"))
        assertTrue(source.contains("effects.release()"))
    }

    @Test
    fun controllerInputIsConsumedByTheTopCustomDialog() {
        val activitySource = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val dialogSource = File("src/main/kotlin/dev/adrian/showdown/ShowdownDialog.kt").readText()

        assertTrue(activitySource.contains("ShowdownDialog.dispatchControllerKey(this, keyCode)"))
        assertTrue(activitySource.contains("ShowdownDialog.dispatchControllerKey(this@MainActivity, event.keyCode)"))
        assertTrue(dialogSource.contains("override fun dispatchKeyEvent(event: KeyEvent): Boolean"))
        assertTrue(dialogSource.contains("override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean"))
        assertTrue(dialogSource.contains("private fun handleControllerKey(keyCode: Int): Boolean"))
        assertTrue(dialogSource.contains("buttonViews[it]?.performClick()"))
        assertTrue(dialogSource.contains("fun dispatchControllerMotion(hostContext: Context, horizontal: Int, vertical: Int): Boolean"))
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
        assertTrue(source.contains("moveDex.loadMoveInfo(::bindMoveDexResolvers)"))
        assertTrue(listener.contains("ensureMoveDexLoaded()"))
        assertFalse(teamEditor.contains("moveDex.load"))
        assertTrue(source.contains("private fun ensureTeamEditorSuggestions(editor: TeamSetEditor)"))
        assertTrue(source.contains("field.setOnFocusChangeListener"))
        assertTrue(source.contains("if (details.visibility == View.VISIBLE) ensureTeamEditorSuggestions(editor)"))
    }

    @Test
    fun teamAndLadderFormatPickersUseTheSearchableCustomSurface() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val ladderPicker = source.substringAfter("private fun showLadderFormatPicker()").substringBefore("private fun showPrivateMessageComposer")
        val teamPicker = source.substringAfter("private fun showTeamFormatPicker(target: EditText)").substringBefore("private data class TeamStatEditor")
        val teamSelectionPicker = source.substringAfter("private fun showTeamPicker(").substringBefore("private fun cancelActiveSearch")

        assertTrue(ladderPicker.contains("setSearchableSingleChoiceItems"))
        assertTrue(ladderPicker.contains("ShowdownFormatSearch::searchText"))
        assertTrue(teamPicker.contains("setSearchableSingleChoiceItems"))
        assertTrue(teamPicker.contains("ShowdownFormatSearch::searchText"))
        assertTrue(teamSelectionPicker.contains("setSearchableSingleChoiceItems"))
        assertTrue(teamSelectionPicker.contains("ShowdownTeamLibraryQuery::searchText"))
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
        assertTrue(source.contains("session.setBattleParticipant(true)"))
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
    fun roomCatalogsUseSearchableCustomSurfaces() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val battlePicker = source.substringAfter("private fun showBattleRoomPicker()").substringBefore("private fun showRoomList()")
        val roomList = source.substringAfter("private fun showRoomList()").substringBefore("private fun renderRoomListDialog()")
        val roomRenderer = source.substringAfter("private fun renderRoomListDialog()").substringBefore("private fun showTournamentDirectory()")

        assertTrue(battlePicker.contains("setSearchableSingleChoiceItems"))
        assertTrue(battlePicker.contains("Search live battles or players"))
        assertTrue(roomList.contains("roomListSearchQuery = \"\""))
        assertTrue(roomRenderer.contains("Search rooms, players, or formats"))
        assertTrue(roomRenderer.contains("ShowdownRoomQuery.matches"))
        assertTrue(roomRenderer.contains("roomScrollHeight = (dialogViewport * 0.36f).toInt()"))
    }

    @Test
    fun tournamentDirectoryUsesAFilterableCustomSurface() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val directory = source.substringAfter("private fun showTournamentDirectory()").substringBefore("private fun requestTournamentDirectory()")
        val renderer = source.substringAfter("private fun updateTournamentDirectoryDialog()").substringBefore("private fun styleDynamicDialogButton")

        assertTrue(directory.contains("Search tournaments, formats, or status"))
        assertTrue(directory.contains("tournamentDirectorySearchQuery"))
        assertTrue(renderer.contains("ShowdownTournamentQuery.matches"))
        assertTrue(renderer.contains("No matching tournaments"))
    }

    @Test
    fun ladderEntriesUseAFilterableCustomSurface() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val ladder = source.substringAfter("private fun renderLadderDialog()").substringBefore("private fun requestLadder")

        assertTrue(ladder.contains("Search players or rank"))
        assertTrue(ladder.contains("setSearchableSingleChoiceItems"))
        assertTrue(ladder.contains("ShowdownLadderQuery.searchText"))
    }

    @Test
    fun ladderDirectorySupportsGuestWebApiFallback() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val dialog = source.substringAfter("private fun showLadderDialog").substringBefore("private fun renderLadderDialog")
        val request = source.substringAfter("private fun requestLadder").substringBefore("private fun showLadderFormatPicker")

        assertFalse(dialog.contains("Sign in to view the ladder"))
        assertTrue(request.contains("ladderFetcher.fetch"))
        assertTrue(request.contains("/cmd laddertop"))
    }

    @Test
    fun playerLookupSupportsGuestWebApiProfiles() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val composer = source.substringAfter("private fun showFindUserComposer()").substringBefore("private fun requestUserDetails")
        val request = source.substringAfter("private fun requestUserDetails").substringBefore("private fun renderUserDetails")

        assertFalse(composer.contains("Sign in to look up another player"))
        assertTrue(request.contains("userFetcher.fetch"))
        assertTrue(request.contains("ShowdownUserDetails.queryCommand"))
    }

    @Test
    fun ladderPlayersOpenTheirPublicProfile() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val ladder = source.substringAfter("private fun renderLadderDialog()").substringBefore("private fun requestLadder")

        assertTrue(ladder.contains("entries.getOrNull(selected)"))
        assertTrue(ladder.contains("requestUserDetails"))
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

    @Test
    fun exposesReplaySearchFromResourcesAndBattleActions() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("text = \"Search replays\""))
        assertTrue(source.contains("showReplaySearchDialog()"))
        assertTrue(source.contains("ShowdownReplaySearchQuery("))
        assertTrue(source.contains("replayFetcher.search(normalized)"))
        assertTrue(source.contains("entry.format.trim().ifBlank"))
        assertTrue(source.contains("https://replay.pokemonshowdown.com/\${entry.id}"))
    }
}
