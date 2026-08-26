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
import dev.adrian.showdown.R
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
    private var requestedPlayerSprite: BattleSpriteRequest? = null
    private var requestedOpponentSprite: BattleSpriteRequest? = null
    private val itemSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val requestedItemSprites = mutableSetOf<String>()
    private var requestedBackdrop = ""
    private var resourcesRequested = false
    private val effectAssets = mutableMapOf<String, Bitmap>()
    private val requestedEffects = mutableSetOf<String>()
    private val partyBallBitmaps = mutableMapOf<PartyBallBitmapKey, Bitmap>()
    private val partyBallPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var inspectedPlayer: Boolean? = null
    private var inspectedSlot: String? = null
    private val playerInspectBounds = RectF()
    private val opponentInspectBounds = RectF()
    private val battleFeedBounds = RectF()
    private val battleFeedPresentation = BattleFeedPresentation()
    private var cachedBattleFeedText: String? = null
    private var cachedBattleFeedVisibleText: String? = null
    private var cachedBattleFeedWidth = -1f
    private var cachedBattleFeedTextSize = -1f
    private var cachedBattleFeedFullLines = emptyList<String>()
    private var cachedBattleFeedLines = emptyList<String>()
    private var battleFeedTouchDownY = 0f
    private var battleFeedTouchLastY = 0f
    private var battleFeedTouchActive = false
    private var battleFeedTouchMoved = false
    private var animationsPaused = false
    private var playbackSpeed = 1f
    private var lightweightMoveStartedAtNanos = 0L
    private var lightweightMoveAnimationEnabled = true
    private var lightweightMoveActorPlayer = true
    private var lightweightMoveTargetPlayer: Boolean? = null
    private var lightweightImpactAtNanos = 0L
    private var lightweightImpactTargets = emptyList<String>()
    private var lightweightMoveName = ""
    private var lightweightMoveType = "NORMAL"
    private var lightweightMoveCategory = "PHYSICAL"
    private var lightweightStatEffectAtNanos = 0L
    private var lightweightStatDirection = 0
    private var lightweightImpactSoundPending = false
    private var lightweightImpactSoundCue: BattleAudioCue? = null
    private var lightweightImpactSoundListener: ((BattleAudioCue?) -> Unit)? = null
    private var lightweightLateImpactSoundCue: BattleAudioCue? = null
    private var lightweightLateImpactSoundListener: ((BattleAudioCue) -> Unit)? = null
    private var lightweightPausedAtNanos = 0L

    private data class InspectTarget(val player: Boolean, val slot: String?)

    private data class HpColors(val fill: Int, val highlight: Int, val shadow: Int)

    private data class PartyBallBitmapKey(val size: Int, val state: PartyBallState)

    init {
        setWillNotDraw(false)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = BattlePlaybackSpeed.coerce(speed)
        battleFeedPresentation.setPlaybackSpeed(speed)
        invalidate()
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
    }

    fun refreshResourceRequests() {
        if (!session.isLiveBattleActive() && !session.isBattleFinished()) {
            releaseRetainedResources()
        }
        resourcesRequested = false
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
        itemSprites.clear()
        requestedItemSprites.clear()
        effectAssets.clear()
        requestedEffects.clear()
        partyBallBitmaps.clear()
        backdrop = null
        requestedBackdrop = ""
        resourcesRequested = false
        spriteCache.clearMemory()
    }

    fun resetBattleFeed() {
        battleFeedPresentation.reset()
        battleFeedBounds.setEmpty()
        lightweightMoveStartedAtNanos = 0L
        lightweightMoveAnimationEnabled = true
        lightweightMoveActorPlayer = true
        lightweightMoveTargetPlayer = null
        lightweightImpactAtNanos = 0L
        lightweightImpactTargets = emptyList()
        lightweightMoveName = ""
        lightweightMoveType = "NORMAL"
        lightweightMoveCategory = "PHYSICAL"
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
                    lightweightMoveTargetPlayer = fields.getOrNull(4)?.let(session::isLocalBattleSide)
                    lightweightImpactAtNanos = 0L
                    lightweightImpactTargets = emptyList()
                    lightweightImpactSoundPending = false
                    lightweightImpactSoundCue = null
                    lightweightLateImpactSoundCue = null
                    lightweightMoveName = fields.getOrNull(3).orEmpty()
                    val animationMoveName = ShowdownBattleMovePresentation.animationName(moveArguments, lightweightMoveName)
                    val originalMoveType = session.moveTypeFor(lightweightMoveName)?.uppercase() ?: inferMoveType(lightweightMoveName)
                    val originalMoveCategory = session.moveInfoFor(lightweightMoveName)?.category?.uppercase()
                        ?: inferMoveCategory(lightweightMoveName)
                    lightweightMoveType = session.moveTypeFor(animationMoveName)?.uppercase() ?: originalMoveType
                    lightweightMoveCategory = session.moveInfoFor(animationMoveName)?.category?.uppercase()
                        ?: originalMoveCategory
                    lightweightStatEffectAtNanos = 0L
                    lightweightStatDirection = 0
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
                        lightweightImpactAtNanos = nowNanos + BattleSceneTiming.scaledDurationNanos(
                            BattleSceneTiming.lightweightImpactDelayNanos,
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
        val singles = session.isSinglesBattle()
        val playerX = if (singles) ShowdownBattleLayout.x(width, ShowdownBattleLayout.PLAYER_X) else width * 0.30f
        val playerY = if (singles) ShowdownBattleLayout.y(height, ShowdownBattleLayout.PLAYER_Y) else height * 0.67f
        val opponentX = if (singles) ShowdownBattleLayout.x(width, ShowdownBattleLayout.OPPONENT_X) else width * 0.73f
        val opponentY = if (singles) ShowdownBattleLayout.y(height, ShowdownBattleLayout.OPPONENT_Y) else height * 0.42f
        val playerCombatants = session.playerActiveCombatants()
        val opponentCombatants = session.opponentActiveCombatants()
        val nowNanos = System.nanoTime()
        if (session.isReplayMode() && !session.hasBattleProtocolTranscript()) {
            battleFeedPresentation.update(emptyList(), false, SystemClock.elapsedRealtime())
            drawLobby(canvas, width, height, scale)
            return
        }
        if (!session.isLiveBattleActive() && !session.isBattleFinished()) {
            battleFeedPresentation.update(emptyList(), false, SystemClock.elapsedRealtime())
            drawLobby(canvas, width, height, scale)
            return
        }
        val playerStatusAlpha = statusCardAlpha(session.playerPokemon, session.playerCondition, nowNanos) *
            BattleSceneTiming.summonStatusCardAlpha(session.playerEntryAtNanos, nowNanos)
        val opponentStatusAlpha = statusCardAlpha(session.opponentPokemon, session.opponentCondition, nowNanos) *
            BattleSceneTiming.summonStatusCardAlpha(session.opponentEntryAtNanos, nowNanos)
        playerInspectBounds.set(width * 0.05f, height * 0.28f, width * 0.57f, height * 0.88f)
        opponentInspectBounds.set(width * 0.47f, height * 0.11f, width * 0.95f, height * 0.67f)
        if (!resourcesRequested) {
            requestResources()
            resourcesRequested = true
        }
        drawBackdrop(canvas, width, height)
        if (!singles && opponentCombatants.isNotEmpty()) {
            fieldCombatants(opponentCombatants, false).forEachIndexed { index, combatant ->
                drawCombatant(
                    canvas,
                    multiCombatantX(width, false, index, opponentCombatants.size),
                    opponentY,
                    scale * 0.92f,
                    combatant.name,
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
                session.opponentPokemon,
                session.opponentCondition,
                session.opponentEntryAtNanos,
                nowNanos,
                opponentSprite,
                showdownPlacement = singles
            )
        }
        if (!singles && playerCombatants.isNotEmpty()) {
            fieldCombatants(playerCombatants, true).forEachIndexed { index, combatant ->
                drawCombatant(
                    canvas,
                    multiCombatantX(width, true, index, playerCombatants.size),
                    playerY,
                    scale * 1.02f,
                    combatant.name,
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
                session.playerPokemon,
                session.playerCondition,
                session.playerEntryAtNanos,
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
                if (playerStatusAlpha > 0f) {
                    drawStatusCard(
                        canvas,
                        RectF(
                            width * ShowdownBattleLayout.SINGLE_CARD_LEFT_FRACTION,
                            height * 0.80f,
                            ShowdownBattleLayout.singlePlayerCardRight(width, scale),
                            height * 0.98f
                        ),
                        session.playerDetails(),
                        session.playerHp,
                        scale,
                        playerStatusAlpha,
                        session.playerPartyDetails()
                    )
                }
            } else {
                drawActiveStatusCards(canvas, width, height, scale, true, fieldCombatants(playerCombatants, true))
            }
            if (singles) {
                if (opponentStatusAlpha > 0f) {
                    drawStatusCard(
                        canvas,
                        RectF(
                            ShowdownBattleLayout.singleOpponentCardLeft(width, scale),
                            height * 0.02f,
                            width * ShowdownBattleLayout.SINGLE_CARD_RIGHT_FRACTION,
                            height * 0.20f
                        ),
                        session.opponentDetails(),
                        session.opponentHp,
                        scale,
                        opponentStatusAlpha,
                        session.opponentPartyDetails()
                    )
                }
            } else {
                drawActiveStatusCards(canvas, width, height, scale, false, fieldCombatants(opponentCombatants, false))
            }
            drawBattleFeed(canvas, width, height, scale)
        }
        drawInspectSheet(canvas, width, height, scale)
        if (
            (
            playerCombatants.any { isFainting(it.name, it.condition) } ||
            opponentCombatants.any { isFainting(it.name, it.condition) } ||
            BattleSceneTiming.summonProgress(session.playerEntryAtNanos, nowNanos) < 1f ||
            BattleSceneTiming.summonProgress(session.opponentEntryAtNanos, nowNanos) < 1f ||
            playerSprite?.isAnimated == true ||
            opponentSprite?.isAnimated == true ||
            playerCombatants.any { playerActiveSprites[it.slot]?.isAnimated == true } ||
            opponentCombatants.any { opponentActiveSprites[it.slot]?.isAnimated == true } ||
            lightweightMoveEffectActive(nowNanos)
            ) && !animationsPaused
        ) {
            postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
        }
        if (session.battleClockSeconds() != null && !animationsPaused) postInvalidateDelayed(1_000L)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
        val playerCombatants = fieldCombatants(session.playerActiveCombatants(), true)
        val opponentCombatants = fieldCombatants(session.opponentActiveCombatants(), false)
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
        val spriteTarget = combatants.mapIndexed { index, combatant ->
            val centerX = multiCombatantX(width, player, index, combatants.size)
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

    private fun requestResources() {
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
        val playerActiveCombatants = session.playerActiveCombatants()
        val opponentActiveCombatants = session.opponentActiveCombatants()
        if (session.isSinglesBattle() || playerActiveCombatants.isEmpty()) {
            val playerCombatant = playerActiveCombatants.firstOrNull()
            val playerSpecies = playerCombatant?.species
                ?.ifBlank { session.playerPokemon }
                ?: session.playerPokemon
            val playerRequest = BattleSpriteRequests.single(playerSpecies, BattleSpriteSide.PLAYER, session.spriteStyle, playerCombatant?.shiny == true)
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
        if (session.isSinglesBattle() || opponentActiveCombatants.isEmpty()) {
            val opponentCombatant = opponentActiveCombatants.firstOrNull()
            val opponentSpecies = opponentCombatant?.species
                ?.ifBlank { session.opponentPokemon }
                ?: session.opponentPokemon
            val opponentRequest = BattleSpriteRequests.single(opponentSpecies, BattleSpriteSide.OPPONENT, session.spriteStyle, opponentCombatant?.shiny == true)
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
        if (session.isSinglesBattle()) {
            requestedPlayerActiveSprites.clear()
            requestedOpponentActiveSprites.clear()
            playerActiveSprites.values.forEach { it?.stopAnimation() }
            opponentActiveSprites.values.forEach { it?.stopAnimation() }
            playerActiveSprites.clear()
            opponentActiveSprites.clear()
        } else {
            requestActiveSprites(
                BattleSpriteRequests.active(playerActiveCombatants, BattleSpriteSide.PLAYER, session.spriteStyle),
                playerActiveSprites,
                requestedPlayerActiveSprites
            )
            requestActiveSprites(
                BattleSpriteRequests.active(opponentActiveCombatants, BattleSpriteSide.OPPONENT, session.spriteStyle),
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
        val actorX = if (lightweightMoveActorPlayer) playerX else opponentX
        val actorY = if (lightweightMoveActorPlayer) playerY else opponentY
        val impactAt = lightweightImpactAtNanos
        val targetPlayer = lightweightMoveTargetPlayer ?: !lightweightMoveActorPlayer
        val targetX = if (targetPlayer) playerX else opponentX
        val targetY = if (targetPlayer) playerY else opponentY
        val palette = lightweightMovePalette(lightweightMoveType)
        if (lightweightMoveAnimationEnabled) {
            if (lightweightMoveCategory == "STATUS") {
                drawStatusMoveEffect(canvas, targetX, targetY, scale, nowNanos, palette)
            } else {
                drawAttackMoveEffect(
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
                    palette
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
        palette: MoveEffectPalette
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
        val eased = moveProgress * moveProgress * (3f - 2f * moveProgress)
        val endX = actorX + (targetX - actorX) * eased
        val endY = actorY + (targetY - actorY) * eased
        val dx = targetX - actorX
        val dy = targetY - actorY
        val length = maxOf(1f, kotlin.math.sqrt(dx * dx + dy * dy))
        val normalX = -dy / length
        val normalY = dx / length
        val tailProgress = (moveProgress - 0.16f).coerceAtLeast(0f)
        val tailX = actorX + (targetX - actorX) * tailProgress
        val tailY = actorY + (targetY - actorY) * tailProgress
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 34f * scale
        paint.color = Color.argb(((1f - moveProgress) * 55f).toInt(), Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary))
        canvas.drawLine(tailX, tailY - 42f * scale, endX, endY - 42f * scale, paint)
        paint.strokeWidth = 13f * scale
        paint.color = Color.argb(((1f - moveProgress) * 220f).toInt(), Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
        canvas.drawLine(tailX, tailY - 42f * scale, endX, endY - 42f * scale, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        val orbRadius = (24f + 14f * sin(moveProgress * Math.PI).toFloat()) * scale
        paint.color = palette.accent
        canvas.drawCircle(endX, endY - 42f * scale, orbRadius, paint)
        paint.color = Color.argb(220, 255, 255, 255)
        canvas.drawCircle(endX - normalX * orbRadius * 0.35f, endY - 42f * scale - normalY * orbRadius * 0.35f, orbRadius * 0.34f, paint)
        paint.color = Color.argb(((1f - moveProgress) * 180f).toInt(), Color.red(palette.secondary), Color.green(palette.secondary), Color.blue(palette.secondary))
        for (index in 0 until 3) {
            val offset = (index - 1) * 34f * scale
            canvas.drawCircle(endX + normalX * offset, endY - 42f * scale + normalY * offset, (8f + index * 3f) * scale, paint)
        }
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
        val index = combatants.indexOfFirst { combatant ->
            BattleDamageCueResolver.targetKey(combatant.slot) == BattleDamageCueResolver.targetKey(target)
        }
        if (index < 0) return if (player) playerX to playerY else opponentX to opponentY
        return multiCombatantX(width, player, index, combatants.size) to if (player) height * 0.67f else height * 0.42f
    }

    private fun requestHeldItemSprites() {
        val itemNames = buildList {
            add(session.playerDetails().item)
            add(session.opponentDetails().item)
            session.playerActiveCombatants().forEach { combatant ->
                add(session.detailsForActiveCombatant(true, combatant.slot)?.item.orEmpty())
            }
            session.opponentActiveCombatants().forEach { combatant ->
                add(session.detailsForActiveCombatant(false, combatant.slot)?.item.orEmpty())
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

    private fun multiCombatantX(width: Float, player: Boolean, index: Int, count: Int): Float {
        val step = if (count > 2) 0.12f else 0.16f
        val base = if (player) 0.20f else 0.64f
        return width * (base + index * step)
    }

    private fun fieldCombatants(combatants: List<BattleSession.ActiveCombatant>, player: Boolean) =
        if (player) combatants else combatants.asReversed()

    private fun drawCombatant(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        scale: Float,
        pokemon: String,
        condition: String,
        summonAtNanos: Long,
        nowNanos: Long,
        sprite: ShowdownSpriteCache.SpriteAsset?,
        showdownPlacement: Boolean = false
    ) {
        val faintProgress = faintProgress(pokemon, condition, nowNanos)
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

    private fun faintProgress(pokemon: String, condition: String, nowNanos: Long) = BattleSceneTiming.faintProgress(
        pokemon,
        condition,
        session.latestFaintedPokemon,
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

    private fun isFainting(pokemon: String, condition: String) =
        condition.contains("FNT", true) && faintProgress(pokemon, condition, System.nanoTime()) < 1f

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
        val details = inspectedSlot?.let { session.detailsForActiveCombatant(playerSide, it) }
            ?: if (playerSide) session.playerDetails() else session.opponentDetails()
        val visibleCombatants = inspectedSlot?.let { slot ->
            (if (playerSide) session.playerActiveCombatants() else session.opponentActiveCombatants())
                .filter { it.slot == slot }
        } ?: (if (playerSide) session.playerActiveCombatants() else session.opponentActiveCombatants())
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
        paint.color = MUTED
        canvas.drawText("Ability", left, row, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = INK
        canvas.drawText(ellipsizeToWidth(details.ability, right - left - 150f * scale, paint), right, row, paint)
        paint.textAlign = Paint.Align.LEFT
        row += 56f * scale
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
        paint.textSize = readableTextSize(36f, scale, 16f)
        paint.color = MUTED
        canvas.drawText(ellipsizeToWidth(details.stats, right - left, paint), left, row, paint)
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
        party: List<BattleSession.PokemonDetails>
    ) {
        drawBattleStatusCard(
            canvas,
            bounds,
            BattleCardContent.from(details, hp),
            scale,
            alpha,
            BattleCardLayout.compactFor(1),
            party
        )
    }

    private fun drawActiveStatusCards(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        player: Boolean,
        combatants: List<BattleSession.ActiveCombatant>
    ) {
        val layout = BattleCardLayout.compactFor(combatants.size)
        val nowNanos = System.nanoTime()
        val party = if (player) session.playerPartyDetails() else session.opponentPartyDetails()
        combatants.forEachIndexed { index, combatant ->
            val alpha = statusCardAlpha(combatant.name, combatant.condition, nowNanos) *
                BattleSceneTiming.summonStatusCardAlpha(combatant.entryAtNanos, nowNanos)
            if (alpha > 0f) {
                drawCompactStatusCard(
                    canvas,
                    BattleCardLayout.compactBoundsFor(width, height, player, index, combatants.size).toRectF(),
                    BattleCardContent.from(
                        combatant,
                        session.detailsForActiveCombatant(player, combatant.slot)?.item.orEmpty()
                    ),
                    scale,
                    alpha,
                    layout,
                    party
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
        party: List<BattleSession.PokemonDetails>
    ) {
        drawBattleStatusCard(
            canvas,
            bounds,
            content,
            scale,
            alpha,
            layout,
            party
        )
    }

    private fun drawBattleStatusCard(
        canvas: Canvas,
        bounds: RectF,
        content: BattleCardContent,
        scale: Float,
        alpha: Float,
        layout: CompactBattleCardLayout,
        party: List<BattleSession.PokemonDetails>
    ) {
        val layer = canvas.saveLayerAlpha(bounds, (alpha * 255f).toInt())
        val left = bounds.left + 20f * scale
        val right = bounds.right - 20f * scale
        drawBattleStatusCardSurface(canvas, bounds, scale)
        drawBattleStatusCardContent(canvas, bounds, content, left, right, scale, layout, party)
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
        party: List<BattleSession.PokemonDetails>
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

    private fun drawBattleFeed(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val nowMillis = SystemClock.elapsedRealtime()
        battleFeedBounds.setEmpty()
        val feedEntries = session.battleFeedEntries()
        val persistentText = session.battleResult()
        battleFeedPresentation.update(feedEntries, session.battleFeedVisible, nowMillis, persistentText)
        val frame = battleFeedPresentation.frame(nowMillis) ?: return
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
        val playerSpriteRight = if (session.isSinglesBattle()) {
            ShowdownBattleLayout.x(width, ShowdownBattleLayout.PLAYER_X) +
                145f * scale * ShowdownBattleLayout.PLAYER_SCALE
        } else if (playerCombatants.isEmpty()) {
            playerCardRight
        } else {
            playerCombatants.indices.maxOf { index ->
                multiCombatantX(width, true, index, playerCombatants.size) + 145f * scale * 1.02f
            }
        }
        val settledLeft = maxOf(width * 0.33f, playerCardRight + sideGap, playerSpriteRight + sideGap)
        val left = settledLeft
        val right = width * 0.97f
        val bottom = min(height * 0.945f, height * 0.98f - 32f * scale)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        val textSize = readableTextSize(36f, scale, 11f)
        paint.textSize = textSize
        val maxWidth = right - left - 48f * scale
        val lineHeight = maxOf(42f * scale, paint.descent() - paint.ascent() + 8f * scale)
        val padding = 22f * scale
        if (cachedBattleFeedText != frame.text ||
            cachedBattleFeedVisibleText != frame.visibleText ||
            cachedBattleFeedWidth != maxWidth ||
            cachedBattleFeedTextSize != textSize
        ) {
            cachedBattleFeedText = frame.text
            cachedBattleFeedVisibleText = frame.visibleText
            cachedBattleFeedWidth = maxWidth
            cachedBattleFeedTextSize = textSize
            cachedBattleFeedFullLines = BattleFeedText.wrap(frame.text, maxWidth, 2, paint::measureText)
                .ifEmpty { listOf("") }
            cachedBattleFeedLines = BattleFeedText.wrap(frame.visibleText, maxWidth, cachedBattleFeedFullLines.size, paint::measureText)
                .ifEmpty { listOf("") }
        }
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
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.25f * scale
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = Color.argb((220f * outlineAlpha).toInt(), 3, 12, 18)
            canvas.drawText(line, left + 24f * scale, baseline, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((255f * textAlpha).toInt(), 255, 255, 255)
            canvas.drawText(line, left + 24f * scale, baseline, paint)
        }
        canvas.restore()
        if (battleFeedPresentation.needsAnimation(nowMillis)) postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
    }

    private fun ellipsize(value: String, maximum: Int) = if (value.length <= maximum) value else "${value.take(maximum - 1)}…"

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
