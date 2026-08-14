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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

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
    private var pokeballSheet: Bitmap? = null
    private var requestedPokeballSheet = false
    private val partyBallGlyphValidity = mutableMapOf<PartyBallState, Boolean>()
    private var playerSprite: ShowdownSpriteCache.SpriteAsset? = null
    private var opponentSprite: ShowdownSpriteCache.SpriteAsset? = null
    private val playerActiveSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val opponentActiveSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val requestedPlayerActiveSprites = mutableMapOf<String, BattleSpriteRequest>()
    private val requestedOpponentActiveSprites = mutableMapOf<String, BattleSpriteRequest>()
    private var playerPlaceholder: ShowdownSpriteCache.SpriteAsset? = null
    private var opponentPlaceholder: ShowdownSpriteCache.SpriteAsset? = null
    private var requestedPlayerSprite: BattleSpriteRequest? = null
    private var requestedOpponentSprite: BattleSpriteRequest? = null
    private var requestedBackdrop = ""
    private val effectAssets = mutableMapOf<String, Bitmap>()
    private val requestedEffects = mutableSetOf<String>()
    private var inspectedPlayer: Boolean? = null
    private var inspectedSlot: String? = null
    private val playerInspectBounds = RectF()
    private val opponentInspectBounds = RectF()
    private val battleFeedBounds = RectF()
    private val battleFeedPresentation = BattleFeedPresentation()
    private var battleFeedTouchDownY = 0f
    private var battleFeedTouchLastY = 0f
    private var battleFeedTouchActive = false
    private var battleFeedTouchMoved = false

    private data class InspectTarget(val player: Boolean, val slot: String?)

    private data class HpColors(val fill: Int, val highlight: Int, val shadow: Int)

    init {
        setWillNotDraw(false)
        spriteCache.requestPlaceholder(BattleSpriteSide.PLAYER) {
            playerPlaceholder = it
            invalidate()
        }
        spriteCache.requestPlaceholder(BattleSpriteSide.OPPONENT) {
            opponentPlaceholder = it
            invalidate()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        battleFeedPresentation.setPlaybackSpeed(speed)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val scale = min(width / 1920f, height / 1080f)
        val singles = session.isSinglesBattle()
        val playerX = width * 0.30f
        val playerY = height * if (singles) 0.68f else 0.67f
        val opponentX = width * 0.73f
        val opponentY = height * if (singles) 0.45f else 0.42f
        val playerCombatants = session.playerActiveCombatants()
        val opponentCombatants = session.opponentActiveCombatants()
        val nowNanos = System.nanoTime()
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
        requestResources()
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
                    opponentActiveSprites[combatant.slot] ?: opponentPlaceholder
                )
            }
        } else {
            drawCombatant(
                canvas,
                opponentX,
                opponentY,
                scale * if (singles) 1.30f else 1.05f,
                session.opponentPokemon,
                session.opponentCondition,
                session.opponentEntryAtNanos,
                nowNanos,
                opponentSprite ?: opponentPlaceholder
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
                    playerActiveSprites[combatant.slot] ?: playerPlaceholder
                )
            }
        } else {
            drawCombatant(
                canvas,
                playerX,
                playerY,
                scale * if (singles) 1.50f else 1.16f,
                session.playerPokemon,
                session.playerCondition,
                session.playerEntryAtNanos,
                nowNanos,
                playerSprite ?: playerPlaceholder
            )
        }
        drawHeader(canvas, width, scale)
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
                        RectF(width * 0.015f, height * 0.80f, width * 0.315f, height * 0.98f),
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
                        RectF(width * 0.685f, height * 0.02f, width * 0.985f, height * 0.20f),
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
            playerCombatants.any { isFainting(it.name, it.condition) } ||
            opponentCombatants.any { isFainting(it.name, it.condition) } ||
            BattleSceneTiming.summonProgress(session.playerEntryAtNanos, nowNanos) < 1f ||
            BattleSceneTiming.summonProgress(session.opponentEntryAtNanos, nowNanos) < 1f ||
            (playerSprite ?: playerPlaceholder)?.isAnimated == true ||
            (opponentSprite ?: opponentPlaceholder)?.isAnimated == true ||
            playerCombatants.any { playerActiveSprites[it.slot]?.isAnimated == true } ||
            opponentCombatants.any { opponentActiveSprites[it.slot]?.isAnimated == true }
        ) {
            postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
        }
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
        val playerSpecies = session.playerActiveCombatants().firstOrNull()?.species
            ?.ifBlank { session.playerPokemon }
            ?: session.playerPokemon
        val playerRequest = BattleSpriteRequests.single(playerSpecies, BattleSpriteSide.PLAYER, session.spriteStyle)
        if (playerRequest != requestedPlayerSprite) {
            requestedPlayerSprite = playerRequest
            playerSprite = null
            spriteCache.requestPokemon(playerRequest) { asset ->
                if (playerRequest == requestedPlayerSprite) {
                    playerSprite = asset
                    invalidate()
                }
            }
        }
        val opponentSpecies = session.opponentActiveCombatants().firstOrNull()?.species
            ?.ifBlank { session.opponentPokemon }
            ?: session.opponentPokemon
        val opponentRequest = BattleSpriteRequests.single(opponentSpecies, BattleSpriteSide.OPPONENT, session.spriteStyle)
        if (opponentRequest != requestedOpponentSprite) {
            requestedOpponentSprite = opponentRequest
            opponentSprite = null
            spriteCache.requestPokemon(opponentRequest) { asset ->
                if (opponentRequest == requestedOpponentSprite) {
                    opponentSprite = asset
                    invalidate()
                }
            }
        }
        requestActiveSprites(
            BattleSpriteRequests.active(session.playerActiveCombatants(), BattleSpriteSide.PLAYER, session.spriteStyle),
            playerActiveSprites,
            requestedPlayerActiveSprites
        )
        requestActiveSprites(
            BattleSpriteRequests.active(session.opponentActiveCombatants(), BattleSpriteSide.OPPONENT, session.spriteStyle),
            opponentActiveSprites,
            requestedOpponentActiveSprites
        )
        if (!requestedPokeballSheet) {
            requestedPokeballSheet = true
            spriteCache.requestPokemonBallSheet { asset ->
                pokeballSheet = asset
                partyBallGlyphValidity.clear()
                invalidate()
            }
        }
        SHOWDOWN_EFFECTS.forEach { name ->
            if (requestedEffects.add(name)) {
                spriteCache.requestEffect(name) { asset ->
                    if (asset != null) effectAssets[name] = asset
                    invalidate()
                }
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
            assets.remove(it)
        }
        plannedRequests.forEach { plannedRequest ->
            val slot = plannedRequest.slot
            val request = plannedRequest.request
            if (requests[slot] == request) return@forEach
            requests[slot] = request
            assets[slot] = null
            spriteCache.requestPokemon(request) { asset ->
                if (requests[slot] == request) {
                    assets[slot] = asset
                    invalidate()
                }
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
        canvas.drawText("SHOWDOWN!", 178f * scale, 111f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 22f * scale
        paint.color = CYAN
        canvas.drawText(session.matchFormat.label, 180f * scale, 143f * scale, paint)
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
        canvas.drawText("Ready for a battle", card.left + 68f * scale, card.top + 108f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 28f * scale
        paint.color = MUTED
        canvas.drawText("Use the lower screen to connect, search, or challenge.", card.left + 68f * scale, card.top + 165f * scale, paint)
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
        canvas.drawText("Menu: Find battle  ·  Challenge player  ·  Team library", card.left + 68f * scale, card.bottom - 70f * scale, paint)
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
        sprite: ShowdownSpriteCache.SpriteAsset?
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
        sprite.draw(
            canvas,
            RectF(
                centerX - spriteWidth / 2f,
                centerY + summonOffset - spriteHeight * 0.68f - 240f * scale * easedFaint,
                centerX + spriteWidth / 2f,
                centerY + summonOffset + spriteHeight * 0.32f - 240f * scale * easedFaint
            ),
            SystemClock.elapsedRealtime(),
            alpha = ((1f - easedFaint) * summonAlpha * 255f).toInt()
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
        val innerInset = 10f * scale
        val iconSize = 42f * scale
        val iconGap = 12f * scale
        val title = "SHOWDOWN!"
        val format = session.format.uppercase(Locale.ROOT)
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = 25f * scale
        val titleLeft = padding + innerInset + iconSize + iconGap
        val titleWidth = paint.measureText(title)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 14f * scale
        val formatWidth = paint.measureText(format)
        val headerRight = (titleLeft + maxOf(titleWidth, formatWidth) + innerInset).coerceAtMost(width - padding)
        val formatAvailableWidth = (headerRight - titleLeft - innerInset).coerceAtLeast(0f)
        val displayedFormat = ellipsizeToWidth(format, formatAvailableWidth, paint)
        paint.color = Color.argb(200, 5, 12, 29)
        canvas.drawRoundRect(RectF(padding, padding, headerRight, padding + 58f * scale), 16f * scale, 16f * scale, paint)
        logo?.let {
            source.set(0, 0, it.width, it.height)
            destination.set(padding + innerInset, padding + 8f * scale, padding + innerInset + iconSize, padding + 50f * scale)
            canvas.drawBitmap(it, source, destination, paint)
        }
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = 25f * scale
        paint.color = INK
        canvas.drawText(title, titleLeft, padding + 31f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 14f * scale
        paint.color = CYAN
        canvas.drawText(displayedFormat, titleLeft + scale, padding + 48f * scale, paint)
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
        canvas.drawText(ellipsizeToWidth(details.item, right - left - 110f * scale, paint), right, row, paint)
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
                    BattleCardContent.from(combatant),
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
        paint.color = Color.rgb(232, 232, 232)
        canvas.drawText(content.levelLabel, textRight, bounds.top + height * contentLayout.titleBaselineFraction, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = readableTextSize(height * 0.27f, scale, 10.5f)
        val titleWidth = (textRight - textLeft - levelWidth - 16f * scale).coerceAtLeast(0f)
        paint.color = INK
        canvas.drawText(
            ellipsizeToWidth(content.title, titleWidth, paint),
            textLeft,
            bounds.top + height * contentLayout.titleBaselineFraction,
            paint
        )
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
        val sheet = pokeballSheet
        if (sheet != null && state != PartyBallState.FAINTED && drawPartyBallFromSheet(canvas, sheet, left, top, size, state)) {
            return
        }
        drawFallbackPartyBall(canvas, left, top, size, state)
    }

    private fun drawPartyBallFromSheet(
        canvas: Canvas,
        sheet: Bitmap,
        left: Float,
        top: Float,
        size: Float,
        state: PartyBallState
    ): Boolean {
        val cellLeft = if (state == PartyBallState.STATUSED) POKEBALL_TILE_WIDTH_PIXELS else 0
        val cellRight = cellLeft + POKEBALL_GLYPH_LEFT + POKEBALL_GLYPH_SIZE
        val glyphBottom = POKEBALL_GLYPH_TOP + POKEBALL_GLYPH_SIZE
        if (cellRight > sheet.width || glyphBottom > sheet.height) return false
        val glyphVisible = partyBallGlyphValidity.getOrPut(state) {
            var visible = false
            for (y in POKEBALL_GLYPH_TOP until glyphBottom) {
                for (x in (cellLeft + POKEBALL_GLYPH_LEFT) until cellRight) {
                    if ((sheet.getPixel(x, y) ushr 24) != 0) {
                        visible = true
                        break
                    }
                }
                if (visible) break
            }
            visible
        }
        if (!glyphVisible) return false
        source.set(
            cellLeft + POKEBALL_GLYPH_LEFT,
            POKEBALL_GLYPH_TOP,
            cellRight,
            glyphBottom
        )
        destination.set(left, top, left + size, top + size)
        canvas.drawBitmap(sheet, source, destination, paint)
        return true
    }

    private fun drawFallbackPartyBall(canvas: Canvas, left: Float, top: Float, size: Float, state: PartyBallState) {
        val centerX = left + size / 2f
        val centerY = top + size / 2f
        val radius = size * 0.40f
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = when (state) {
            PartyBallState.READY -> Color.rgb(234, 76, 42)
            PartyBallState.STATUSED -> Color.rgb(236, 196, 31)
            PartyBallState.FAINTED -> Color.rgb(95, 106, 117)
        }
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.color = Color.rgb(219, 228, 235)
        canvas.drawArc(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), 0f, 180f, true, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, size * 0.06f)
        paint.color = Color.rgb(184, 197, 208)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(31, 39, 47)
        canvas.drawRect(centerX - radius, centerY - paint.strokeWidth / 2f, centerX + radius, centerY + paint.strokeWidth / 2f, paint)
        paint.color = Color.rgb(198, 209, 218)
        canvas.drawCircle(centerX, centerY, size * 0.11f, paint)
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
        val feedEntries = session.battleFeedEntries()
        battleFeedPresentation.update(feedEntries, true, nowMillis)
        val frame = battleFeedPresentation.frame(nowMillis) ?: return
        val alpha = frame.alpha
        val playerCardRight = width * 0.315f
        val sideGap = maxOf(48f * scale, width * 0.025f)
        val settledLeft = maxOf(width * 0.33f, playerCardRight + sideGap)
        val left = settledLeft
        val right = width * 0.97f
        val bottom = min(height * 0.945f, height * 0.98f - 32f * scale)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textSize = readableTextSize(36f, scale, 11f)
        val maxWidth = right - left - 48f * scale
        val lineHeight = maxOf(42f * scale, paint.descent() - paint.ascent() + 8f * scale)
        val padding = 22f * scale
        val fullLines = BattleFeedText.wrap(frame.text, maxWidth, 2, paint::measureText)
            .ifEmpty { listOf("") }
        val lines = BattleFeedText.wrap(frame.visibleText, maxWidth, fullLines.size, paint::measureText)
            .ifEmpty { listOf("") }
        val boundsHeight = fullLines.size * lineHeight + padding * 2f
        val top = (bottom - boundsHeight).coerceAtLeast(height * 0.70f)
        val bounds = RectF(left, top, right, bottom)
        battleFeedBounds.set(bounds)
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            Color.argb((78f * alpha).toInt(), 21, 42, 57),
            Color.argb((32f * alpha).toInt(), 52, 79, 94),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.argb((132f * alpha).toInt(), 183, 229, 235)
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
            paint.color = Color.argb((220f * alpha).toInt(), 3, 12, 18)
            canvas.drawText(line, left + 24f * scale, baseline, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((255f * alpha).toInt(), 255, 255, 255)
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
        const val POKEBALL_TILE_WIDTH_PIXELS = 40
        const val POKEBALL_GLYPH_LEFT = 14
        const val POKEBALL_GLYPH_TOP = 10
        const val POKEBALL_GLYPH_SIZE = 12
    }

    private enum class PartyBallState {
        READY,
        STATUSED,
        FAINTED
    }
}
