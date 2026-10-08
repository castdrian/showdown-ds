package dev.adrian.showdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import dev.adrian.showdown.R
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class BattleSceneView(
    context: Context,
    private val session: BattleSession,
    private val spriteCache: ShowdownSpriteCache
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val battleFeedTypeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    private val battleFeedBoldTypeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    private val source = Rect()
    private val destination = RectF()
    private val logo: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.showdown_logo)
    private var backdrop: Bitmap? = null
    private var playerSprite: ShowdownSpriteCache.SpriteAsset? = null
    private var opponentSprite: ShowdownSpriteCache.SpriteAsset? = null
    private val playerActiveSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val opponentActiveSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val requestedPlayerActiveSprites = mutableMapOf<String, BattleSpriteRequest>()
    private val requestedOpponentActiveSprites = mutableMapOf<String, BattleSpriteRequest>()
    private val previewSprites = mutableMapOf<Int, ShowdownSpriteCache.SpriteAsset?>()
    private val requestedPreviewSprites = mutableMapOf<Int, BattleSpriteRequest>()
    private var requestedPlayerSprite: BattleSpriteRequest? = null
    private var requestedOpponentSprite: BattleSpriteRequest? = null
    private val itemSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val requestedItemSprites = mutableSetOf<String>()
    private val ladderBadgeSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val requestedLadderBadgeSprites = mutableSetOf<String>()
    private var requestedBackdrop = ""
    private var requestedSwitchOutVisual: BattleSession.SwitchOutVisual? = null
    private var resourcesRequested = false
    private val spriteRequestTracker = BattleSpriteRequestTracker()
    private val effectAssets = mutableMapOf<String, Bitmap>()
    private val requestedEffects = mutableSetOf<String>()
    private val partyBallBitmaps = mutableMapOf<PartyBallBitmapKey, Bitmap>()
    private val partyBallPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var inspectedPlayer: Boolean? = null
    private var inspectedSlot: String? = null
    private var opponentPreviewPageIndex = 0
    private var opponentPreviewRosterKey = ""
    private val playerInspectBounds = RectF()
    private val opponentInspectBounds = RectF()
    private val battleFeedBounds = RectF()
    private val fieldEffectBounds = RectF()
    private val battleFeedPresentation = BattleFeedPresentation()
    private var displayedBattleSceneSnapshot: BattleSession.BattleSceneSnapshot? = null
    private var displayedSwitchOutVisual: BattleSession.SwitchOutVisual? = null
    private var cachedBattleFeedText: String? = null
    private var cachedBattleFeedVisibleText: String? = null
    private var cachedBattleFeedMarkup: String? = null
    private var cachedBattleFeedWidth = -1f
    private var cachedBattleFeedTextSize = -1f
    private var cachedBattleFeedFullLines = emptyList<String>()
    private var cachedBattleFeedLines = emptyList<List<BattleFeedText.StyledRun>>()
    private var battleFeedTouchDownY = 0f
    private var battleFeedTouchLastY = 0f
    private var battleFeedTouchActive = false
    private var battleFeedTouchMoved = false
    private var teamPreviewTouchDownX = 0f
    private var teamPreviewTouchActive = false
    private var animationsPaused = false
    private var playbackSpeed = 1f
    private var lightweightMoveStartedAtNanos = 0L
    private var lightweightMoveAnimationEnabled = true
    private var lightweightMoveActorPlayer = true
    private var lightweightMoveActorSlot: String? = null
    private var lightweightMoveTargetPlayer: Boolean? = null
    private var lightweightMoveTargetSlot: String? = null
    private var lightweightImpactAtNanos = 0L
    private var lightweightImpactTargets = emptyList<String>()
    private var lightweightMoveName = ""
    private var lightweightMoveType = "NORMAL"
    private var lightweightMoveCategory = "PHYSICAL"
    private var lightweightMoveStyle = BattleMoveVisualStyle.TYPE_BURST
    private var lightweightStatEffectAtNanos = 0L
    private var lightweightStatDirection = 0
    private var lightweightImpactSoundPending = false
    private var lightweightImpactSoundCue: BattleAudioCue? = null
    private var lightweightImpactSoundListener: ((BattleAudioCue?) -> Unit)? = null
    private var lightweightLateImpactSoundCue: BattleAudioCue? = null
    private var lightweightLateImpactSoundListener: ((BattleAudioCue) -> Unit)? = null
    private var lightweightPausedAtNanos = 0L
    private var accessibilityNodeProvider: CanvasAccessibilityNodeProvider? = null

    private data class InspectTarget(val player: Boolean, val slot: String?)

    private data class HpColors(val fill: Int, val highlight: Int, val shadow: Int)

    private data class PartyBallBitmapKey(val size: Int, val state: PartyBallState)

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = accessibilityProvider()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.contentDescription = accessibilityDescription()
        info.isFocusable = true
        info.isClickable = false
        accessibilityProvider().addVirtualChildren(info)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = BattlePlaybackSpeed.coerce(speed)
        battleFeedPresentation.setPlaybackSpeed(speed)
        invalidate()
    }

    fun remainingBattleFeedPlaybackBudgetMillis(nowMillis: Long = SystemClock.elapsedRealtime()): Long {
        battleFeedPresentation.updateMessages(
            session.battleFeedMessages(),
            session.battleFeedVisible,
            nowMillis,
            session.battleResult()
        )
        return battleFeedPresentation.remainingPlaybackBudgetMillis(nowMillis)
    }

    fun setPlaybackPaused(paused: Boolean) {
        if (animationsPaused == paused) return
        if (paused) {
            lightweightPausedAtNanos = System.nanoTime()
        } else if (lightweightPausedAtNanos > 0L) {
            val pausedDuration = (System.nanoTime() - lightweightPausedAtNanos).coerceAtLeast(0L)
            lightweightMoveStartedAtNanos = shiftTimestamp(lightweightMoveStartedAtNanos, pausedDuration)
            lightweightImpactAtNanos = shiftTimestamp(lightweightImpactAtNanos, pausedDuration)
            lightweightStatEffectAtNanos = shiftTimestamp(lightweightStatEffectAtNanos, pausedDuration)
            lightweightPausedAtNanos = 0L
        }
        animationsPaused = paused
        battleFeedPresentation.setPlaybackPaused(paused, SystemClock.elapsedRealtime())
        if (paused) stopRetainedAnimations()
        invalidate()
    }

    fun stopRetainedAnimations() {
        playerSprite?.stopAnimation()
        opponentSprite?.stopAnimation()
        playerActiveSprites.values.forEach { it?.stopAnimation() }
        opponentActiveSprites.values.forEach { it?.stopAnimation() }
        itemSprites.values.forEach { it?.stopAnimation() }
        ladderBadgeSprites.values.forEach { it?.stopAnimation() }
    }

    fun refreshResourceRequests() {
        if (!session.isLiveBattleActive() && !session.isBattleFinished()) {
            releaseRetainedResources()
        }
        resourcesRequested = false
        spriteRequestTracker.reset()
        invalidate()
    }

    fun releaseRetainedResources() {
        stopRetainedAnimations()
        playerSprite = null
        opponentSprite = null
        requestedPlayerSprite = null
        requestedOpponentSprite = null
        playerActiveSprites.clear()
        opponentActiveSprites.clear()
        requestedPlayerActiveSprites.clear()
        requestedOpponentActiveSprites.clear()
        previewSprites.values.forEach { it?.stopAnimation() }
        previewSprites.clear()
        requestedPreviewSprites.clear()
        itemSprites.clear()
        requestedItemSprites.clear()
        ladderBadgeSprites.clear()
        requestedLadderBadgeSprites.clear()
        effectAssets.clear()
        requestedEffects.clear()
        partyBallBitmaps.clear()
        backdrop = null
        requestedBackdrop = ""
        resourcesRequested = false
        spriteRequestTracker.reset()
        spriteCache.clearMemory()
    }

    fun resetBattleFeed() {
        battleFeedPresentation.reset()
        battleFeedBounds.setEmpty()
        lightweightMoveStartedAtNanos = 0L
        lightweightMoveAnimationEnabled = true
        lightweightMoveActorPlayer = true
        lightweightMoveActorSlot = null
        lightweightMoveTargetPlayer = null
        lightweightMoveTargetSlot = null
        lightweightImpactAtNanos = 0L
        lightweightImpactTargets = emptyList()
        lightweightMoveName = ""
        lightweightMoveType = "NORMAL"
        lightweightMoveCategory = "PHYSICAL"
        lightweightMoveStyle = BattleMoveVisualStyle.TYPE_BURST
        lightweightStatEffectAtNanos = 0L
        lightweightStatDirection = 0
        lightweightImpactSoundPending = false
        lightweightImpactSoundCue = null
        lightweightLateImpactSoundCue = null
        lightweightPausedAtNanos = 0L
        invalidate()
    }

    fun setLightweightImpactSoundListener(
        listener: ((BattleAudioCue?) -> Unit)?,
        lateListener: ((BattleAudioCue) -> Unit)? = null
    ) {
        lightweightImpactSoundListener = listener
        lightweightLateImpactSoundListener = lateListener
    }

    fun applyLightweightBattleProtocol(
        lines: List<String>,
        directDamageTargetsByLine: Map<Int, Set<String>> = emptyMap(),
        impactCueByLine: Map<Int, BattleAudioCue?> = emptyMap()
    ) {
        val nowNanos = System.nanoTime()
        var changed = false
        lines.forEachIndexed { lineIndex, line ->
            val fields = line.split('|')
            when (fields.getOrNull(1)) {
                "move" -> {
                    val actor = fields.getOrNull(2).orEmpty()
                    val moveArguments = fields.drop(5)
                    lightweightMoveAnimationEnabled = ShowdownBattleMovePresentation.shouldAnimate(moveArguments)
                    lightweightMoveStartedAtNanos = if (lightweightMoveAnimationEnabled) nowNanos else 0L
                    lightweightMoveActorPlayer = session.isLocalBattleSide(actor)
                    lightweightMoveActorSlot = protocolSlot(actor)
                    val target = fields.getOrNull(4)
                    lightweightMoveTargetPlayer = target?.takeIf(String::isNotBlank)?.let(session::isLocalBattleSide)
                    lightweightMoveTargetSlot = protocolSlot(target)
                    lightweightImpactAtNanos = 0L
                    lightweightImpactTargets = emptyList()
                    lightweightImpactSoundPending = false
                    lightweightImpactSoundCue = null
                    lightweightLateImpactSoundCue = null
                    lightweightMoveName = fields.getOrNull(3).orEmpty()
                    val animationMoveName = ShowdownBattleMovePresentation.animationName(moveArguments, lightweightMoveName)
                    val originalMoveInfo = session.moveInfoFor(lightweightMoveName)
                    val originalMoveType = session.moveTypeFor(lightweightMoveName)?.uppercase() ?: inferMoveType(lightweightMoveName)
                    val originalMoveCategory = originalMoveInfo?.category?.uppercase()
                        ?: inferMoveCategory(lightweightMoveName)
                    val animationMoveInfo = session.moveInfoFor(animationMoveName) ?: originalMoveInfo
                    lightweightMoveType = session.moveTypeFor(animationMoveName)?.uppercase() ?: originalMoveType
                    lightweightMoveCategory = animationMoveInfo?.category?.uppercase()
                        ?: originalMoveCategory
                    lightweightMoveStyle = if (lightweightMoveCategory == "STATUS") {
                        BattleMoveVisualStyle.STATUS
                    } else {
                        BattleMoveVisualStyleResolver.resolve(animationMoveInfo, lightweightMoveType)
                    }
                    lightweightStatEffectAtNanos = 0L
                    lightweightStatDirection = 0
                    changed = true
                }
                "-anim" -> {
                    val animation = ShowdownBattleMovePresentation.protocolAnimation(fields) ?: return@forEachIndexed
                    val localActor = session.isLocalBattleSide(animation.actor)
                    val actorSlot = protocolSlot(animation.actor)
                    lightweightMoveStartedAtNanos = if (animation.shouldAnimate) nowNanos else 0L
                    lightweightMoveActorPlayer = localActor
                    lightweightMoveActorSlot = actorSlot
                    lightweightMoveTargetPlayer = animation.target?.let(session::isLocalBattleSide)
                    lightweightMoveTargetSlot = protocolSlot(animation.target)
                    lightweightImpactAtNanos = 0L
                    lightweightImpactTargets = emptyList()
                    lightweightImpactSoundPending = false
                    lightweightImpactSoundCue = null
                    lightweightLateImpactSoundCue = null
                    lightweightMoveAnimationEnabled = animation.shouldAnimate
                    lightweightMoveName = animation.moveName
                    lightweightMoveType = session.moveTypeFor(animation.moveName)?.uppercase() ?: inferMoveType(animation.moveName)
                    val moveInfo = session.moveInfoFor(animation.moveName)
                    lightweightMoveCategory = moveInfo?.category?.uppercase()
                        ?: inferMoveCategory(animation.moveName)
                    lightweightMoveStyle = if (lightweightMoveCategory == "STATUS") {
                        BattleMoveVisualStyle.STATUS
                    } else {
                        BattleMoveVisualStyleResolver.resolve(moveInfo, lightweightMoveType)
                    }
                    changed = true
                }
                "-damage", "-sethp" -> {
                    val directTargets = directDamageTargetsByLine[lineIndex].orEmpty()
                    val target = BattleDamageCueResolver.healthUpdates(fields)
                        .firstOrNull { update ->
                            directTargets.any { target ->
                                BattleDamageCueResolver.targetKey(target) == BattleDamageCueResolver.targetKey(update.target)
                            }
                        }
                        ?.target
                    if (target != null) {
                        lightweightLateImpactSoundCue = null
                        lightweightImpactTargets = if (lightweightImpactSoundPending && lightweightImpactAtNanos > nowNanos) {
                            (lightweightImpactTargets + directTargets).distinctBy(BattleDamageCueResolver::targetKey)
                        } else {
                            directTargets.toList()
                        }
                        lightweightImpactAtNanos = nowNanos + BattleSceneTiming.lightweightImpactDelayForAnimation(
                            lightweightMoveAnimationEnabled,
                            playbackSpeed
                        )
                        lightweightImpactSoundPending = true
                        lightweightImpactSoundCue = impactCueByLine[lineIndex]
                        changed = true
                    }
                }
                "-supereffective", "-resisted" -> {
                    val cue = BattleAudioCueResolver.cueForProtocolLine(line) ?: return@forEachIndexed
                    if (lightweightImpactSoundPending) {
                        lightweightImpactSoundCue = cue
                    } else {
                        lightweightLateImpactSoundCue = cue
                    }
                    changed = true
                }
                "-boost", "-unboost", "-setboost" -> {
                    lightweightMoveCategory = "STATUS"
                    lightweightStatEffectAtNanos = nowNanos
                    lightweightStatDirection = when (fields.getOrNull(1)) {
                        "-unboost" -> -1
                        "-setboost" -> fields.getOrNull(4)?.toIntOrNull()?.signum() ?: 1
                        else -> 1
                    }
                    changed = true
                }
                "-status", "-curestatus", "-heal", "-fail", "-block", "-immune", "-miss", "-nothing" -> {
                    if (lightweightMoveStartedAtNanos > 0L) {
                        lightweightMoveCategory = "STATUS"
                        lightweightImpactAtNanos = nowNanos
                        lightweightImpactTargets = fields.getOrNull(2)?.let { listOf(it) }.orEmpty()
                        changed = true
                    }
                }
            }
        }
        if (changed) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val scale = min(width / 1920f, height / 1080f)
        val teamPreview = session.battlePhase == BattleSession.BattlePhase.TEAM_PREVIEW
        val publicTeamPreview = shouldShowPublicTeamPreview()
        val singles = session.isSinglesBattle()
        val playerX = if (singles) ShowdownBattleLayout.x(width, ShowdownBattleLayout.PLAYER_X) else width * 0.30f
        val playerY = if (singles) ShowdownBattleLayout.y(height, ShowdownBattleLayout.PLAYER_Y) else height * 0.67f
        val opponentX = if (singles) ShowdownBattleLayout.x(width, ShowdownBattleLayout.OPPONENT_X) else width * 0.73f
        val opponentY = if (singles) ShowdownBattleLayout.y(height, ShowdownBattleLayout.OPPONENT_Y) else height * 0.42f
        val nowNanos = System.nanoTime()
        if (session.isReplayMode() && !session.hasBattleProtocolTranscript()) {
            displayedBattleSceneSnapshot = null
            displayedSwitchOutVisual = null
            battleFeedPresentation.update(emptyList(), false, SystemClock.elapsedRealtime())
            drawLobby(canvas, width, height, scale)
            accessibilityNodeProvider?.refreshIfChanged()
            return
        }
        if (!session.isLiveBattleActive() && !session.isBattleFinished() && !teamPreview && !publicTeamPreview) {
            displayedBattleSceneSnapshot = null
            displayedSwitchOutVisual = null
            battleFeedPresentation.update(emptyList(), false, SystemClock.elapsedRealtime())
            drawLobby(canvas, width, height, scale)
            accessibilityNodeProvider?.refreshIfChanged()
            return
        }
        if (teamPreview || publicTeamPreview) {
            displayedBattleSceneSnapshot = null
            displayedSwitchOutVisual = null
            battleFeedPresentation.update(emptyList(), false, SystemClock.elapsedRealtime())
            battleFeedBounds.setEmpty()
            requestTeamPreviewSprites()
            ensureResourcesRequested(session.playerActiveCombatants(), session.opponentActiveCombatants())
            drawBackdrop(canvas, width, height)
            drawTeamPreview(canvas, width, height, scale)
            if (!animationsPaused && previewSprites.values.any { it?.isAnimated == true }) {
                postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
            }
            accessibilityNodeProvider?.refreshIfChanged()
            return
        }
        val battleFeedTime = SystemClock.elapsedRealtime()
        battleFeedPresentation.updateMessages(
            session.battleFeedMessages(),
            session.battleFeedVisible,
            battleFeedTime,
            session.battleResult()
        )
        val battleFeedFrame = battleFeedPresentation.frame(battleFeedTime)
        val sceneContext = battleFeedFrame?.sceneContext
        val sceneSnapshot = sceneContext?.snapshot
        displayedBattleSceneSnapshot = sceneSnapshot
        val switchOutVisual = sceneContext?.switchOutVisual
        displayedSwitchOutVisual = switchOutVisual
        if (requestedSwitchOutVisual != switchOutVisual) {
            requestedSwitchOutVisual = switchOutVisual
            resourcesRequested = false
        }
        val playerCombatants = BattleFeedSceneState.combatantsForMessage(
            sceneSnapshot?.playerCombatants ?: session.playerActiveCombatants(),
            true,
            switchOutVisual
        )
        val opponentCombatants = BattleFeedSceneState.combatantsForMessage(
            sceneSnapshot?.opponentCombatants ?: session.opponentActiveCombatants(),
            false,
            switchOutVisual
        )
        requestTeamPreviewSprites()
        ensureResourcesRequested(playerCombatants, opponentCombatants)
        val playerCombatant = playerCombatants.firstOrNull()
        val opponentCombatant = opponentCombatants.firstOrNull()
        val playerDetails = BattleFeedSceneState.detailsForMessage(
            sceneSnapshot?.playerDetails ?: session.playerDetails(),
            true,
            switchOutVisual
        )
        val opponentDetails = BattleFeedSceneState.detailsForMessage(
            sceneSnapshot?.opponentDetails ?: session.opponentDetails(),
            false,
            switchOutVisual
        )
        val playerPartyDetails = sceneSnapshot?.playerPartyDetails ?: session.playerPartyDetails()
        val opponentPartyDetails = sceneSnapshot?.opponentPartyDetails ?: session.opponentPartyDetails()
        val currentBattleInfo = session.battleInfo()
        val battleInfo = sceneSnapshot?.battleInfo?.copy(
            ladderBadgesBySide = currentBattleInfo.ladderBadgesBySide
        ) ?: currentBattleInfo
        requestLadderBadgeSprites(battleInfo.ladderBadgesBySide)
        val fieldVisuals = BattleFieldVisualComposer.compose(battleInfo)
        val playerStatusAlpha = statusCardAlpha(
            playerCombatant?.name ?: session.playerPokemon,
            playerCombatant?.condition ?: session.playerCondition,
            nowNanos
        ) * BattleSceneTiming.summonStatusCardAlpha(
            playerCombatant?.entryAtNanos ?: session.playerEntryAtNanos,
            nowNanos
        )
        val opponentStatusAlpha = statusCardAlpha(
            opponentCombatant?.name ?: session.opponentPokemon,
            opponentCombatant?.condition ?: session.opponentCondition,
            nowNanos
        ) * BattleSceneTiming.summonStatusCardAlpha(
            opponentCombatant?.entryAtNanos ?: session.opponentEntryAtNanos,
            nowNanos
        )
        val playerStatusCardVisible = playerStatusAlpha > 0f &&
            BattleFeedSceneState.hasKnownPokemon(
                playerCombatant,
                playerDetails,
                requireActiveCombatant = session.isReplayMode() || session.isSpectatorMode()
            )
        val opponentStatusCardVisible = opponentStatusAlpha > 0f &&
            BattleFeedSceneState.hasKnownPokemon(
                opponentCombatant,
                opponentDetails,
                requireActiveCombatant = session.isReplayMode() || session.isSpectatorMode()
            )
        playerInspectBounds.set(width * 0.05f, height * 0.28f, width * 0.57f, height * 0.88f)
        opponentInspectBounds.set(width * 0.47f, height * 0.11f, width * 0.95f, height * 0.67f)
        drawBackdrop(canvas, width, height)
        drawFieldVisuals(canvas, width, height, scale, nowNanos, fieldVisuals)
        if (!singles && opponentCombatants.isNotEmpty()) {
            val centeredSlot = BattleCombatantLayout.centeredSlot(session.isTriplesCentered(), opponentCombatants)
            fieldCombatants(opponentCombatants, false).forEachIndexed { index, combatant ->
                drawCombatant(
                    canvas,
                    BattleCombatantLayout.x(width, false, index, opponentCombatants.size, centeredSlot, combatant.slot),
                    opponentY,
                    scale * 0.92f,
                    combatant.slot,
                    combatant.condition,
                    combatant.entryAtNanos,
                    nowNanos,
                    opponentActiveSprites[combatant.slot]
                )
            }
        } else {
            drawCombatant(
                canvas,
                opponentX,
                opponentY,
                scale * if (singles) ShowdownBattleLayout.OPPONENT_SCALE else 1.05f,
                opponentCombatant?.slot ?: primaryBattleSlot(player = false),
                opponentCombatant?.condition ?: session.opponentCondition,
                opponentCombatant?.entryAtNanos ?: session.opponentEntryAtNanos,
                nowNanos,
                opponentSprite,
                showdownPlacement = singles
            )
        }
        if (!singles && playerCombatants.isNotEmpty()) {
            val centeredSlot = BattleCombatantLayout.centeredSlot(session.isTriplesCentered(), playerCombatants)
            fieldCombatants(playerCombatants, true).forEachIndexed { index, combatant ->
                drawCombatant(
                    canvas,
                    BattleCombatantLayout.x(width, true, index, playerCombatants.size, centeredSlot, combatant.slot),
                    playerY,
                    scale * 1.02f,
                    combatant.slot,
                    combatant.condition,
                    combatant.entryAtNanos,
                    nowNanos,
                    playerActiveSprites[combatant.slot]
                )
            }
        } else {
            drawCombatant(
                canvas,
                playerX,
                playerY,
                scale * if (singles) ShowdownBattleLayout.PLAYER_SCALE else 1.16f,
                playerCombatant?.slot ?: primaryBattleSlot(player = true),
                playerCombatant?.condition ?: session.playerCondition,
                playerCombatant?.entryAtNanos ?: session.playerEntryAtNanos,
                nowNanos,
                playerSprite,
                showdownPlacement = singles
            )
        }
        if (!animationsPaused && lightweightImpactSoundPending && lightweightImpactAtNanos > 0L && nowNanos >= lightweightImpactAtNanos) {
            lightweightImpactSoundPending = false
            lightweightImpactSoundListener?.invoke(lightweightImpactSoundCue)
            lightweightImpactSoundCue = null
        }
        if (!animationsPaused && !lightweightImpactSoundPending) {
            lightweightLateImpactSoundCue?.let { cue ->
                lightweightLateImpactSoundCue = null
                lightweightLateImpactSoundListener?.invoke(cue)
            }
        }
        drawLightweightMoveEffect(canvas, width, height, scale, nowNanos)
        drawHeader(canvas, width, scale)
        drawBattleClock(canvas, width, scale)
        if ((inspectedPlayer == true && !session.hasActivePlayerCombatant()) ||
            (inspectedPlayer == false && !session.hasActiveOpponentCombatant())
        ) {
            inspectedPlayer = null
            inspectedSlot = null
        }
        if (inspectedPlayer != null && inspectedSlot != null) {
            val combatants = if (inspectedPlayer == true) playerCombatants else opponentCombatants
            if (combatants.none { it.slot == inspectedSlot }) {
                inspectedPlayer = null
                inspectedSlot = null
            }
        }
        if (inspectedPlayer == null) {
            if (singles) {
                if (playerStatusCardVisible) {
                    drawStatusCard(
                        canvas,
                        RectF(
                            width * ShowdownBattleLayout.SINGLE_CARD_LEFT_FRACTION,
                            height * 0.80f,
                            ShowdownBattleLayout.singlePlayerCardRight(width, scale),
                            height * 0.98f
                        ),
                        playerDetails,
                        playerCombatant?.hp ?: session.playerHp,
                        scale,
                        playerStatusAlpha,
                        playerPartyDetails,
                        battleInfo.ladderBadgesBySide[statusCardSide(true, playerCombatant)].orEmpty()
                    )
                }
            } else {
                drawActiveStatusCards(
                    canvas,
                    width,
                    height,
                    scale,
                    true,
                    fieldCombatants(playerCombatants, true),
                    switchOutVisual,
                    sceneSnapshot?.playerDetailsBySlot.orEmpty(),
                    playerPartyDetails,
                    ladderBadgesBySide = battleInfo.ladderBadgesBySide
                )
            }
            if (singles) {
                if (opponentStatusCardVisible) {
                    drawStatusCard(
                        canvas,
                        RectF(
                            ShowdownBattleLayout.singleOpponentCardLeft(width, scale),
                            height * 0.02f,
                            width * ShowdownBattleLayout.SINGLE_CARD_RIGHT_FRACTION,
                            height * 0.20f
                        ),
                        opponentDetails,
                        opponentCombatant?.hp ?: session.opponentHp,
                        scale,
                        opponentStatusAlpha,
                        opponentPartyDetails,
                        battleInfo.ladderBadgesBySide[statusCardSide(false, opponentCombatant)].orEmpty()
                    )
                }
            } else {
                drawActiveStatusCards(
                    canvas,
                    width,
                    height,
                    scale,
                    false,
                    fieldCombatants(opponentCombatants, false),
                    switchOutVisual,
                    sceneSnapshot?.opponentDetailsBySlot.orEmpty(),
                    opponentPartyDetails,
                    sceneSnapshot?.opponentPartyDetailsBySlot ?: session.opponentPartyDetailsBySlot(),
                    battleInfo.ladderBadgesBySide
                )
            }
            drawBattleFeed(canvas, width, height, scale, battleFeedFrame)
        }
        drawInspectSheet(canvas, width, height, scale)
        if (
            (
            playerCombatants.any { isFainting(it.slot, it.condition) } ||
            opponentCombatants.any { isFainting(it.slot, it.condition) } ||
            BattleSceneTiming.summonProgress(session.playerEntryAtNanos, nowNanos) < 1f ||
            BattleSceneTiming.summonProgress(session.opponentEntryAtNanos, nowNanos) < 1f ||
            playerSprite?.isAnimated == true ||
            opponentSprite?.isAnimated == true ||
            playerCombatants.any { playerActiveSprites[it.slot]?.isAnimated == true } ||
            opponentCombatants.any { opponentActiveSprites[it.slot]?.isAnimated == true } ||
            fieldVisuals.hasActiveVisuals ||
            lightweightMoveEffectActive(nowNanos)
            ) && !animationsPaused
        ) {
            postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
        }
        if (session.battleClockSeconds() != null && !animationsPaused) postInvalidateDelayed(1_000L)
        accessibilityNodeProvider?.refreshIfChanged()
    }

    private fun accessibilityProvider() = accessibilityNodeProvider ?: CanvasAccessibilityNodeProvider(
        this,
        ::accessibilityDescription,
        ::accessibilityNodes
    ).also { accessibilityNodeProvider = it }

    private fun displayedPlayerCombatants() = BattleFeedSceneState.combatantsForMessage(
        displayedBattleSceneSnapshot?.playerCombatants ?: session.playerActiveCombatants(),
        true,
        displayedSwitchOutVisual
    )

    private fun displayedOpponentCombatants() = BattleFeedSceneState.combatantsForMessage(
        displayedBattleSceneSnapshot?.opponentCombatants ?: session.opponentActiveCombatants(),
        false,
        displayedSwitchOutVisual
    )

    private fun displayedPlayerDetails() = BattleFeedSceneState.detailsForMessage(
        displayedBattleSceneSnapshot?.playerDetails ?: session.playerDetails(),
        true,
        displayedSwitchOutVisual
    )

    private fun displayedOpponentDetails() = BattleFeedSceneState.detailsForMessage(
        displayedBattleSceneSnapshot?.opponentDetails ?: session.opponentDetails(),
        false,
        displayedSwitchOutVisual
    )

    private fun displayedDetailsForActiveCombatant(playerSide: Boolean, slot: String): BattleSession.PokemonDetails? {
        displayedSwitchOutVisual?.takeIf { it.playerSide == playerSide && it.combatant.slot == slot }?.let {
            return it.details
        }
        val snapshot = displayedBattleSceneSnapshot
        val detailsBySlot = if (playerSide) snapshot?.playerDetailsBySlot else snapshot?.opponentDetailsBySlot
        return detailsBySlot?.get(slot) ?: session.detailsForActiveCombatant(playerSide, slot)
    }

    private fun accessibilityDescription(): String {
        if (session.isReplayMode() && !session.hasBattleProtocolTranscript()) return "Showdown replay. Loading battle."
        if (!session.isLiveBattleActive() && !session.isBattleFinished() && session.battlePhase != BattleSession.BattlePhase.TEAM_PREVIEW && !shouldShowPublicTeamPreview()) {
            return "Showdown lobby. ${session.status}"
        }
        if (session.battlePhase == BattleSession.BattlePhase.TEAM_PREVIEW || shouldShowPublicTeamPreview()) {
            val party = session.opponentPartyDetails()
            val visibleIndices = opponentPreviewIndices(party)
            val pageCount = TeamRosterPager.pageCount(party.size)
            val pageSummary = if (pageCount > 1) " Page ${opponentPreviewPageIndex + 1} of $pageCount." else ""
            return "Pokémon team preview. Opponent team: ${party.size} Pokémon.$pageSummary ${visibleIndices.size} shown."
        }
        val playerDetails = displayedPlayerDetails()
        val opponentDetails = displayedOpponentDetails()
        val playerCombatant = displayedPlayerCombatants().firstOrNull()
        val opponentCombatant = displayedOpponentCombatants().firstOrNull()
        val requireActiveCombatant = session.isReplayMode() || session.isSpectatorMode()
        val playerKnown = BattleFeedSceneState.hasKnownPokemon(
            playerCombatant,
            playerDetails,
            requireActiveCombatant
        )
        val opponentKnown = BattleFeedSceneState.hasKnownPokemon(
            opponentCombatant,
            opponentDetails,
            requireActiveCombatant
        )
        val player = playerCombatant?.let {
            BattleSession.displayPokemonName(it.name, it.species)
        } ?: BattleSession.displayPokemonName(playerDetails.name, playerDetails.species)
        val opponent = opponentCombatant?.let {
            BattleSession.displayPokemonName(it.name, it.species)
        } ?: BattleSession.displayPokemonName(opponentDetails.name, opponentDetails.species)
        val matchup = when {
            playerKnown && opponentKnown -> "Battle. $player versus $opponent."
            playerKnown -> "Battle. Your active Pokémon is $player. The opponent has not sent out a Pokémon yet."
            opponentKnown -> "Battle. The opponent's active Pokémon is $opponent. Your Pokémon has not been sent out yet."
            else -> "Battle is waiting for Pokémon to enter."
        }
        val log = cachedBattleFeedVisibleText?.takeIf(String::isNotBlank)?.let { "Battle log: $it" }
        return listOfNotNull(matchup, session.status, log).joinToString(" ")
    }

    private fun accessibilityNodes(): List<CanvasAccessibilityNode> {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return emptyList()
        if (session.isReplayMode() && !session.hasBattleProtocolTranscript()) return emptyList()
        val teamPreview = session.battlePhase == BattleSession.BattlePhase.TEAM_PREVIEW || shouldShowPublicTeamPreview()
        if (!session.isLiveBattleActive() && !session.isBattleFinished() && !teamPreview) return emptyList()
        if (teamPreview) return opponentTeamPreviewAccessibilityNodes(width, height)
        if (inspectedPlayer != null) return inspectSheetAccessibilityNodes(width, height)

        val nodes = mutableListOf<CanvasAccessibilityNode>()
        val scale = min(width / 1920f, height / 1080f)
        if (session.isSinglesBattle()) {
            val player = displayedPlayerDetails()
            val opponent = displayedOpponentDetails()
            val playerCombatant = displayedPlayerCombatants().firstOrNull()
            val opponentCombatant = displayedOpponentCombatants().firstOrNull()
            val requireActiveCombatant = session.isReplayMode() || session.isSpectatorMode()
            if (BattleFeedSceneState.hasKnownPokemon(playerCombatant, player, requireActiveCombatant)) {
                addAccessibilityNode(
                    nodes,
                    ACCESSIBLE_PLAYER_ID,
                    withLadderBadgeAccessibility(
                        "Your active Pokémon, ${pokemonAccessibilitySummary(player)}",
                        ladderBadgesForSide(statusCardSide(true, playerCombatant))
                    ),
                    RectF(
                        width * ShowdownBattleLayout.SINGLE_CARD_LEFT_FRACTION,
                        height * 0.80f,
                        ShowdownBattleLayout.singlePlayerCardRight(width, scale),
                        height * 0.98f
                    ),
                    selected = inspectedPlayer == true
                ) {
                    selectInspectedPokemon(true, null)
                }
            }
            if (BattleFeedSceneState.hasKnownPokemon(opponentCombatant, opponent, requireActiveCombatant)) {
                addAccessibilityNode(
                    nodes,
                    ACCESSIBLE_OPPONENT_ID,
                    withLadderBadgeAccessibility(
                        "Opponent's active Pokémon, ${pokemonAccessibilitySummary(opponent)}",
                        ladderBadgesForSide(statusCardSide(false, opponentCombatant))
                    ),
                    RectF(
                        ShowdownBattleLayout.singleOpponentCardLeft(width, scale),
                        height * 0.02f,
                        width * ShowdownBattleLayout.SINGLE_CARD_RIGHT_FRACTION,
                        height * 0.20f
                    ),
                    selected = inspectedPlayer == false
                ) {
                    selectInspectedPokemon(false, null)
                }
            }
        } else {
            addMultiCombatantAccessibilityNodes(nodes, width, height, scale, true)
            addMultiCombatantAccessibilityNodes(nodes, width, height, scale, false)
        }
        if (!battleFeedBounds.isEmpty && !cachedBattleFeedVisibleText.isNullOrBlank()) {
            addAccessibilityNode(
                nodes,
                ACCESSIBLE_BATTLE_LOG_ID,
                "Battle log. $cachedBattleFeedVisibleText",
                RectF(battleFeedBounds),
                role = CanvasAccessibilityNode.Role.BUTTON
            ) {
                battleFeedPresentation.advanceOnTap(SystemClock.elapsedRealtime())
                invalidate()
                performClick()
            }
        }
        return nodes
    }

    private fun opponentTeamPreviewAccessibilityNodes(width: Float, height: Float): List<CanvasAccessibilityNode> {
        val party = session.opponentPartyDetails()
        val visibleIndices = opponentPreviewIndices(party)
        val pageCount = TeamRosterPager.pageCount(party.size)
        val nodes = mutableListOf<CanvasAccessibilityNode>()
        if (pageCount > 1) {
            val navigation = BattleTeamPreviewLayout.navigationSlots(width, height)
            navigation.forEachIndexed { index, slot ->
                val previous = index == 0
                val enabled = if (previous) opponentPreviewPageIndex > 0 else opponentPreviewPageIndex + 1 < pageCount
                nodes += CanvasAccessibilityNode(
                    if (previous) ACCESSIBLE_TEAM_PREVIEW_PREVIOUS_ID else ACCESSIBLE_TEAM_PREVIEW_NEXT_ID,
                    if (previous) "Previous opponent team page, ${opponentPreviewPageIndex + 1} of $pageCount" else "Next opponent team page, ${opponentPreviewPageIndex + 1} of $pageCount",
                    Rect().apply {
                        RectF(slot.left, slot.top, slot.right, slot.bottom).roundOut(this)
                    }.toCanvasAccessibilityBounds(),
                    enabled = enabled,
                    onClick = if (enabled) ({ changeOpponentPreviewPage(if (previous) -1 else 1) }) else null
                )
            }
        }
        val slots = BattleTeamPreviewLayout.slots(width, height, visibleIndices.size)
        visibleIndices.forEachIndexed { visibleIndex, teamIndex ->
            val details = party[teamIndex]
            val slot = slots[visibleIndex]
            val name = BattleSession.displayPokemonName(details.name, details.species)
            val pokemonSummary = BattleAccessibilityText.pokemon(
                name,
                details.level,
                details.gender,
                details.hp,
                details.condition
            )
            val typeSummary = details.types.takeIf { it.isNotEmpty() }?.joinToString()?.let { ", types $it" }.orEmpty()
            nodes += CanvasAccessibilityNode(
                ACCESSIBLE_TEAM_PREVIEW_BASE + teamIndex,
                "Opponent Pokémon, $pokemonSummary$typeSummary",
                Rect().apply {
                    RectF(slot.left, slot.top, slot.right, slot.bottom).roundOut(this)
                }.toCanvasAccessibilityBounds(),
                role = CanvasAccessibilityNode.Role.TEXT
            )
        }
        return nodes
    }

    private fun inspectSheetAccessibilityNodes(width: Float, height: Float): List<CanvasAccessibilityNode> {
        val playerSide = inspectedPlayer ?: return emptyList()
        val details = inspectedSlot?.let { displayedDetailsForActiveCombatant(playerSide, it) }
            ?: if (playerSide) displayedPlayerDetails() else displayedOpponentDetails()
        val combatants = inspectedSlot?.let { slot ->
            (if (playerSide) displayedPlayerCombatants() else displayedOpponentCombatants())
                .filter { it.slot == slot }
        } ?: (if (playerSide) displayedPlayerCombatants() else displayedOpponentCombatants())
        val effects = combatants.flatMap { it.volatileEffects + it.turnEffects + it.moveEffects }.distinct()
        val name = BattleSession.displayPokemonName(details.name, details.species)
        val description = BattleAccessibilityText.inspectDetails(details, name, effects)
        val bounds = inspectSheetBounds(width, height, playerSide)
        return listOf(
            CanvasAccessibilityNode(
                ACCESSIBLE_INSPECT_DETAILS_ID,
                description,
                Rect().apply { bounds.roundOut(this) }.toCanvasAccessibilityBounds(),
                role = CanvasAccessibilityNode.Role.BUTTON,
                onClick = {
                    inspectedPlayer = null
                    inspectedSlot = null
                    invalidate()
                    performClick()
                }
            )
        )
    }

    private fun addMultiCombatantAccessibilityNodes(
        nodes: MutableList<CanvasAccessibilityNode>,
        width: Float,
        height: Float,
        scale: Float,
        player: Boolean
    ) {
        val combatants = fieldCombatants(
            if (player) displayedPlayerCombatants() else displayedOpponentCombatants(),
            player
        )
        val centerY = height * if (player) 0.67f else 0.42f
        val centeredSlot = BattleCombatantLayout.centeredSlot(session.isTriplesCentered(), combatants)
        val shownBadgeSides = mutableSetOf<String>()
        val badgesBySide = session.battleInfo().ladderBadgesBySide
        combatants.forEachIndexed { index, combatant ->
            val details = displayedDetailsForActiveCombatant(player, combatant.slot) ?: return@forEachIndexed
            val centerX = BattleCombatantLayout.x(width, player, index, combatants.size, centeredSlot, combatant.slot)
            val bounds = RectF(
                centerX - 220f * scale,
                centerY - 360f * scale,
                centerX + 220f * scale,
                centerY + 160f * scale
            )
            val cardBounds = BattleCardLayout.compactBoundsFor(width, height, player, index, combatants.size).toRectF()
            bounds.union(cardBounds)
            val name = BattleSession.displayPokemonName(details.name, details.species)
            val idBase = if (player) ACCESSIBLE_PLAYER_PARTY_BASE else ACCESSIBLE_OPPONENT_PARTY_BASE
            val side = combatant.slot.take(2)
            val badges = ShowdownLadderBadgePresentation.forStatusCard(
                side,
                shownBadgeSides,
                badgesBySide
            )
            addAccessibilityNode(
                nodes,
                idBase + index,
                withLadderBadgeAccessibility(
                    "${if (player) "Your" else "Opponent's"} active Pokémon, ${pokemonAccessibilitySummary(details)}",
                    badges
                ),
                bounds,
                selected = inspectedPlayer == player && inspectedSlot == combatant.slot
            ) {
                selectInspectedPokemon(player, combatant.slot)
            }
        }
    }

    private fun selectInspectedPokemon(player: Boolean, slot: String?) {
        inspectedPlayer = player
        inspectedSlot = slot
        invalidate()
        performClick()
    }

    private fun pokemonAccessibilitySummary(details: BattleSession.PokemonDetails): String {
        val name = BattleSession.displayPokemonName(details.name, details.species)
        return BattleAccessibilityText.pokemon(name, details.level, details.gender, details.hp, details.condition)
    }

    private fun withLadderBadgeAccessibility(label: String, badges: List<ShowdownLadderBadge>): String {
        if (badges.isEmpty()) return label
        return "$label. Ladder badges: ${badges.joinToString(". ") { it.accessibilityLabel }}"
    }

    private fun ladderBadgesForSide(side: String) = ShowdownLadderBadgePresentation.visible(
        session.battleInfo().ladderBadgesBySide[side].orEmpty()
    )

    private fun inspectSheetBounds(width: Float, height: Float, player: Boolean) = if (player) {
        RectF(width * 0.025f, height * 0.14f, width * 0.49f, height * 0.85f)
    } else {
        RectF(width * 0.51f, height * 0.16f, width * 0.975f, height * 0.87f)
    }

    private fun addAccessibilityNode(
        nodes: MutableList<CanvasAccessibilityNode>,
        id: Int,
        label: String,
        bounds: RectF,
        role: CanvasAccessibilityNode.Role = CanvasAccessibilityNode.Role.BUTTON,
        selected: Boolean = false,
        action: () -> Unit
    ) {
        if (bounds.isEmpty) return
        val screenBounds = Rect()
        bounds.roundOut(screenBounds)
        nodes += CanvasAccessibilityNode(
            id,
            label,
            screenBounds.toCanvasAccessibilityBounds(),
            role,
            selected = selected,
            onClick = action
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isTeamPreviewVisible()) {
                    teamPreviewTouchActive = true
                    teamPreviewTouchDownX = event.x
                    return true
                }
                if (inspectTargetAt(event.x, event.y) != null || inspectedPlayer != null) return true
                if (battleFeedBounds.contains(event.x, event.y)) {
                    battleFeedTouchDownY = event.y
                    battleFeedTouchLastY = event.y
                    battleFeedTouchActive = true
                    battleFeedTouchMoved = false
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (teamPreviewTouchActive) return true
                if (!battleFeedTouchActive) return false
                val delta = event.y - battleFeedTouchLastY
                if (abs(delta) > 0.5f) {
                    battleFeedTouchMoved = battleFeedTouchMoved || abs(event.y - battleFeedTouchDownY) > 12f
                    battleFeedTouchLastY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (teamPreviewTouchActive) {
                    teamPreviewTouchActive = false
                    val horizontalDelta = event.x - teamPreviewTouchDownX
                    val direction = when {
                        abs(horizontalDelta) > width * 0.08f -> if (horizontalDelta < 0f) 1 else -1
                        else -> teamPreviewPageDirectionAt(event.x, event.y)
                    }
                    direction?.let(::changeOpponentPreviewPage)
                    performClick()
                    return true
                }
                val wasBattleFeedTouch = battleFeedTouchActive
                battleFeedTouchActive = false
                if (wasBattleFeedTouch && battleFeedTouchMoved) {
                    battleFeedTouchMoved = false
                    performClick()
                    return true
                }
                if (wasBattleFeedTouch) {
                    battleFeedPresentation.advanceOnTap(SystemClock.elapsedRealtime())
                    battleFeedTouchMoved = false
                    invalidate()
                    performClick()
                    return true
                }
                val target = inspectTargetAt(event.x, event.y)
                if (target == null) {
                    inspectedPlayer = null
                    inspectedSlot = null
                } else if (target.player == inspectedPlayer && target.slot == inspectedSlot) {
                    inspectedPlayer = null
                    inspectedSlot = null
                } else {
                    inspectedPlayer = target.player
                    inspectedSlot = target.slot
                }
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (teamPreviewTouchActive) {
                    teamPreviewTouchActive = false
                    return true
                }
                battleFeedTouchActive = false
                battleFeedTouchMoved = false
                return inspectedPlayer != null
            }
        }
        return super.onTouchEvent(event)
    }

    private fun inspectTargetAt(x: Float, y: Float): InspectTarget? {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val scale = min(viewWidth / 1920f, viewHeight / 1080f)
        if (session.isSinglesBattle()) {
            return when {
                playerInspectBounds.contains(x, y) -> InspectTarget(true, null)
                opponentInspectBounds.contains(x, y) -> InspectTarget(false, null)
                else -> null
            }
        }
        val playerCombatants = fieldCombatants(displayedPlayerCombatants(), true)
        val opponentCombatants = fieldCombatants(displayedOpponentCombatants(), false)
        val playerTarget = findMultiInspectTarget(x, y, viewWidth, viewHeight, scale, true, playerCombatants)
        val opponentTarget = findMultiInspectTarget(x, y, viewWidth, viewHeight, scale, false, opponentCombatants)
        return playerTarget ?: opponentTarget ?: when {
            playerInspectBounds.contains(x, y) -> playerCombatants.firstOrNull()?.let { InspectTarget(true, it.slot) }
            opponentInspectBounds.contains(x, y) -> opponentCombatants.firstOrNull()?.let { InspectTarget(false, it.slot) }
            else -> null
        }
    }

    private fun findMultiInspectTarget(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        scale: Float,
        player: Boolean,
        combatants: List<BattleSession.ActiveCombatant>
    ): InspectTarget? {
        if (combatants.isEmpty()) return null
        val centerY = height * if (player) 0.67f else 0.42f
        val centeredSlot = BattleCombatantLayout.centeredSlot(session.isTriplesCentered(), combatants)
        val spriteTarget = combatants.mapIndexed { index, combatant ->
            val centerX = BattleCombatantLayout.x(width, player, index, combatants.size, centeredSlot, combatant.slot)
            val bounds = RectF(
                centerX - 220f * scale,
                centerY - 360f * scale,
                centerX + 220f * scale,
                centerY + 160f * scale
            )
            combatant to bounds
        }.firstOrNull { (_, bounds) -> bounds.contains(x, y) }
        if (spriteTarget != null) return InspectTarget(player, spriteTarget.first.slot)

        val cardTarget = combatants.mapIndexed { index, combatant ->
            combatant to BattleCardLayout.compactBoundsFor(width, height, player, index, combatants.size).toRectF()
        }.firstOrNull { (_, bounds) -> bounds.contains(x, y) }
        return cardTarget?.let { InspectTarget(player, it.first.slot) }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun ensureResourcesRequested(
        playerCombatants: List<BattleSession.ActiveCombatant>,
        opponentCombatants: List<BattleSession.ActiveCombatant>
    ) {
        val requests = BattleSpriteRequests.forScene(
            playerCombatants = playerCombatants,
            opponentCombatants = opponentCombatants,
            singlesBattle = session.isSinglesBattle(),
            style = session.spriteStyle,
            playerFallbackSpecies = session.playerPokemon,
            opponentFallbackSpecies = session.opponentPokemon
        )
        if (spriteRequestTracker.updateIfChanged(requests)) resourcesRequested = false
        if (resourcesRequested) return
        requestResources(requests)
        resourcesRequested = true
    }

    private fun requestResources(requests: BattleSceneSpriteRequests) {
        val backdropName = session.showdownBackdrop()
        if (backdropName != requestedBackdrop) {
            requestedBackdrop = backdropName
            backdrop = null
            spriteCache.requestBackdrop(backdropName) { asset ->
                if (backdropName == requestedBackdrop) {
                    backdrop = asset
                    invalidate()
                }
            }
        }
        if (session.battlePhase == BattleSession.BattlePhase.TEAM_PREVIEW) return
        val playerRequest = requests.playerLead
        if (playerRequest != null) {
            if (playerRequest != requestedPlayerSprite) {
                requestedPlayerSprite = playerRequest
                playerSprite?.stopAnimation()
                playerSprite = null
                spriteCache.requestPokemon(playerRequest) { asset ->
                    if (playerRequest == requestedPlayerSprite) {
                        playerSprite?.takeUnless { it === asset }?.stopAnimation()
                        playerSprite = asset
                        invalidate()
                    } else asset?.stopAnimation()
                }
            }
        } else {
            requestedPlayerSprite = null
            playerSprite?.stopAnimation()
            playerSprite = null
        }
        val opponentRequest = requests.opponentLead
        if (opponentRequest != null) {
            if (opponentRequest != requestedOpponentSprite) {
                requestedOpponentSprite = opponentRequest
                opponentSprite?.stopAnimation()
                opponentSprite = null
                spriteCache.requestPokemon(opponentRequest) { asset ->
                    if (opponentRequest == requestedOpponentSprite) {
                        opponentSprite?.takeUnless { it === asset }?.stopAnimation()
                        opponentSprite = asset
                        invalidate()
                    } else asset?.stopAnimation()
                }
            }
        } else {
            requestedOpponentSprite = null
            opponentSprite?.stopAnimation()
            opponentSprite = null
        }
        if (requests.singlesBattle) {
            requestedPlayerActiveSprites.clear()
            requestedOpponentActiveSprites.clear()
            playerActiveSprites.values.forEach { it?.stopAnimation() }
            opponentActiveSprites.values.forEach { it?.stopAnimation() }
            playerActiveSprites.clear()
            opponentActiveSprites.clear()
        } else {
            requestActiveSprites(
                requests.playerActive,
                playerActiveSprites,
                requestedPlayerActiveSprites
            )
            requestActiveSprites(
                requests.opponentActive,
                opponentActiveSprites,
                requestedOpponentActiveSprites
            )
        }
        requestHeldItemSprites()
        SHOWDOWN_EFFECTS.forEach { name ->
            if (requestedEffects.add(name)) {
                spriteCache.requestEffect(name) { asset ->
                    if (name !in requestedEffects) return@requestEffect
                    if (asset != null) effectAssets[name] = asset
                    invalidate()
                }
            }
        }
    }

    private fun requestTeamPreviewSprites() {
        val party = session.opponentPartyDetails()
        val teamPreviewVisible = session.battlePhase == BattleSession.BattlePhase.TEAM_PREVIEW || shouldShowPublicTeamPreview()
        val visibleIndices = if (teamPreviewVisible) {
            opponentPreviewIndices(party).toSet()
        } else {
            emptySet()
        }
        requestedPreviewSprites.keys.filterNot(visibleIndices::contains).toList().forEach { index ->
            requestedPreviewSprites.remove(index)
            previewSprites.remove(index)?.stopAnimation()
        }
        if (!teamPreviewVisible) return
        visibleIndices.forEach { index ->
            val details = party[index]
            val species = details.species.ifBlank { details.name }.trim()
            if (species.isBlank() || species.equals("Unknown", true)) return@forEach
            val request = BattleSpriteRequests.single(
                species,
                BattleSpriteSide.OPPONENT,
                session.spriteStyle,
                details.shiny
            )
            if (requestedPreviewSprites[index] == request) return@forEach
            requestedPreviewSprites[index] = request
            previewSprites[index]?.stopAnimation()
            previewSprites[index] = null
            var staticFallbackRequested = false
            fun requestStaticFallback() {
                if (staticFallbackRequested || requestedPreviewSprites[index] != request) return
                staticFallbackRequested = true
                spriteCache.requestStaticDexSprite(species, details.shiny) { fallback ->
                    if (requestedPreviewSprites[index] != request) {
                        fallback?.stopAnimation()
                        return@requestStaticDexSprite
                    }
                    val current = previewSprites[index]
                    if (fallback != null && (current == null || !current.isAnimated)) {
                        current?.takeUnless { it === fallback }?.stopAnimation()
                        previewSprites[index] = fallback
                        invalidate()
                    } else {
                        fallback?.stopAnimation()
                    }
                }
            }
            spriteCache.requestTeamPreviewPokemon(request) { asset ->
                if (requestedPreviewSprites[index] == request) {
                    previewSprites[index]?.takeUnless { it === asset }?.stopAnimation()
                    previewSprites[index] = asset
                    invalidate()
                } else {
                    asset?.stopAnimation()
                }
            }
            postDelayed(::requestStaticFallback, TEAM_PREVIEW_STATIC_FALLBACK_DELAY_MILLIS)
        }
    }

    private fun shouldShowPublicTeamPreview() =
        !session.isBattleFinished() &&
            (session.isReplayMode() || session.isSpectatorMode()) &&
            session.opponentPartyDetails().isNotEmpty() &&
            session.playerActiveCombatants().isEmpty() &&
            session.opponentActiveCombatants().isEmpty()

    private fun isTeamPreviewVisible() =
        session.battlePhase == BattleSession.BattlePhase.TEAM_PREVIEW || shouldShowPublicTeamPreview()

    private fun opponentPreviewIndices(party: List<BattleSession.PokemonDetails>): List<Int> {
        val rosterKey = party.joinToString("|") { "${it.name}:${it.species}" }
        if (opponentPreviewRosterKey != rosterKey) {
            opponentPreviewRosterKey = rosterKey
            opponentPreviewPageIndex = 0
        }
        opponentPreviewPageIndex = TeamRosterPager.movePage(opponentPreviewPageIndex, party.size, 0)
        return TeamRosterPager.visibleIndices(party.size, opponentPreviewPageIndex).toList()
    }

    private fun teamPreviewPageDirectionAt(x: Float, y: Float): Int? {
        val controls = BattleTeamPreviewLayout.navigationSlots(width.toFloat(), height.toFloat())
        val previous = RectF(controls[0].left, controls[0].top, controls[0].right, controls[0].bottom)
        val next = RectF(controls[1].left, controls[1].top, controls[1].right, controls[1].bottom)
        return when {
            previous.contains(x, y) -> -1
            next.contains(x, y) -> 1
            else -> null
        }
    }

    private fun changeOpponentPreviewPage(direction: Int) {
        val party = session.opponentPartyDetails()
        opponentPreviewIndices(party)
        val nextPage = TeamRosterPager.movePage(opponentPreviewPageIndex, party.size, direction)
        if (nextPage == opponentPreviewPageIndex) return
        opponentPreviewPageIndex = nextPage
        invalidate()
        performClick()
    }

    private fun lightweightMoveEffectActive(nowNanos: Long): Boolean {
        val moveActive = lightweightMoveStartedAtNanos > 0L && nowNanos - lightweightMoveStartedAtNanos < scaledLightweightMoveDurationNanos()
        val impactActive = lightweightMoveAnimationEnabled && lightweightImpactAtNanos > 0L && nowNanos - lightweightImpactAtNanos < scaledLightweightImpactDurationNanos()
        val statActive = lightweightStatEffectAtNanos > 0L && nowNanos - lightweightStatEffectAtNanos < scaledLightweightStatDurationNanos()
        return moveActive || impactActive || statActive
    }

    private fun drawLightweightMoveEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        nowNanos: Long
    ) {
        if (!lightweightMoveEffectActive(nowNanos)) return
        val singles = session.isSinglesBattle()
        val playerX = if (singles) ShowdownBattleLayout.x(width, ShowdownBattleLayout.PLAYER_X) else width * 0.30f
        val playerY = if (singles) ShowdownBattleLayout.y(height, ShowdownBattleLayout.PLAYER_Y) else height * 0.67f
        val opponentX = if (singles) ShowdownBattleLayout.x(width, ShowdownBattleLayout.OPPONENT_X) else width * 0.73f
        val opponentY = if (singles) ShowdownBattleLayout.y(height, ShowdownBattleLayout.OPPONENT_Y) else height * 0.42f
        val actorCenter = lightweightMoveActorSlot?.let {
            lightweightTargetCenter(width, height, it, playerX, playerY, opponentX, opponentY)
        }
        val actorX = actorCenter?.first ?: if (lightweightMoveActorPlayer) playerX else opponentX
        val actorY = actorCenter?.second ?: if (lightweightMoveActorPlayer) playerY else opponentY
        val impactAt = lightweightImpactAtNanos
        val targetPlayer = lightweightMoveTargetPlayer ?: !lightweightMoveActorPlayer
        val targetCenter = lightweightMoveTargetSlot?.let {
            lightweightTargetCenter(width, height, it, playerX, playerY, opponentX, opponentY)
        }
        val targetX = targetCenter?.first ?: if (targetPlayer) playerX else opponentX
        val targetY = targetCenter?.second ?: if (targetPlayer) playerY else opponentY
        val palette = lightweightMovePalette(lightweightMoveType)
        if (lightweightMoveAnimationEnabled) {
            when (lightweightMoveStyle) {
                BattleMoveVisualStyle.STATUS -> drawStatusMoveEffect(canvas, targetX, targetY, scale, nowNanos, palette)
                else -> drawAttackMoveEffect(
                    canvas,
                    width,
                    height,
                    playerX,
                    playerY,
                    opponentX,
                    opponentY,
                    targetPlayer,
                    impactAt,
                    actorX,
                    actorY,
                    targetX,
                    targetY,
                    scale,
                    nowNanos,
                    palette,
                    lightweightMoveStyle
                )
            }
        }
        drawStatEffect(canvas, targetX, targetY, scale, nowNanos, palette)
    }

    private fun drawAttackMoveEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        playerX: Float,
        playerY: Float,
        opponentX: Float,
        opponentY: Float,
        targetPlayer: Boolean,
        impactAt: Long,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        scale: Float,
        nowNanos: Long,
        palette: MoveEffectPalette,
        style: BattleMoveVisualStyle
    ) {
        val moveProgress = ((nowNanos - lightweightMoveStartedAtNanos).toFloat() / scaledLightweightMoveDurationNanos()).coerceIn(0f, 1f)
        if (impactAt > 0L && nowNanos >= impactAt) {
            val progress = ((nowNanos - impactAt).toFloat() / scaledLightweightImpactDurationNanos()).coerceIn(0f, 1f)
            val radius = (42f + progress * 170f) * scale
            val impactTargets = lightweightImpactTargets.ifEmpty { listOf(if (targetPlayer) "p1a" else "p2a") }
            impactTargets.forEach { target ->
                val center = lightweightTargetCenter(width, height, target, playerX, playerY, opponentX, opponentY)
                drawImpactBurst(canvas, center.first, center.second - 38f * scale, radius, progress, scale, palette)
            }
            return
        }
        drawTypedMoveEffect(canvas, style, actorX, actorY, targetX, targetY, moveProgress, scale, nowNanos, palette)
    }

    private fun drawTypedMoveEffect(
        canvas: Canvas,
        style: BattleMoveVisualStyle,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        progress: Float,
        scale: Float,
        nowNanos: Long,
        palette: MoveEffectPalette
    ) {
        val eased = progress * progress * (3f - 2f * progress)
        val centerX = actorX + (targetX - actorX) * eased
        val centerY = actorY + (targetY - actorY) * eased - 42f * scale
        when (style) {
            BattleMoveVisualStyle.CONTACT_STRIKE -> drawContactStrikeEffect(canvas, actorX, actorY, targetX, targetY, progress, scale, palette)
            BattleMoveVisualStyle.GROUND_RIPPLE -> drawGroundRippleEffect(canvas, actorX, actorY, targetX, targetY, progress, scale, palette)
            BattleMoveVisualStyle.FIRE_BURST -> drawFireBurstEffect(canvas, actorX, actorY, targetX, targetY, progress, scale, palette)
            BattleMoveVisualStyle.WATER_WAVE -> drawWaterWaveEffect(canvas, centerX, centerY, progress, scale, palette)
            BattleMoveVisualStyle.ELECTRIC_ARC -> drawElectricArcEffect(canvas, actorX, actorY, targetX, targetY, progress, scale, palette)
            BattleMoveVisualStyle.ICE_SHARDS -> drawIceShardEffect(canvas, actorX, actorY, targetX, targetY, progress, scale, palette)
            BattleMoveVisualStyle.LEAF_SPIRAL -> drawLeafSpiralEffect(canvas, actorX, actorY, targetX, targetY, progress, scale, palette)
            BattleMoveVisualStyle.PSYCHIC_PULSE -> drawPsychicPulseEffect(canvas, centerX, centerY, progress, scale, palette)
            BattleMoveVisualStyle.WIND_CRESCENT -> drawWindCrescentEffect(canvas, centerX, centerY, progress, scale, palette)
            BattleMoveVisualStyle.TYPE_BURST -> drawTypeBurstEffect(canvas, centerX, centerY, progress, scale, palette)
            BattleMoveVisualStyle.STATUS -> Unit
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.alpha = 255
    }

    private fun drawContactStrikeEffect(
        canvas: Canvas,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        val eased = progress * progress * (3f - 2f * progress)
        val centerX = actorX + (targetX - actorX) * eased
        val centerY = actorY + (targetY - actorY) * eased - 42f * scale
        val slashLength = (30f + 38f * sin(progress * Math.PI).toFloat()) * scale
        val direction = atan2((targetY - actorY).toDouble(), (targetX - actorX).toDouble()).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 15f * scale
        paint.color = Color.argb(190, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
        canvas.save()
        canvas.rotate(direction * 180f / Math.PI.toFloat(), centerX, centerY)
        canvas.drawLine(centerX - slashLength, centerY - 20f * scale, centerX + slashLength, centerY + 20f * scale, paint)
        paint.strokeWidth = 10f * scale
        paint.color = Color.argb(235, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
        canvas.drawLine(centerX - slashLength * 0.8f, centerY + 23f * scale, centerX + slashLength * 0.8f, centerY - 23f * scale, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
    }

    private fun drawGroundRippleEffect(
        canvas: Canvas,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        val eased = progress * progress * (3f - 2f * progress)
        val centerX = actorX + (targetX - actorX) * eased
        val centerY = actorY + (targetY - actorY) * eased + 22f * scale
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f * scale
        for (index in 0..2) {
            val radius = (24f + ((progress * 3f + index * 0.28f) % 1f) * 76f) * scale
            paint.color = Color.argb((190f * (1f - index * 0.2f)).toInt(), Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
            fieldEffectBounds.set(centerX - radius, centerY - radius * 0.22f, centerX + radius, centerY + radius * 0.22f)
            canvas.drawOval(fieldEffectBounds, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(105, Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary))
        canvas.drawCircle(centerX, centerY, 13f * scale, paint)
    }

    private fun drawFireBurstEffect(
        canvas: Canvas,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        val angle = atan2((targetY - actorY).toDouble(), (targetX - actorX).toDouble()).toFloat() * 180f / Math.PI.toFloat() + 90f
        for (index in 0 until 4) {
            val particleProgress = (progress - index * 0.075f).coerceIn(0f, 1f)
            val x = actorX + (targetX - actorX) * particleProgress
            val y = actorY + (targetY - actorY) * particleProgress - 42f * scale
            val radius = (9f + (1f - index * 0.12f) * 14f * sin((particleProgress * Math.PI).toFloat())) * scale
            canvas.save()
            canvas.rotate(angle, x, y)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(220, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
            fieldEffectBounds.set(x - radius * 0.62f, y - radius * 1.55f, x + radius * 0.62f, y + radius * 1.3f)
            canvas.drawOval(fieldEffectBounds, paint)
            paint.color = Color.argb(230, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
            fieldEffectBounds.inset(radius * 0.2f, radius * 0.3f)
            canvas.drawOval(fieldEffectBounds, paint)
            canvas.restore()
        }
    }

    private fun drawWaterWaveEffect(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (index in 0..2) {
            val radius = (26f + ((progress * 2.8f + index * 0.31f) % 1f) * 76f) * scale
            paint.strokeWidth = (12f - index * 2f) * scale
            paint.color = Color.argb(210 - index * 38, Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary))
            fieldEffectBounds.set(centerX - radius, centerY - radius * 0.62f, centerX + radius, centerY + radius * 0.62f)
            canvas.drawArc(fieldEffectBounds, 195f, 148f, false, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawElectricArcEffect(
        canvas: Canvas,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        val endProgress = (progress * 1.18f).coerceIn(0f, 1f)
        val dx = (targetX - actorX) * endProgress
        val dy = (targetY - actorY) * endProgress
        val length = maxOf(1f, kotlin.math.sqrt(dx * dx + dy * dy))
        val normalX = -dy / length
        val normalY = dx / length
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 12f * scale
        paint.color = Color.argb(142, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
        var previousX = actorX
        var previousY = actorY - 42f * scale
        for (index in 1..6) {
            val fraction = index / 6f
            val jitter = sin(progress * 24f + index * 2f) * 27f * scale
            val x = actorX + dx * fraction + normalX * jitter
            val y = actorY + dy * fraction - 42f * scale + normalY * jitter
            canvas.drawLine(previousX, previousY, x, y, paint)
            previousX = x
            previousY = y
        }
        paint.strokeWidth = 5f * scale
        paint.color = Color.argb(240, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
        previousX = actorX
        previousY = actorY - 42f * scale
        for (index in 1..6) {
            val fraction = index / 6f
            val jitter = sin(progress * 24f + index * 2f) * 27f * scale
            val x = actorX + dx * fraction + normalX * jitter
            val y = actorY + dy * fraction - 42f * scale + normalY * jitter
            canvas.drawLine(previousX, previousY, x, y, paint)
            previousX = x
            previousY = y
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawIceShardEffect(
        canvas: Canvas,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        val dx = targetX - actorX
        val dy = targetY - actorY
        val length = maxOf(1f, kotlin.math.sqrt(dx * dx + dy * dy))
        val normalX = -dy / length
        val normalY = dx / length
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f * scale
        for (index in 0 until 5) {
            val particleProgress = (progress - index * 0.055f).coerceIn(0f, 1f)
            val centerX = actorX + dx * particleProgress + normalX * (index - 2) * 35f * scale
            val centerY = actorY + dy * particleProgress + normalY * (index - 2) * 35f * scale - 42f * scale
            val size = (14f + index % 3 * 6f) * scale
            paint.color = Color.argb(220, Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary))
            canvas.drawLine(centerX, centerY - size, centerX + size * 0.55f, centerY, paint)
            canvas.drawLine(centerX + size * 0.55f, centerY, centerX, centerY + size, paint)
            canvas.drawLine(centerX, centerY + size, centerX - size * 0.55f, centerY, paint)
            canvas.drawLine(centerX - size * 0.55f, centerY, centerX, centerY - size, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawLeafSpiralEffect(
        canvas: Canvas,
        actorX: Float,
        actorY: Float,
        targetX: Float,
        targetY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        val dx = targetX - actorX
        val dy = targetY - actorY
        for (index in 0 until 6) {
            val particleProgress = (progress - index * 0.045f).coerceIn(0f, 1f)
            val angle = progress * 17f + index * 2.1f
            val sway = sin(angle) * 52f * scale
            val x = actorX + dx * particleProgress + sway
            val y = actorY + dy * particleProgress - 42f * scale + cos(angle) * 32f * scale
            canvas.save()
            canvas.rotate(angle * 57.2958f, x, y)
            paint.style = Paint.Style.FILL
            paint.color = if (index % 2 == 0) palette.secondary else palette.accent
            fieldEffectBounds.set(x - 8f * scale, y - 21f * scale, x + 8f * scale, y + 21f * scale)
            canvas.drawOval(fieldEffectBounds, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * scale
            paint.color = Color.argb(195, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
            canvas.drawLine(x, y - 17f * scale, x, y + 17f * scale, paint)
            canvas.restore()
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawPsychicPulseEffect(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        paint.style = Paint.Style.STROKE
        for (index in 0..3) {
            val phase = (progress * 2.6f + index * 0.27f) % 1f
            val radius = (18f + phase * 102f) * scale
            paint.strokeWidth = (9f - phase * 5f) * scale
            paint.color = Color.argb(((1f - phase) * 205f).toInt(), Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawWindCrescentEffect(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (index in 0..2) {
            val radius = (48f + index * 37f + sin(progress * Math.PI).toFloat() * 30f) * scale
            paint.strokeWidth = (13f - index * 3f) * scale
            paint.color = Color.argb(205 - index * 48, Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary))
            fieldEffectBounds.set(centerX - radius, centerY - radius * 0.7f, centerX + radius, centerY + radius * 0.7f)
            canvas.drawArc(fieldEffectBounds, 195f + index * 9f, 150f, false, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawTypeBurstEffect(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        val radius = (18f + 42f * sin(progress * Math.PI).toFloat()) * scale
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 9f * scale
        paint.color = Color.argb(220, Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary))
        for (index in 0 until 8) {
            val angle = index * Math.PI / 4.0 + progress * Math.PI * 1.5
            val inner = radius * 0.32f
            val outer = radius * (0.8f + index % 2 * 0.4f)
            canvas.drawLine(
                centerX + cos(angle).toFloat() * inner,
                centerY + sin(angle).toFloat() * inner,
                centerX + cos(angle).toFloat() * outer,
                centerY + sin(angle).toFloat() * outer,
                paint
            )
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 12f * scale, paint)
    }

    private fun drawStatusMoveEffect(
        canvas: Canvas,
        targetX: Float,
        targetY: Float,
        scale: Float,
        nowNanos: Long,
        palette: MoveEffectPalette
    ) {
        val progress = ((nowNanos - lightweightMoveStartedAtNanos).toFloat() / scaledLightweightMoveDurationNanos()).coerceIn(0f, 1f)
        val centerY = targetY - 42f * scale
        val pulse = (sin(progress * Math.PI * 2.0).toFloat() + 1f) * 0.5f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (8f + pulse * 6f) * scale
        paint.color = Color.argb(((1f - progress) * 175f).toInt(), Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
        canvas.drawCircle(targetX, centerY, (65f + progress * 90f) * scale, paint)
        paint.strokeWidth = 4f * scale
        paint.color = Color.argb(((1f - progress) * 220f).toInt(), Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
        for (index in 0 until 8) {
            val angle = progress * Math.PI * 2.0 + index * Math.PI / 4.0
            val inner = 80f * scale
            val outer = (116f + pulse * 16f) * scale
            canvas.drawLine(
                targetX + cos(angle).toFloat() * inner,
                centerY + sin(angle).toFloat() * inner,
                targetX + cos(angle).toFloat() * outer,
                centerY + sin(angle).toFloat() * outer,
                paint
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawStatEffect(
        canvas: Canvas,
        targetX: Float,
        targetY: Float,
        scale: Float,
        nowNanos: Long,
        palette: MoveEffectPalette
    ) {
        if (lightweightStatEffectAtNanos <= 0L) return
        val progress = ((nowNanos - lightweightStatEffectAtNanos).toFloat() / scaledLightweightStatDurationNanos()).coerceIn(0f, 1f)
        if (progress >= 1f) return
        val centerY = targetY - 56f * scale
        val direction = lightweightStatDirection.toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f * scale
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(((1f - progress) * 220f).toInt(), Color.red(if (direction > 0f) palette.accent else Color.rgb(245, 112, 128)), Color.green(if (direction > 0f) palette.accent else Color.rgb(245, 112, 128)), Color.blue(if (direction > 0f) palette.accent else Color.rgb(245, 112, 128)))
        for (index in 0 until 3) {
            val x = targetX + (index - 1) * 48f * scale
            val baseY = centerY + 54f * scale
            val travel = (progress * 100f + index * 16f) * direction * scale
            canvas.drawLine(x, baseY + travel, x, baseY - 38f * scale + travel, paint)
            canvas.drawLine(x, baseY - 38f * scale + travel, x - 12f * scale, baseY - 22f * scale + travel, paint)
            canvas.drawLine(x, baseY - 38f * scale + travel, x + 12f * scale, baseY - 22f * scale + travel, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawImpactBurst(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        progress: Float,
        scale: Float,
        palette: MoveEffectPalette
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (16f - progress * 10f) * scale
        paint.color = Color.argb(((1f - progress) * 225f).toInt(), Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.strokeWidth = 4f * scale
        paint.color = Color.argb(((1f - progress) * 210f).toInt(), 255, 255, 255)
        for (index in 0 until 10) {
            val angle = index * Math.PI / 5.0
            val inner = radius * 0.55f
            val outer = radius * (0.95f + (index % 2) * 0.15f)
            canvas.drawLine(
                centerX + cos(angle).toFloat() * inner,
                centerY + sin(angle).toFloat() * inner,
                centerX + cos(angle).toFloat() * outer,
                centerY + sin(angle).toFloat() * outer,
                paint
            )
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(((1f - progress) * 110f).toInt(), Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
        canvas.drawCircle(centerX, centerY, radius * 0.42f, paint)
    }

    private data class MoveEffectPalette(val primary: Int, val secondary: Int, val accent: Int)

    private fun lightweightMovePalette(type: String): MoveEffectPalette = when (type) {
        "FIRE" -> MoveEffectPalette(Color.rgb(193, 55, 28), Color.rgb(255, 167, 57), Color.rgb(255, 235, 171))
        "WATER" -> MoveEffectPalette(Color.rgb(38, 105, 221), Color.rgb(74, 194, 255), Color.rgb(214, 248, 255))
        "ELECTRIC" -> MoveEffectPalette(Color.rgb(194, 145, 12), Color.rgb(255, 223, 74), Color.rgb(255, 251, 195))
        "GRASS" -> MoveEffectPalette(Color.rgb(41, 139, 70), Color.rgb(118, 221, 102), Color.rgb(226, 255, 202))
        "ICE" -> MoveEffectPalette(Color.rgb(56, 154, 190), Color.rgb(159, 244, 255), Color.rgb(240, 255, 255))
        "FIGHTING" -> MoveEffectPalette(Color.rgb(171, 55, 38), Color.rgb(245, 116, 73), Color.rgb(255, 228, 180))
        "POISON" -> MoveEffectPalette(Color.rgb(116, 45, 145), Color.rgb(218, 110, 229), Color.rgb(255, 212, 255))
        "GROUND" -> MoveEffectPalette(Color.rgb(145, 90, 38), Color.rgb(222, 168, 82), Color.rgb(255, 235, 185))
        "FLYING" -> MoveEffectPalette(Color.rgb(75, 102, 190), Color.rgb(163, 195, 255), Color.rgb(239, 246, 255))
        "PSYCHIC" -> MoveEffectPalette(Color.rgb(174, 47, 132), Color.rgb(255, 112, 199), Color.rgb(255, 225, 247))
        "BUG" -> MoveEffectPalette(Color.rgb(72, 128, 40), Color.rgb(180, 226, 69), Color.rgb(240, 255, 196))
        "ROCK" -> MoveEffectPalette(Color.rgb(105, 81, 42), Color.rgb(201, 165, 92), Color.rgb(255, 240, 191))
        "GHOST" -> MoveEffectPalette(Color.rgb(69, 54, 137), Color.rgb(153, 125, 245), Color.rgb(232, 224, 255))
        "DRAGON" -> MoveEffectPalette(Color.rgb(55, 64, 176), Color.rgb(133, 144, 255), Color.rgb(225, 231, 255))
        "DARK" -> MoveEffectPalette(Color.rgb(33, 37, 57), Color.rgb(118, 120, 151), Color.rgb(231, 232, 255))
        "STEEL" -> MoveEffectPalette(Color.rgb(73, 101, 125), Color.rgb(185, 215, 232), Color.rgb(244, 253, 255))
        "FAIRY" -> MoveEffectPalette(Color.rgb(191, 74, 157), Color.rgb(255, 151, 223), Color.rgb(255, 236, 252))
        else -> MoveEffectPalette(Color.rgb(78, 91, 116), Color.rgb(173, 194, 226), Color.rgb(241, 247, 255))
    }

    private fun inferMoveType(move: String): String {
        val normalized = move.lowercase()
        return when {
            normalized.containsAny("flame", "fire", "ember", "heat", "blaze") -> "FIRE"
            normalized.containsAny("water", "hydro", "aqua", "surf", "scald") -> "WATER"
            normalized.containsAny("thunder", "spark", "volt", "electro", "zap") -> "ELECTRIC"
            normalized.containsAny("leaf", "vine", "seed", "grass", "wood", "energy") -> "GRASS"
            normalized.containsAny("ice", "frost", "blizzard", "freeze") -> "ICE"
            normalized.containsAny("shadow", "ghost", "hex", "spirit") -> "GHOST"
            normalized.containsAny("psychic", "mind", "psybeam", "future") -> "PSYCHIC"
            normalized.containsAny("dragon", "scale", "draco") -> "DRAGON"
            normalized.containsAny("dark", "night", "bite", "crunch", "knock") -> "DARK"
            normalized.containsAny("fairy", "gleam", "charm", "kiss") -> "FAIRY"
            else -> "NORMAL"
        }
    }

    private fun inferMoveCategory(move: String): String {
        val normalized = move.lowercase()
        return if (normalized.containsAny("protect", "recover", "roost", "dance", "plot", "toxic", "will-o", "status")) "STATUS" else "PHYSICAL"
    }

    private fun String.containsAny(vararg values: String) = values.any(::contains)

    private fun Int.signum() = when {
        this < 0 -> -1
        this > 0 -> 1
        else -> 0
    }

    private fun scaledLightweightMoveDurationNanos() =
        BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightMoveDurationNanos, playbackSpeed).toFloat()

    private fun scaledLightweightImpactDurationNanos() =
        BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightImpactDurationNanos, playbackSpeed).toFloat()

    private fun scaledLightweightStatDurationNanos() =
        BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightStatDurationNanos, playbackSpeed).toFloat()

    private fun shiftTimestamp(timestamp: Long, duration: Long): Long =
        timestamp.takeIf { it > 0L }?.plus(duration) ?: 0L

    private fun lightweightTargetCenter(
        width: Float,
        height: Float,
        target: String,
        playerX: Float,
        playerY: Float,
        opponentX: Float,
        opponentY: Float
    ): Pair<Float, Float> {
        val player = session.isLocalBattleSide(target)
        if (session.isSinglesBattle()) return if (player) playerX to playerY else opponentX to opponentY
        val combatants = if (player) fieldCombatants(session.playerActiveCombatants(), true) else fieldCombatants(session.opponentActiveCombatants(), false)
        val x = BattleCombatantLayout.xForSlot(
            width,
            player,
            combatants,
            BattleCombatantLayout.centeredSlot(session.isTriplesCentered(), combatants),
            target
        ) ?: return if (player) playerX to playerY else opponentX to opponentY
        return x to if (player) height * 0.67f else height * 0.42f
    }

    private fun protocolSlot(actor: String?): String? = actor
        ?.substringBefore(':')
        ?.trim()
        ?.takeIf { it.matches(Regex("p[1-4][a-z]")) }

    private fun requestHeldItemSprites() {
        val itemNames = buildList {
            add(displayedPlayerDetails().item)
            add(displayedOpponentDetails().item)
            displayedPlayerCombatants().forEach { combatant ->
                add(displayedDetailsForActiveCombatant(true, combatant.slot)?.item.orEmpty())
            }
            displayedOpponentCombatants().forEach { combatant ->
                add(displayedDetailsForActiveCombatant(false, combatant.slot)?.item.orEmpty())
            }
        }
        itemNames.forEach { item ->
            val path = BattleItemPresentation.iconPath(item) ?: return@forEach
            if (!requestedItemSprites.add(path)) return@forEach
            spriteCache.requestItem(item) { asset ->
                if (path !in requestedItemSprites) {
                    asset?.stopAnimation()
                    return@requestItem
                }
                itemSprites[path]?.stopAnimation()
                itemSprites[path] = asset
                invalidate()
            }
        }
    }

    private fun requestLadderBadgeSprites(badgesBySide: Map<String, List<ShowdownLadderBadge>>) {
        badgesBySide.values
            .flatMap(ShowdownLadderBadgePresentation::visible)
            .distinctBy(ShowdownLadderBadge::assetPath)
            .forEach { badge ->
                val path = badge.assetPath
                if (!requestedLadderBadgeSprites.add(path)) return@forEach
                spriteCache.requestLadderBadge(badge) { asset ->
                    if (path !in requestedLadderBadgeSprites) {
                        asset?.stopAnimation()
                        return@requestLadderBadge
                    }
                    ladderBadgeSprites[path]?.stopAnimation()
                    ladderBadgeSprites[path] = asset
                    invalidate()
                }
            }
    }

    private fun requestActiveSprites(
        plannedRequests: List<BattleSpriteSlotRequest>,
        assets: MutableMap<String, ShowdownSpriteCache.SpriteAsset?>,
        requests: MutableMap<String, BattleSpriteRequest>
    ) {
        val activeSlots = plannedRequests.map { it.slot }.toSet()
        requests.keys.filterNot(activeSlots::contains).toList().forEach {
            requests.remove(it)
            assets.remove(it)?.stopAnimation()
        }
        plannedRequests.forEach { plannedRequest ->
            val slot = plannedRequest.slot
            val request = plannedRequest.request
            if (requests[slot] == request) return@forEach
            requests[slot] = request
            assets[slot]?.stopAnimation()
            assets[slot] = null
            spriteCache.requestPokemon(request) { asset ->
                if (requests[slot] == request) {
                    assets[slot]?.takeUnless { it === asset }?.stopAnimation()
                    assets[slot] = asset
                    invalidate()
                } else asset?.stopAnimation()
            }
        }
    }

    private fun drawBackdrop(canvas: Canvas, width: Float, height: Float) {
        paint.shader = LinearGradient(0f, 0f, width, height, Color.rgb(10, 21, 40), Color.rgb(34, 12, 58), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        backdrop?.let {
            source.set(0, 0, it.width, it.height)
            destination.set(0f, 0f, width, height)
            paint.alpha = 212
            canvas.drawBitmap(it, source, destination, paint)
            paint.alpha = 255
        }
    }

    private fun drawFieldVisuals(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        nowNanos: Long,
        visuals: BattleFieldVisuals
    ) {
        if (!visuals.hasActiveVisuals) return
        val timeMillis = (nowNanos / 1_000_000L % 120_000L).toFloat()
        visuals.terrain?.let { drawTerrainVisual(canvas, width, height, scale, timeMillis, it) }
        visuals.weather?.let { drawWeatherVisual(canvas, width, height, scale, timeMillis, it) }
        visuals.overlays.forEach { drawFieldOverlay(canvas, width, height, scale, timeMillis, it) }
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.alpha = 255
    }

    private fun drawTerrainVisual(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        timeMillis: Float,
        terrain: BattleTerrainVisual
    ) {
        val horizon = height * 0.55f
        val terrainColor = when (terrain) {
            BattleTerrainVisual.ELECTRIC -> Color.rgb(62, 132, 255)
            BattleTerrainVisual.GRASSY -> Color.rgb(61, 198, 102)
            BattleTerrainVisual.MISTY -> Color.rgb(255, 151, 219)
            BattleTerrainVisual.PSYCHIC -> Color.rgb(175, 81, 255)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(42, Color.red(terrainColor), Color.green(terrainColor), Color.blue(terrainColor))
        canvas.drawRect(0f, horizon, width, height, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * scale
        paint.color = Color.argb(96, Color.red(terrainColor), Color.green(terrainColor), Color.blue(terrainColor))
        when (terrain) {
            BattleTerrainVisual.ELECTRIC, BattleTerrainVisual.PSYCHIC -> {
                val centerX = width * 0.5f
                for (index in 0..12) {
                    val floorX = width * index / 12f
                    canvas.drawLine(centerX, horizon, floorX, height, paint)
                }
                for (index in 1..5) {
                    val depth = index / 6f
                    val y = horizon + (height - horizon) * depth * depth
                    canvas.drawLine(0f, y, width, y, paint)
                }
                if (terrain == BattleTerrainVisual.PSYCHIC) {
                    paint.strokeWidth = 7f * scale
                    paint.color = Color.argb(82, 241, 193, 255)
                    val phase = timeMillis * 0.018f
                    val pulse = 26f * scale + 12f * scale * sin(phase)
                    fieldEffectBounds.set(centerX - pulse * 3f, horizon + 20f * scale, centerX + pulse * 3f, horizon + 20f * scale + pulse)
                    canvas.drawOval(fieldEffectBounds, paint)
                }
            }
            BattleTerrainVisual.GRASSY -> {
                paint.strokeWidth = 4f * scale
                paint.color = Color.argb(178, 117, 238, 126)
                for (index in 0 until 26) {
                    val x = width * index / 25f
                    val groundY = horizon + (height - horizon) * (0.36f + (index % 4) * 0.13f)
                    val sway = sin(timeMillis * 0.002f + index) * 10f * scale
                    canvas.drawLine(x, groundY, x - 7f * scale + sway, groundY - 23f * scale, paint)
                    canvas.drawLine(x, groundY, x + 8f * scale + sway, groundY - 19f * scale, paint)
                }
            }
            BattleTerrainVisual.MISTY -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(30, 255, 203, 244)
                fieldEffectBounds.set(-width * 0.2f, horizon - 20f * scale, width * 1.2f, height * 0.82f)
                canvas.drawOval(fieldEffectBounds, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 12f * scale
                paint.color = Color.argb(62, 255, 199, 243)
                for (index in 0..3) {
                    val y = horizon + (height - horizon) * (0.18f + index * 0.19f)
                    val sway = sin(timeMillis * 0.0013f + index) * 24f * scale
                    canvas.drawLine(width * 0.06f + sway, y, width * 0.94f + sway, y, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawWeatherVisual(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        timeMillis: Float,
        weather: BattleWeatherVisual
    ) {
        when (weather) {
            BattleWeatherVisual.RAIN -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 2.5f * scale
                for (index in 0 until 36) {
                    val x = (index * 83f + timeMillis * 0.23f) % (width + 80f) - 40f
                    val y = (index * 127f + timeMillis * 0.72f) % (height + 80f) - 40f
                    paint.color = Color.argb(102 + index % 5 * 16, 113, 192, 255)
                    canvas.drawLine(x, y, x - 11f * scale, y + 34f * scale, paint)
                }
            }
            BattleWeatherVisual.SNOW -> {
                paint.style = Paint.Style.FILL
                for (index in 0 until 30) {
                    val drift = cos(timeMillis * 0.0012f + index) * 32f * scale
                    val x = ((index * 97f + timeMillis * 0.11f + drift) % (width + 40f)) - 20f
                    val y = (index * 139f + timeMillis * 0.19f) % (height + 60f) - 30f
                    paint.color = Color.argb(148 + index % 4 * 22, 229, 248, 255)
                    canvas.drawCircle(x, y, (2.5f + index % 3) * scale, paint)
                }
            }
            BattleWeatherVisual.SANDSTORM -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                for (index in 0 until 34) {
                    val x = (index * 79f + timeMillis * 0.19f) % (width + 100f) - 50f
                    val y = height * 0.28f + (index * 61f % (height * 0.65f))
                    paint.strokeWidth = (2f + index % 3) * scale
                    paint.color = Color.argb(80 + index % 5 * 20, 242, 197, 125)
                    canvas.drawLine(x, y, x + (18f + index % 5 * 6f) * scale, y - 2f * scale, paint)
                }
            }
            BattleWeatherVisual.SUN -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(24, 255, 164, 65)
                canvas.drawRect(0f, 0f, width, height, paint)
                val centerX = width * 0.79f
                val centerY = height * 0.19f
                val pulse = 8f * scale * sin(timeMillis * 0.0014f)
                paint.color = Color.argb(44, 255, 199, 92)
                canvas.drawCircle(centerX, centerY, 82f * scale + pulse, paint)
                paint.color = Color.argb(78, 255, 220, 126)
                canvas.drawCircle(centerX, centerY, 36f * scale + pulse * 0.35f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f * scale
                paint.color = Color.argb(52, 255, 215, 126)
                for (index in 0 until 8) {
                    val angle = index * Math.PI / 4.0 + timeMillis * 0.00015
                    canvas.drawLine(
                        centerX + cos(angle).toFloat() * 98f * scale,
                        centerY + sin(angle).toFloat() * 98f * scale,
                        centerX + cos(angle).toFloat() * 143f * scale,
                        centerY + sin(angle).toFloat() * 143f * scale,
                        paint
                    )
                }
            }
            BattleWeatherVisual.STRONG_WINDS -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                for (index in 0 until 18) {
                    val y = height * (0.12f + index % 9 * 0.1f)
                    val x = (index * 137f + timeMillis * 0.16f) % (width + 240f) - 120f
                    val length = (70f + index % 4 * 28f) * scale
                    paint.strokeWidth = (2f + index % 3) * scale
                    paint.color = Color.argb(35 + index % 4 * 13, 215, 232, 255)
                    canvas.drawLine(x, y, x + length, y - 8f * scale, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawFieldOverlay(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        timeMillis: Float,
        overlay: BattleFieldOverlay
    ) {
        val centerX = width * 0.5f
        val centerY = height * 0.54f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        when (overlay) {
            BattleFieldOverlay.GRAVITY -> {
                paint.strokeWidth = 4f * scale
                paint.color = Color.argb(78, 158, 191, 255)
                for (index in 0 until 12) {
                    val x = width * (0.08f + index * 0.076f)
                    val fall = (timeMillis * 0.2f + index * 73f) % (height * 0.68f)
                    val y = height * 0.12f + fall
                    canvas.drawLine(x, y, x, y + 22f * scale, paint)
                    canvas.drawLine(x, y + 22f * scale, x - 7f * scale, y + 13f * scale, paint)
                    canvas.drawLine(x, y + 22f * scale, x + 7f * scale, y + 13f * scale, paint)
                }
            }
            BattleFieldOverlay.TRICK_ROOM -> {
                val angle = timeMillis * 0.012f
                paint.strokeWidth = 5f * scale
                paint.color = Color.argb(76, 204, 137, 255)
                canvas.save()
                canvas.rotate(angle, centerX, centerY)
                for (index in 0..2) {
                    val inset = index * 42f * scale
                    fieldEffectBounds.set(centerX - width * 0.28f + inset, centerY - height * 0.24f + inset, centerX + width * 0.28f - inset, centerY + height * 0.24f - inset)
                    canvas.drawRect(fieldEffectBounds, paint)
                }
                canvas.restore()
            }
            BattleFieldOverlay.MAGIC_ROOM -> {
                paint.strokeWidth = 5f * scale
                paint.color = Color.argb(73, 103, 209, 255)
                for (index in 0..3) {
                    val radius = (88f + index * 54f + sin(timeMillis * 0.002f + index) * 12f) * scale
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
            }
            BattleFieldOverlay.WONDER_ROOM -> {
                paint.strokeWidth = 6f * scale
                paint.color = Color.argb(70, 240, 146, 255)
                for (index in 0..3) {
                    val widthRadius = (170f + index * 100f) * scale
                    val heightRadius = (26f + index * 17f + sin(timeMillis * 0.0015f + index) * 5f) * scale
                    fieldEffectBounds.set(centerX - widthRadius, height * 0.68f - heightRadius, centerX + widthRadius, height * 0.68f + heightRadius)
                    canvas.drawOval(fieldEffectBounds, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawLobby(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val replayLoading = session.isReplayMode() && !session.hasBattleProtocolTranscript()
        paint.alpha = 255
        paint.shader = LinearGradient(0f, 0f, width, height, Color.rgb(7, 17, 34), Color.rgb(20, 46, 58), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        logo?.let {
            source.set(0, 0, it.width, it.height)
            destination.set(72f * scale, 62f * scale, 150f * scale, 140f * scale)
            canvas.drawBitmap(it, source, destination, paint)
        }
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 46f * scale
        paint.color = INK
        canvas.drawText(if (replayLoading) "REPLAY" else "SHOWDOWN!", 178f * scale, 111f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 22f * scale
        paint.color = CYAN
        val formatLabel = ShowdownTeamLibraryQuery.displayFormat(session.matchFormat.id, session.availableMatchFormats())
        canvas.drawText(formatLabel, 180f * scale, 143f * scale, paint)
        val card = RectF(width * 0.12f, height * 0.25f, width * 0.88f, height * 0.77f)
        paint.shader = LinearGradient(card.left, card.top, card.right, card.bottom, Color.rgb(25, 61, 79), Color.rgb(8, 26, 43), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card, 34f * scale, 34f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(180, 102, 211, 231)
        canvas.drawRoundRect(card, 34f * scale, 34f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 54f * scale
        paint.color = INK
        canvas.drawText(
            if (replayLoading) "Loading replay" else "Ready for a battle",
            card.left + 68f * scale,
            card.top + 108f * scale,
            paint
        )
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 28f * scale
        paint.color = MUTED
        canvas.drawText(
            if (replayLoading) "Preparing the battle timeline." else "Use the lower screen to connect, search, or challenge.",
            card.left + 68f * scale,
            card.top + 165f * scale,
            paint
        )
        val statusWidth = card.width() - 136f * scale
        paint.textSize = 34f * scale
        val statusLines = mutableListOf<String>()
        var remainingWords = session.status.split(' ').filter(String::isNotBlank)
        while (remainingWords.isNotEmpty() && statusLines.size < 2) {
            var line = ""
            var consumed = 0
            while (consumed < remainingWords.size) {
                val word = remainingWords[consumed]
                val candidate = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(candidate) > statusWidth) break
                line = candidate
                consumed += 1
            }
            if (consumed == remainingWords.size) {
                statusLines += line
                remainingWords = emptyList()
            } else if (line.isBlank()) {
                statusLines += ellipsizeToWidth(remainingWords.joinToString(" "), statusWidth, paint)
                remainingWords = emptyList()
            } else if (statusLines.isEmpty()) {
                statusLines += line
                remainingWords = remainingWords.drop(consumed)
            } else {
                statusLines += ellipsizeToWidth(
                    (listOf(line) + remainingWords.drop(consumed)).joinToString(" "),
                    statusWidth,
                    paint
                )
                remainingWords = emptyList()
            }
        }
        paint.color = Color.rgb(190, 246, 240)
        statusLines.take(2).forEachIndexed { index, text ->
            canvas.drawText(text, card.left + 68f * scale, card.top + (250f + index * 48f) * scale, paint)
        }
        paint.textSize = 24f * scale
        paint.color = CYAN
        canvas.drawText(
            if (replayLoading) "Playback will begin shortly" else "Menu: Find battle  ·  Challenge player  ·  Team library",
            card.left + 68f * scale,
            card.bottom - 70f * scale,
            paint
        )
    }

    private fun drawTeamPreview(canvas: Canvas, width: Float, height: Float, scale: Float) {
        drawHeader(canvas, width, scale)
        val party = session.opponentPartyDetails()
        val visibleIndices = opponentPreviewIndices(party)
        val pageCount = TeamRosterPager.pageCount(party.size)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(44f, scale, 20f)
        paint.color = INK
        val title = if (pageCount > 1) "Opponent team · ${opponentPreviewPageIndex + 1} / $pageCount" else "Opponent team"
        canvas.drawText(ellipsizeToWidth(title, width * 0.58f, paint), width / 2f, height * 0.195f, paint)
        paint.textAlign = Paint.Align.LEFT
        if (pageCount > 1) drawTeamPreviewPageNavigation(canvas, width, height, scale, pageCount)
        if (party.isEmpty()) {
            val bounds = RectF(width * 0.23f, height * 0.40f, width * 0.77f, height * 0.62f)
            paint.color = Color.argb(148, 8, 23, 38)
            canvas.drawRoundRect(bounds, 24f * scale, 24f * scale, paint)
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.textSize = readableTextSize(30f, scale, 15f)
            paint.color = MUTED
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Waiting for the opponent's team…", width / 2f, bounds.centerY() + 10f * scale, paint)
            paint.textAlign = Paint.Align.LEFT
            return
        }
        val slots = BattleTeamPreviewLayout.slots(width, height, visibleIndices.size)
        visibleIndices.forEachIndexed { visibleIndex, teamIndex ->
            val card = slots[visibleIndex]
            val details = party[teamIndex]
            paint.shader = LinearGradient(
                card.left,
                card.top,
                card.right,
                card.bottom,
                Color.argb(218, 20, 58, 78),
                Color.argb(218, 5, 24, 41),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(card.left, card.top, card.right, card.bottom), 24f * scale, 24f * scale, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * scale
            paint.color = Color.argb(205, 86, 215, 231)
            canvas.drawRoundRect(RectF(card.left, card.top, card.right, card.bottom), 24f * scale, 24f * scale, paint)
            paint.style = Paint.Style.FILL
            val spriteBounds = RectF(
                card.left + 22f * scale,
                card.top + 26f * scale,
                card.left + 206f * scale,
                card.bottom - 26f * scale
            )
            previewSprites[teamIndex]?.draw(
                canvas,
                spriteBounds,
                SystemClock.elapsedRealtime(),
                animate = !animationsPaused
            ) ?: drawPartyBall(
                canvas,
                spriteBounds.centerX() - 42f * scale,
                spriteBounds.centerY() - 42f * scale,
                84f * scale,
                PartyBallState.READY
            )
            val textLeft = card.left + 230f * scale
            val textRight = card.right - 22f * scale
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(34f, scale, 17f)
            paint.color = INK
            canvas.drawText(
                ellipsizeToWidth(BattleSession.displayPokemonName(details.name, details.species), textRight - textLeft, paint),
                textLeft,
                card.top + 92f * scale,
                paint
            )
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.textSize = readableTextSize(24f, scale, 14f)
            paint.color = MUTED
            canvas.drawText("Lv.${details.level}${details.gender}", textLeft, card.top + 136f * scale, paint)
            drawTeamPreviewTypes(canvas, details.types, textLeft, card.top + 174f * scale, textRight, scale)
        }
    }

    private fun drawTeamPreviewPageNavigation(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        pageCount: Int
    ) {
        val controls = BattleTeamPreviewLayout.navigationSlots(width, height)
        controls.forEachIndexed { index, slot ->
            val previous = index == 0
            val enabled = if (previous) opponentPreviewPageIndex > 0 else opponentPreviewPageIndex + 1 < pageCount
            val bounds = RectF(slot.left, slot.top, slot.right, slot.bottom)
            paint.color = Color.argb(if (enabled) 150 else 80, 8, 30, 48)
            canvas.drawRoundRect(bounds, bounds.height() / 2f, bounds.height() / 2f, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(40f, scale, 18f)
            paint.color = if (enabled) INK else MUTED
            canvas.drawText(
                if (previous) "‹" else "›",
                bounds.centerX(),
                bounds.centerY() - (paint.ascent() + paint.descent()) / 2f,
                paint
            )
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTeamPreviewTypes(
        canvas: Canvas,
        types: List<String>,
        left: Float,
        top: Float,
        right: Float,
        scale: Float
    ) {
        var badgeLeft = left
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(19f, scale, 11f)
        types.take(2).forEach { type ->
            val badgeWidth = maxOf(78f * scale, paint.measureText(type) + 30f * scale)
            if (badgeLeft + badgeWidth > right) return@forEach
            paint.color = typeColor(type)
            canvas.drawRoundRect(RectF(badgeLeft, top, badgeLeft + badgeWidth, top + 38f * scale), 13f * scale, 13f * scale, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(type, badgeLeft + badgeWidth / 2f, top + 26f * scale, paint)
            paint.textAlign = Paint.Align.LEFT
            badgeLeft += badgeWidth + 8f * scale
        }
    }

    private fun fieldCombatants(combatants: List<BattleSession.ActiveCombatant>, player: Boolean) =
        if (player) combatants else combatants.asReversed()

    private fun primaryBattleSlot(player: Boolean): String {
        val playerSide = session.battlePlayerSlot()
        val side = if (player) playerSide else if (playerSide == "p1") "p2" else "p1"
        return "${side}a"
    }

    private fun statusCardSide(player: Boolean, combatant: BattleSession.ActiveCombatant?) =
        combatant?.slot?.take(2) ?: primaryBattleSlot(player).take(2)

    private fun drawCombatant(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        scale: Float,
        slot: String,
        condition: String,
        summonAtNanos: Long,
        nowNanos: Long,
        sprite: ShowdownSpriteCache.SpriteAsset?,
        showdownPlacement: Boolean = false
    ) {
        val faintProgress = faintProgress(slot, condition, nowNanos)
        if (faintProgress >= 1f) return
        sprite ?: return
        drawSummonBall(canvas, centerX, centerY, scale, summonAtNanos, nowNanos)
        val summonAlpha = BattleSceneTiming.summonSpriteAlpha(summonAtNanos, nowNanos)
        if (summonAlpha <= 0f) return
        val summonScale = BattleSceneTiming.summonSpriteScale(summonAtNanos, nowNanos)
        val spriteWidth = 290f * scale * summonScale
        val spriteHeight = 300f * scale * summonScale
        val summonOffset = BattleSceneTiming.summonVerticalOffset(summonAtNanos, nowNanos) * scale
        val easedFaint = faintProgress * faintProgress
        val imageCenterY = if (showdownPlacement) centerY + summonOffset else centerY + summonOffset - spriteHeight * 0.18f
        sprite.draw(
            canvas,
            RectF(
                centerX - spriteWidth / 2f,
                imageCenterY - spriteHeight / 2f - 240f * scale * easedFaint,
                centerX + spriteWidth / 2f,
                imageCenterY + spriteHeight / 2f - 240f * scale * easedFaint
            ),
            SystemClock.elapsedRealtime(),
            alpha = ((1f - easedFaint) * summonAlpha * 255f).toInt(),
            animate = !animationsPaused
        )
    }

    private fun drawSummonBall(canvas: Canvas, centerX: Float, centerY: Float, scale: Float, summonAtNanos: Long, nowNanos: Long) {
        val alpha = BattleSceneTiming.summonBallAlpha(summonAtNanos, nowNanos)
        val ball = effectAssets["pokeball.png"] ?: return
        if (alpha <= 0f) return
        val progress = BattleSceneTiming.summonProgress(summonAtNanos, nowNanos)
        val size = 78f * scale * (0.70f + progress.coerceAtMost(0.3f))
        val vertical = centerY - 76f * scale + BattleSceneTiming.summonVerticalOffset(summonAtNanos, nowNanos) * scale
        source.set(0, 0, ball.width, ball.height)
        destination.set(centerX - size / 2f, vertical - size / 2f, centerX + size / 2f, vertical + size / 2f)
        paint.alpha = (alpha * 255f).toInt()
        canvas.drawBitmap(ball, source, destination, paint)
        paint.alpha = 255
    }

    private fun faintProgress(slot: String, condition: String, nowNanos: Long) = BattleSceneTiming.faintProgress(
        slot,
        condition,
        session.latestFaintedSlot,
        session.latestFaintAtNanos,
        nowNanos
    )

    private fun statusCardAlpha(pokemon: String, condition: String, nowNanos: Long) = BattleSceneTiming.statusCardAlpha(
        pokemon,
        condition,
        session.latestFaintedPokemon,
        session.latestFaintAtNanos,
        nowNanos
    )

    private fun isFainting(slot: String, condition: String) =
        condition.contains("FNT", true) && faintProgress(slot, condition, System.nanoTime()) < 1f

    private fun drawHeader(canvas: Canvas, width: Float, scale: Float) {
        val padding = 30f * scale
        val innerInset = 14f * scale
        val iconSize = 60f * scale
        val iconGap = 16f * scale
        val headerHeight = 82f * scale
        val title = "SHOWDOWN!"
        val format = session.format.takeIf(String::isNotBlank)
            ?: ShowdownTeamLibraryQuery.displayFormat(session.matchFormat.id, session.availableMatchFormats())
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = 34f * scale
        val titleLeft = padding + innerInset + iconSize + iconGap
        val titleWidth = paint.measureText(title)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 18f * scale
        val formatWidth = paint.measureText(format)
        val headerRight = (titleLeft + maxOf(titleWidth, formatWidth) + innerInset).coerceAtMost(width - padding)
        val formatAvailableWidth = (headerRight - titleLeft - innerInset).coerceAtLeast(0f)
        val displayedFormat = ellipsizeToWidth(format, formatAvailableWidth, paint)
        paint.color = Color.argb(200, 5, 12, 29)
        canvas.drawRoundRect(RectF(padding, padding, headerRight, padding + headerHeight), 22f * scale, 22f * scale, paint)
        logo?.let {
            source.set(0, 0, it.width, it.height)
            destination.set(padding + innerInset, padding + 11f * scale, padding + innerInset + iconSize, padding + 71f * scale)
            canvas.drawBitmap(it, source, destination, paint)
        }
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = 34f * scale
        paint.color = INK
        canvas.drawText(title, titleLeft, padding + 43f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 18f * scale
        paint.color = CYAN
        canvas.drawText(displayedFormat, titleLeft + scale, padding + 68f * scale, paint)
    }

    private fun drawBattleClock(canvas: Canvas, width: Float, scale: Float) {
        val seconds = session.battleClockSeconds() ?: return
        val label = BattleClockPresentation.timeLabel(seconds)
        val clockTextSize = readableTextSize(30f, scale, 15f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = clockTextSize
        val pillHeight = 66f * scale
        val pillWidth = maxOf(150f * scale, paint.measureText(label) + 72f * scale)
        val centerX = width / 2f
        val top = 30f * scale
        val bounds = RectF(centerX - pillWidth / 2f, top, centerX + pillWidth / 2f, top + pillHeight)
        val color = when (BattleClockPresentation.urgency(seconds)) {
            BattleClockUrgency.NORMAL -> Color.rgb(62, 186, 211)
            BattleClockUrgency.WARNING -> Color.rgb(244, 189, 61)
            BattleClockUrgency.CRITICAL -> Color.rgb(245, 91, 86)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(218, 5, 17, 29)
        canvas.drawRoundRect(bounds, 24f * scale, 24f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(235, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(bounds, 24f * scale, 24f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = INK
        canvas.drawText(label, centerX, top + 44f * scale, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawInspectSheet(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val playerSide = inspectedPlayer ?: return
        val details = inspectedSlot?.let { displayedDetailsForActiveCombatant(playerSide, it) }
            ?: if (playerSide) displayedPlayerDetails() else displayedOpponentDetails()
        val visibleCombatants = inspectedSlot?.let { slot ->
            (if (playerSide) displayedPlayerCombatants() else displayedOpponentCombatants())
                .filter { it.slot == slot }
        } ?: (if (playerSide) displayedPlayerCombatants() else displayedOpponentCombatants())
        val activeEffects = visibleCombatants.flatMap { combatant ->
            val effects = combatant.volatileEffects + combatant.turnEffects + combatant.moveEffects
            effects.map { effect -> "${BattleSession.displayPokemonName(combatant.name, combatant.species)}: $effect" }
        }
            .distinct()
        val bounds = if (playerSide) {
            RectF(width * 0.025f, height * 0.14f, width * 0.49f, height * 0.85f)
        } else {
            RectF(width * 0.51f, height * 0.16f, width * 0.975f, height * 0.87f)
        }
        paint.color = Color.argb(246, 7, 14, 32)
        canvas.drawRoundRect(bounds, 26f * scale, 26f * scale, paint)
        paint.color = if (playerSide) CYAN else MAGENTA
        canvas.drawRoundRect(RectF(bounds.left, bounds.top, bounds.left + 8f * scale, bounds.bottom), 5f * scale, 5f * scale, paint)
        val left = bounds.left + 34f * scale
        val right = bounds.right - 32f * scale
        var row = bounds.top + 70f * scale
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(60f, scale, 24f)
        paint.color = INK
        canvas.drawText(
            ellipsizeToWidth(BattleSession.displayPokemonName(details.name, details.species), right - left - 168f * scale, paint),
            left,
            row,
            paint
        )
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = readableTextSize(42f, scale, 18f)
        paint.color = if (playerSide) CYAN else MAGENTA
        canvas.drawText("Lv.${details.level}${details.gender}", right, row, paint)
        paint.textAlign = Paint.Align.LEFT
        row += 50f * scale
        var badgeLeft = left
        details.types.forEach { type ->
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(30f, scale, 16f)
            val badgeHeight = 52f * scale
            val badgeWidth = maxOf((type.length * 20f + 58f) * scale, paint.measureText(type) + 44f * scale)
            paint.color = typeColor(type)
            canvas.drawRoundRect(RectF(badgeLeft, row, badgeLeft + badgeWidth, row + badgeHeight), 18f * scale, 18f * scale, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            canvas.drawText(type, badgeLeft + badgeWidth / 2f, row + (badgeHeight - paint.ascent() - paint.descent()) / 2f, paint)
            paint.textAlign = Paint.Align.LEFT
            badgeLeft += badgeWidth + 12f * scale
        }
        row += 100f * scale
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = readableTextSize(42f, scale, 18f)
        paint.color = MUTED
        canvas.drawText("HP", left, row, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = INK
        canvas.drawText("${details.hp}  ${details.condition}", right, row, paint)
        paint.textAlign = Paint.Align.LEFT
        row += 56f * scale
        if (details.possibleAbilities.isEmpty()) {
            paint.color = MUTED
            canvas.drawText("Ability", left, row, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = INK
            canvas.drawText(ellipsizeToWidth(details.ability, right - left - 150f * scale, paint), right, row, paint)
            paint.textAlign = Paint.Align.LEFT
            row += 56f * scale
        } else {
            paint.textSize = readableTextSize(30f, scale, 16f)
            paint.color = INK
            val abilityText = "Possible abilities: ${details.possibleAbilities.joinToString(" · ")}"
            val abilityLines = wrapTextToWidth(abilityText, right - left, paint)
            abilityLines.forEach { line ->
                canvas.drawText(ellipsizeToWidth(line, right - left, paint), left, row, paint)
                row += 36f * scale
            }
            row += maxOf(20f * scale, 56f * scale - abilityLines.size * 36f * scale)
        }
        paint.textSize = readableTextSize(42f, scale, 18f)
        paint.color = MUTED
        canvas.drawText("Item", left, row, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = INK
        val itemName = BattleItemPresentation.visibleName(details.item)
        val itemPath = BattleItemPresentation.iconPath(details.item)
        val itemIconSize = itemPath?.let { minOf(42f * scale, bounds.height() * 0.12f) } ?: 0f
        val itemTextRight = if (itemIconSize > 0f) right - itemIconSize - 12f * scale else right
        canvas.drawText(
            ellipsizeToWidth(itemName ?: "Unknown item", itemTextRight - left, paint),
            itemTextRight,
            row,
            paint
        )
        itemPath?.let { path ->
            itemSprites[path]?.draw(
                canvas,
                RectF(
                    right - itemIconSize,
                    row - itemIconSize * 0.78f,
                    right,
                    row + itemIconSize * 0.22f
                ),
                SystemClock.elapsedRealtime(),
                animate = !animationsPaused
            )
        }
        paint.textAlign = Paint.Align.LEFT
        row += 62f * scale
        paint.textSize = readableTextSize(30f, scale, 16f)
        paint.color = MUTED
        details.stats.lineSequence().filter(String::isNotBlank).forEach { statLine ->
            canvas.drawText(ellipsizeToWidth(statLine, right - left, paint), left, row, paint)
            row += 38f * scale
        }
        paint.textSize = readableTextSize(36f, scale, 16f)
        if (activeEffects.isNotEmpty()) {
            row += 42f * scale
            paint.color = MUTED
            canvas.drawText("Effects", left, row, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = INK
            val effectText = activeEffects.take(3).joinToString(" · ").let { text ->
                if (activeEffects.size > 3) "$text +${activeEffects.size - 3}" else text
            }
            canvas.drawText(ellipsizeToWidth(effectText, right - left - 150f * scale, paint), right, row, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        row += 50f * scale
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(40f, scale, 18f)
        paint.color = if (playerSide) CYAN else MAGENTA
        canvas.drawText("Known moves", left, row, paint)
        row += 44f * scale
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = readableTextSize(38f, scale, 17f)
        details.moves.take(4).forEach { move ->
            paint.color = INK
            canvas.drawText("• $move", left, row, paint)
            row += paint.textSize + 9f * scale
        }
        paint.textSize = readableTextSize(31f, scale, 14f)
        paint.color = MUTED
        canvas.drawText("Tap this Pokémon or outside the sheet to dismiss", left, bounds.bottom - 22f * scale, paint)
    }

    private fun typeColor(type: String) = when (type) {
        "FIRE" -> Color.rgb(239, 100, 76)
        "WATER" -> Color.rgb(74, 152, 244)
        "GRASS" -> Color.rgb(85, 177, 105)
        "ELECTRIC" -> Color.rgb(222, 180, 52)
        "DARK" -> Color.rgb(103, 78, 118)
        "FAIRY" -> Color.rgb(219, 116, 178)
        "POISON" -> Color.rgb(148, 88, 170)
        "DRAGON" -> Color.rgb(92, 102, 215)
        "GROUND" -> Color.rgb(195, 145, 82)
        "FLYING" -> Color.rgb(117, 157, 220)
        "GHOST" -> Color.rgb(100, 83, 152)
        "STEEL" -> Color.rgb(125, 145, 163)
        else -> Color.rgb(110, 137, 168)
    }

    private fun drawStatusCard(
        canvas: Canvas,
        bounds: RectF,
        details: BattleSession.PokemonDetails,
        hp: String,
        scale: Float,
        alpha: Float,
        party: List<BattleSession.PokemonDetails>,
        ladderBadges: List<ShowdownLadderBadge> = emptyList()
    ) {
        drawBattleStatusCard(
            canvas,
            bounds,
            BattleCardContent.from(details, hp),
            scale,
            alpha,
            BattleCardLayout.compactFor(1),
            party,
            ladderBadges
        )
    }

    private fun drawActiveStatusCards(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        player: Boolean,
        combatants: List<BattleSession.ActiveCombatant>,
        switchOutVisual: BattleSession.SwitchOutVisual? = null,
        detailsBySlot: Map<String, BattleSession.PokemonDetails> = emptyMap(),
        partyDetails: List<BattleSession.PokemonDetails>,
        partyDetailsBySlot: Map<String, List<BattleSession.PokemonDetails>> = emptyMap(),
        ladderBadgesBySide: Map<String, List<ShowdownLadderBadge>> = emptyMap()
    ) {
        val layout = BattleCardLayout.compactFor(combatants.size)
        val nowNanos = System.nanoTime()
        val shownBadgeSides = mutableSetOf<String>()
        combatants.forEachIndexed { index, combatant ->
            val alpha = statusCardAlpha(combatant.name, combatant.condition, nowNanos) *
                BattleSceneTiming.summonStatusCardAlpha(combatant.entryAtNanos, nowNanos)
            if (alpha > 0f) {
                val side = combatant.slot.take(2)
                val badges = ShowdownLadderBadgePresentation.forStatusCard(
                    side,
                    shownBadgeSides,
                    ladderBadgesBySide
                )
                drawCompactStatusCard(
                    canvas,
                    BattleCardLayout.compactBoundsFor(width, height, player, index, combatants.size).toRectF(),
                    BattleCardContent.from(
                        combatant,
                        switchOutVisual
                            ?.takeIf { it.playerSide == player && it.combatant.slot == combatant.slot }
                            ?.details
                            ?.item
                            ?: detailsBySlot[combatant.slot]?.item
                            ?: session.detailsForActiveCombatant(player, combatant.slot)?.item.orEmpty()
                    ),
                    scale,
                    alpha,
                    layout,
                    partyDetailsBySlot[combatant.slot] ?: partyDetails,
                    badges
                )
            }
        }
    }

    private fun drawCompactStatusCard(
        canvas: Canvas,
        bounds: RectF,
        content: BattleCardContent,
        scale: Float,
        alpha: Float,
        layout: CompactBattleCardLayout,
        party: List<BattleSession.PokemonDetails>,
        ladderBadges: List<ShowdownLadderBadge>
    ) {
        drawBattleStatusCard(
            canvas,
            bounds,
            content,
            scale,
            alpha,
            layout,
            party,
            ladderBadges
        )
    }

    private fun drawBattleStatusCard(
        canvas: Canvas,
        bounds: RectF,
        content: BattleCardContent,
        scale: Float,
        alpha: Float,
        layout: CompactBattleCardLayout,
        party: List<BattleSession.PokemonDetails>,
        ladderBadges: List<ShowdownLadderBadge>
    ) {
        val layer = canvas.saveLayerAlpha(bounds, (alpha * 255f).toInt())
        val left = bounds.left + 20f * scale
        val right = bounds.right - 20f * scale
        drawBattleStatusCardSurface(canvas, bounds, scale)
        drawBattleStatusCardContent(canvas, bounds, content, left, right, scale, layout, party, ladderBadges)
        canvas.restoreToCount(layer)
    }

    private fun drawBattleStatusCardContent(
        canvas: Canvas,
        bounds: RectF,
        content: BattleCardContent,
        textLeft: Float,
        textRight: Float,
        scale: Float,
        layout: CompactBattleCardLayout,
        party: List<BattleSession.PokemonDetails>,
        ladderBadges: List<ShowdownLadderBadge>
    ) {
        val height = bounds.height()
        val contentLayout = layout.content
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = readableTextSize(height * 0.19f, scale, 9.5f)
        val levelWidth = paint.measureText(content.levelLabel)
        val itemPath = BattleItemPresentation.iconPath(content.item)
        val itemIconSize = itemPath?.let { minOf(30f * scale, height * 0.30f) } ?: 0f
        val itemGap = if (itemPath == null) 0f else 10f * scale
        paint.color = Color.rgb(232, 232, 232)
        canvas.drawText(content.levelLabel, textRight, bounds.top + height * contentLayout.titleBaselineFraction, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = readableTextSize(height * 0.27f, scale, 10.5f)
        val titleWidth = (textRight - textLeft - levelWidth - itemIconSize - itemGap - 16f * scale).coerceAtLeast(0f)
        paint.color = INK
        val titleBaseline = bounds.top + height * contentLayout.titleBaselineFraction
        val titleMeasuredWidth = paint.measureText(content.title)
        val titleHorizontalScale = if (titleMeasuredWidth > titleWidth && titleMeasuredWidth > 0f) {
            titleWidth / titleMeasuredWidth
        } else {
            1f
        }
        canvas.save()
        canvas.scale(titleHorizontalScale, 1f, textLeft, titleBaseline)
        canvas.drawText(content.title, textLeft, titleBaseline, paint)
        canvas.restore()
        itemPath?.let { path ->
            itemSprites[path]?.draw(
                canvas,
                RectF(
                    textRight - levelWidth - itemGap - itemIconSize,
                    titleBaseline - itemIconSize * 0.82f,
                    textRight - levelWidth - itemGap,
                    titleBaseline + itemIconSize * 0.18f
                ),
                SystemClock.elapsedRealtime(),
                animate = !animationsPaused
            )
        }
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = readableTextSize(height * 0.17f, scale, 9.5f)
        paint.color = Color.rgb(238, 238, 238)
        canvas.drawText(content.hpLabel, textRight, bounds.top + height * contentLayout.hpBaselineFraction, paint)
        paint.textAlign = Paint.Align.LEFT
        val track = RectF(
            textLeft,
            bounds.top + height * contentLayout.barTopFraction,
            textRight,
            bounds.top + height * contentLayout.barBottomFraction
        )
        drawHealthBar(canvas, track, content.fraction, scale, height * 0.07f)
        val ballSize = BattleCardLayout.partyIndicatorSize(height)
        val ballGap = maxOf(ballSize * 0.12f, 2f * scale)
        val ballStart = textRight - ballSize * 6f - ballGap * 5f
        val ballTop = BattleCardLayout.partyIndicatorTop(bounds.bottom, ballSize, scale)
        val badgeSize = ballSize * 1.15f
        ShowdownLadderBadgePresentation.visible(ladderBadges).forEachIndexed { index, badge ->
            val path = badge.assetPath
            ladderBadgeSprites[path]?.draw(
                canvas,
                RectF(
                    textLeft + index * (badgeSize + ballGap),
                    ballTop - badgeSize * 0.08f,
                    textLeft + index * (badgeSize + ballGap) + badgeSize,
                    ballTop - badgeSize * 0.08f + badgeSize
                ),
                SystemClock.elapsedRealtime(),
                animate = !animationsPaused
            )
        }
        drawPartyIndicators(canvas, party, ballStart, ballTop, ballSize, ballGap)
    }

    private fun drawPartyIndicators(
        canvas: Canvas,
        party: List<BattleSession.PokemonDetails>,
        start: Float,
        top: Float,
        size: Float,
        gap: Float
    ) {
        repeat(6) { index ->
            val pokemon = party.getOrNull(index)
            val left = start + index * (size + gap)
            val state = when {
                pokemon?.condition?.contains("FNT", true) == true -> PartyBallState.FAINTED
                pokemon != null && pokemon.condition != "READY" -> PartyBallState.STATUSED
                else -> PartyBallState.READY
            }
            drawPartyBall(canvas, left, top, size, state)
        }
    }

    private fun drawPartyBall(canvas: Canvas, left: Float, top: Float, size: Float, state: PartyBallState) {
        paint.alpha = 255
        paint.shader = null
        paint.style = Paint.Style.FILL
        val pixelSize = size.roundToInt().coerceAtLeast(1)
        val key = PartyBallBitmapKey(pixelSize, state)
        val bitmap = partyBallBitmaps[key] ?: Bitmap.createBitmap(
            pixelSize,
            pixelSize,
            Bitmap.Config.ARGB_8888
        ).also { rendered ->
            drawFallbackPartyBall(Canvas(rendered), 0f, 0f, pixelSize.toFloat(), state)
            partyBallBitmaps[key] = rendered
        }
        destination.set(left, top, left + size, top + size)
        canvas.drawBitmap(bitmap, null, destination, partyBallPaint)
    }

    private fun drawFallbackPartyBall(canvas: Canvas, left: Float, top: Float, size: Float, state: PartyBallState) {
        val centerX = left + size / 2f
        val centerY = top + size / 2f
        val radius = size * 0.43f
        val bounds = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        val colors = when (state) {
            PartyBallState.READY -> intArrayOf(Color.rgb(255, 113, 76), Color.rgb(205, 43, 31))
            PartyBallState.STATUSED -> intArrayOf(Color.rgb(255, 230, 92), Color.rgb(190, 135, 22))
            PartyBallState.FAINTED -> intArrayOf(Color.rgb(156, 170, 184), Color.rgb(78, 91, 105))
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.left,
            bounds.bottom,
            Color.rgb(249, 252, 255),
            Color.rgb(188, 204, 216),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.shader = LinearGradient(bounds.left, bounds.top, bounds.left, centerY, colors[0], colors[1], Shader.TileMode.CLAMP)
        canvas.drawArc(bounds, 180f, 180f, true, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1.5f, size * 0.06f)
        paint.color = Color.rgb(176, 192, 205)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(27, 38, 48)
        canvas.drawRoundRect(
            RectF(centerX - radius, centerY - size * 0.045f, centerX + radius, centerY + size * 0.045f),
            size * 0.045f,
            size * 0.045f,
            paint
        )
        paint.color = Color.rgb(218, 229, 237)
        canvas.drawCircle(centerX, centerY, size * 0.13f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, size * 0.035f)
        paint.color = Color.rgb(105, 123, 138)
        canvas.drawCircle(centerX, centerY, size * 0.13f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(145, 255, 255, 255)
        canvas.drawCircle(centerX - size * 0.15f, centerY - size * 0.18f, size * 0.07f, paint)
    }

    private fun drawBattleStatusCardSurface(canvas: Canvas, bounds: RectF, scale: Float) {
        val radius = bounds.height() * 0.15f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(232, 16, 20, 26)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.rgb(104, 111, 120)
        canvas.drawRoundRect(
            RectF(bounds.left + scale, bounds.top + scale, bounds.right - scale, bounds.bottom - scale),
            radius,
            radius,
            paint
        )
        paint.style = Paint.Style.FILL
    }

    private fun drawHealthBar(canvas: Canvas, track: RectF, fraction: Float, scale: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(track.left, track.top, track.left, track.bottom, Color.rgb(55, 63, 72), Color.rgb(12, 17, 22), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(track, radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(150, 229, 238, 245)
        canvas.drawRoundRect(RectF(track.left + scale, track.top + scale, track.right - scale, track.bottom - scale), radius * 0.86f, radius * 0.86f, paint)
        paint.style = Paint.Style.FILL
        val inner = RectF(track.left + 3f * scale, track.top + 3f * scale, track.right - 3f * scale, track.bottom - 3f * scale)
        val colors = healthColors(fraction)
        val hpRight = inner.left + inner.width() * fraction
        if (hpRight > inner.left) {
            val fill = RectF(inner.left, inner.top, hpRight, inner.bottom)
            paint.shader = LinearGradient(
                fill.left,
                fill.top,
                fill.left,
                fill.bottom,
                intArrayOf(colors.highlight, colors.fill, colors.shadow),
                floatArrayOf(0f, 0.54f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(fill, radius * 0.65f, radius * 0.65f, paint)
            paint.shader = null
        }
    }

    private fun readableTextSize(designPixels: Float, scale: Float, minimumSp: Float = 12f): Float = maxOf(
        designPixels * scale,
        minimumSp * resources.displayMetrics.density * resources.configuration.fontScale
    )

    private fun healthColors(fraction: Float) = when {
        fraction > 0.5f -> HpColors(
            Color.rgb(0, 187, 81),
            Color.rgb(0, 221, 96),
            Color.rgb(0, 119, 52)
        )
        fraction > 0.2f -> HpColors(
            Color.rgb(245, 213, 56),
            Color.rgb(248, 227, 121),
            Color.rgb(190, 159, 10)
        )
        else -> HpColors(
            Color.rgb(238, 73, 40),
            Color.rgb(243, 127, 103),
            Color.rgb(163, 38, 13)
        )
    }

    private fun drawBattleFeed(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        frame: BattleFeedFrame?
    ) {
        val nowMillis = SystemClock.elapsedRealtime()
        battleFeedBounds.setEmpty()
        frame ?: return
        val alpha = frame.alpha
        val glassAlpha = alpha.pow(0.62f)
        val textAlpha = alpha.pow(0.25f)
        val outlineAlpha = alpha.pow(0.18f)
        val sideGap = maxOf(48f * scale, width * 0.025f)
        val playerCardRight = if (session.isSinglesBattle()) {
            ShowdownBattleLayout.singlePlayerCardRight(width, scale)
        } else {
            width * 0.315f
        }
        val playerCombatants = session.playerActiveCombatants()
        val centeredPlayerSlot = BattleCombatantLayout.centeredSlot(session.isTriplesCentered(), playerCombatants)
        val playerSpriteRight = if (session.isSinglesBattle()) {
            ShowdownBattleLayout.x(width, ShowdownBattleLayout.PLAYER_X) +
                145f * scale * ShowdownBattleLayout.PLAYER_SCALE
        } else {
            playerCombatants.mapIndexedNotNull { index, combatant ->
                if (combatant.condition.contains("FNT", true)) return@mapIndexedNotNull null
                BattleCombatantLayout.x(
                    width,
                    true,
                    index,
                    playerCombatants.size,
                    centeredPlayerSlot,
                    combatant.slot
                ) + 145f * scale * 1.02f
            }.maxOrNull() ?: playerCardRight
        }
        val settledLeft = maxOf(width * 0.33f, playerCardRight + sideGap, playerSpriteRight + sideGap)
        val left = settledLeft
        val right = width * 0.97f
        val bottom = min(height * 0.945f, height * 0.98f - 32f * scale)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.typeface = battleFeedTypeface
        val textSize = readableTextSize(36f, scale, 11f)
        paint.textSize = textSize
        val maxWidth = right - left - 48f * scale
        val lineHeight = maxOf(42f * scale, paint.descent() - paint.ascent() + 8f * scale)
        val padding = 22f * scale
        val markup = session.battleFeedMarkupFor(frame.visibleText)
        if (cachedBattleFeedText != frame.text ||
            cachedBattleFeedVisibleText != frame.visibleText ||
            cachedBattleFeedMarkup != markup ||
            cachedBattleFeedWidth != maxWidth ||
            cachedBattleFeedTextSize != textSize
        ) {
            cachedBattleFeedText = frame.text
            cachedBattleFeedVisibleText = frame.visibleText
            cachedBattleFeedMarkup = markup
            cachedBattleFeedWidth = maxWidth
            cachedBattleFeedTextSize = textSize
            cachedBattleFeedFullLines = BattleFeedText.wrap(frame.text, maxWidth, 2) { text ->
                val regularWidth = measureBattleFeedText(text, false)
                val boldWidth = measureBattleFeedText(text, true)
                maxOf(regularWidth, boldWidth)
            }
                .ifEmpty { listOf("") }
            cachedBattleFeedLines = BattleFeedText.wrapShowdownMarkup(
                markup,
                maxWidth,
                cachedBattleFeedFullLines.size,
                ::measureBattleFeedText
            ).ifEmpty { listOf(emptyList()) }
        }
        paint.typeface = battleFeedTypeface
        val fullLines = cachedBattleFeedFullLines
        val lines = cachedBattleFeedLines
        val boundsHeight = fullLines.size * lineHeight + padding * 2f
        val top = (bottom - boundsHeight).coerceAtLeast(height * 0.70f)
        val bounds = RectF(left, top, right, bottom)
        battleFeedBounds.set(bounds)
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            Color.argb((78f * glassAlpha).toInt(), 21, 42, 57),
            Color.argb((32f * glassAlpha).toInt(), 52, 79, 94),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.argb((132f * glassAlpha).toInt(), 183, 229, 235)
        canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.FILL
        val viewportHeight = (bounds.height() - padding * 2f).coerceAtLeast(lineHeight)
        val contentHeight = fullLines.size * lineHeight
        val contentTop = bounds.top + padding + (viewportHeight - contentHeight).coerceAtLeast(0f) / 2f
        canvas.save()
        canvas.clipRect(bounds)
        lines.forEachIndexed { index, line ->
            val baseline = contentTop + index * lineHeight + (lineHeight - paint.ascent() - paint.descent()) / 2f
            drawBattleFeedLine(canvas, line, left + 24f * scale, baseline, scale, outlineAlpha, textAlpha)
        }
        canvas.restore()
        if (battleFeedPresentation.needsAnimation(nowMillis)) postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
    }

    private fun measureBattleFeedText(value: String, emphasized: Boolean): Float {
        paint.typeface = if (emphasized) battleFeedBoldTypeface else battleFeedTypeface
        return paint.measureText(value)
    }

    private fun drawBattleFeedLine(
        canvas: Canvas,
        runs: List<BattleFeedText.StyledRun>,
        startX: Float,
        baseline: Float,
        scale: Float,
        outlineAlpha: Float,
        textAlpha: Float
    ) {
        var x = startX
        runs.forEach { run ->
            paint.typeface = if (run.emphasized) battleFeedBoldTypeface else battleFeedTypeface
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.25f * scale
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = Color.argb((220f * outlineAlpha).toInt(), 3, 12, 18)
            canvas.drawText(run.text, x, baseline, paint)
            x += paint.measureText(run.text)
        }
        x = startX
        runs.forEach { run ->
            paint.typeface = if (run.emphasized) battleFeedBoldTypeface else battleFeedTypeface
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((255f * textAlpha).toInt(), 255, 255, 255)
            canvas.drawText(run.text, x, baseline, paint)
            x += paint.measureText(run.text)
        }
        paint.typeface = battleFeedTypeface
        paint.style = Paint.Style.FILL
    }

    private fun ellipsize(value: String, maximum: Int) = if (value.length <= maximum) value else "${value.take(maximum - 1)}…"

    private fun wrapTextToWidth(value: String, maximumWidth: Float, textPaint: Paint): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        value.trim().split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (current.isBlank() || textPaint.measureText(candidate) <= maximumWidth) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun ellipsizeToWidth(value: String, maximumWidth: Float, textPaint: Paint): String {
        if (textPaint.measureText(value) <= maximumWidth) return value
        var end = value.length
        while (end > 1) {
            val candidate = "${value.take(end - 1)}…"
            if (textPaint.measureText(candidate) <= maximumWidth) return candidate
            end -= 1
        }
        return "…"
    }

    private fun BattleCardBounds.toRectF() = RectF(left, top, right, bottom)

    private companion object {
        val SHOWDOWN_EFFECTS = listOf("pokeball.png")
        const val TEAM_PREVIEW_STATIC_FALLBACK_DELAY_MILLIS = 900L
        const val ACCESSIBLE_PLAYER_ID = 1
        const val ACCESSIBLE_OPPONENT_ID = 2
        const val ACCESSIBLE_BATTLE_LOG_ID = 3
        const val ACCESSIBLE_TEAM_PREVIEW_BASE = 100
        const val ACCESSIBLE_TEAM_PREVIEW_PREVIOUS_ID = 124
        const val ACCESSIBLE_TEAM_PREVIEW_NEXT_ID = 125
        const val ACCESSIBLE_INSPECT_DETAILS_ID = 200
        const val ACCESSIBLE_PLAYER_PARTY_BASE = 300
        const val ACCESSIBLE_OPPONENT_PARTY_BASE = 400
        const val INK = 0xFFF0F7FF.toInt()
        const val CYAN = 0xFF4AE7FF.toInt()
        const val MAGENTA = 0xFFFF49B0.toInt()
        const val MUTED = 0xFFBBD1EA.toInt()
    }

    private enum class PartyBallState {
        READY,
        STATUSED,
        FAINTED
    }
}
