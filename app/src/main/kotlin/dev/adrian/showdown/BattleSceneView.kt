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
    private var playerSprite: ShowdownSpriteCache.SpriteAsset? = null
    private var opponentSprite: ShowdownSpriteCache.SpriteAsset? = null
    private val playerActiveSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val opponentActiveSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset?>()
    private val requestedPlayerActiveSprites = mutableMapOf<String, String>()
    private val requestedOpponentActiveSprites = mutableMapOf<String, String>()
    private var playerPlaceholder: ShowdownSpriteCache.SpriteAsset? = null
    private var opponentPlaceholder: ShowdownSpriteCache.SpriteAsset? = null
    private var playerTrainerSprite: ShowdownSpriteCache.SpriteAsset? = null
    private var opponentTrainerSprite: ShowdownSpriteCache.SpriteAsset? = null
    private var requestedPlayerSprite = ""
    private var requestedOpponentSprite = ""
    private var requestedPlayerTrainer = false
    private var requestedOpponentTrainer = false
    private var requestedBackdrop = ""
    private var requestedPokeballSheet = false
    private val effectAssets = mutableMapOf<String, Bitmap>()
    private val requestedEffects = mutableSetOf<String>()
    private var inspectedPlayer: Boolean? = null
    private val playerInspectBounds = RectF()
    private val opponentInspectBounds = RectF()

    private data class HpColors(val fill: Int, val highlight: Int, val shadow: Int)

    init {
        setWillNotDraw(false)
        spriteCache.requestPlaceholder(true) {
            playerPlaceholder = it
            invalidate()
        }
        spriteCache.requestPlaceholder(false) {
            opponentPlaceholder = it
            invalidate()
        }
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
        if (!singles && opponentCombatants.size > 1) {
            fieldCombatants(opponentCombatants, false).forEachIndexed { index, combatant ->
                drawCombatant(
                    canvas,
                    multiCombatantX(width, false, index, opponentCombatants.size),
                    opponentY,
                    scale * 0.92f,
                    false,
                    combatant.condition,
                    combatant.entryAtNanos,
                    nowNanos,
                    opponentActiveSprites[combatant.slot],
                    combatant.name
                )
            }
        } else {
            val combatant = opponentCombatants.firstOrNull()
            drawCombatant(
                canvas,
                opponentX,
                opponentY,
                scale * if (singles) 1.30f else 1.05f,
                false,
                combatant?.condition ?: session.opponentCondition,
                combatant?.entryAtNanos ?: session.opponentEntryAtNanos,
                nowNanos,
                combatant?.let { opponentActiveSprites[it.slot] },
                combatant?.name
            )
        }
        if (!singles && playerCombatants.size > 1) {
            fieldCombatants(playerCombatants, true).forEachIndexed { index, combatant ->
                drawCombatant(
                    canvas,
                    multiCombatantX(width, true, index, playerCombatants.size),
                    playerY,
                    scale * 1.02f,
                    true,
                    combatant.condition,
                    combatant.entryAtNanos,
                    nowNanos,
                    playerActiveSprites[combatant.slot],
                    combatant.name
                )
            }
        } else {
            val combatant = playerCombatants.firstOrNull()
            drawCombatant(
                canvas,
                playerX,
                playerY,
                scale * if (singles) 1.50f else 1.16f,
                true,
                combatant?.condition ?: session.playerCondition,
                combatant?.entryAtNanos ?: session.playerEntryAtNanos,
                nowNanos,
                combatant?.let { playerActiveSprites[it.slot] },
                combatant?.name
            )
        }
        drawHeader(canvas, width, scale)
        if ((inspectedPlayer == true && !session.hasActivePlayerCombatant()) ||
            (inspectedPlayer == false && !session.hasActiveOpponentCombatant())
        ) {
            inspectedPlayer = null
        }
        if (inspectedPlayer == null) {
            if (singles || playerCombatants.size <= 1) {
                if (playerStatusAlpha > 0f) {
                    drawStatusCard(
                        canvas,
                        RectF(width * 0.015f, height * 0.80f, width * 0.315f, height * 0.98f),
                        session.playerName,
                        BattleSession.displayPokemonName(session.playerDetails().name, session.playerDetails().species),
                        session.playerLevel,
                        session.playerGender,
                        session.playerHp,
                        session.playerCondition,
                        session.playerHealthFraction(),
                        scale,
                        playerStatusAlpha,
                        playerTrainerSprite,
                        true,
                        session.playerPartyDetails()
                    )
                }
            } else {
                drawActiveStatusCards(canvas, width, height, scale, true, fieldCombatants(playerCombatants, true))
            }
            if (singles || opponentCombatants.size <= 1) {
                if (opponentStatusAlpha > 0f) {
                    drawStatusCard(
                        canvas,
                        RectF(width * 0.685f, height * 0.02f, width * 0.985f, height * 0.20f),
                        session.opponentName,
                        BattleSession.displayPokemonName(session.opponentDetails().name, session.opponentDetails().species),
                        session.opponentLevel,
                        session.opponentGender,
                        session.opponentHp,
                        session.opponentCondition,
                        session.opponentHealthFraction(),
                        scale,
                        opponentStatusAlpha,
                        opponentTrainerSprite,
                        true,
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
                return playerInspectBounds.contains(event.x, event.y) ||
                    opponentInspectBounds.contains(event.x, event.y) ||
                    inspectedPlayer != null
            }
            MotionEvent.ACTION_UP -> {
                val requestedPlayer = when {
                    playerInspectBounds.contains(event.x, event.y) -> true
                    opponentInspectBounds.contains(event.x, event.y) -> false
                    else -> null
                }
                inspectedPlayer = if (requestedPlayer == null || requestedPlayer == inspectedPlayer) null else requestedPlayer
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                return inspectedPlayer != null
            }
        }
        return super.onTouchEvent(event)
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
        val playerKey = "back:${session.spriteStyle}:$playerSpecies"
        if (playerKey != requestedPlayerSprite) {
            requestedPlayerSprite = playerKey
            playerSprite = null
            spriteCache.requestPokemon(playerSpecies, true, session.spriteStyle) { asset ->
                if (playerKey == requestedPlayerSprite) {
                    playerSprite = asset
                    invalidate()
                }
            }
        }
        val opponentSpecies = session.opponentActiveCombatants().firstOrNull()?.species
            ?.ifBlank { session.opponentPokemon }
            ?: session.opponentPokemon
        val opponentKey = "front:${session.spriteStyle}:$opponentSpecies"
        if (opponentKey != requestedOpponentSprite) {
            requestedOpponentSprite = opponentKey
            opponentSprite = null
            spriteCache.requestPokemon(opponentSpecies, false, session.spriteStyle) { asset ->
                if (opponentKey == requestedOpponentSprite) {
                    opponentSprite = asset
                    invalidate()
                }
            }
        }
        requestActiveSprites(session.playerActiveCombatants(), true, playerActiveSprites, requestedPlayerActiveSprites)
        requestActiveSprites(session.opponentActiveCombatants(), false, opponentActiveSprites, requestedOpponentActiveSprites)
        if (!requestedPlayerTrainer) {
            requestedPlayerTrainer = true
            spriteCache.requestTrainer("red") { asset ->
                playerTrainerSprite = asset
                invalidate()
            }
        }
        if (!requestedOpponentTrainer) {
            requestedOpponentTrainer = true
            spriteCache.requestTrainer("gladion") { asset ->
                opponentTrainerSprite = asset
                invalidate()
            }
        }
        if (!requestedPokeballSheet) {
            requestedPokeballSheet = true
            spriteCache.requestPokemonBallSheet { asset ->
                pokeballSheet = asset
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
        combatants: List<BattleSession.ActiveCombatant>,
        back: Boolean,
        assets: MutableMap<String, ShowdownSpriteCache.SpriteAsset?>,
        requests: MutableMap<String, String>
    ) {
        val activeSlots = combatants.map { it.slot }.toSet()
        requests.keys.filterNot(activeSlots::contains).toList().forEach {
            requests.remove(it)
            assets.remove(it)
        }
        combatants.forEach { combatant ->
            val species = combatant.species.ifBlank { combatant.name }
            val key = "${if (back) "back" else "front"}:${session.spriteStyle}:$species"
            if (requests[combatant.slot] == key) return@forEach
            requests[combatant.slot] = key
            assets[combatant.slot] = null
            spriteCache.requestPokemon(species, back, session.spriteStyle) { asset ->
                if (requests[combatant.slot] == key) {
                    assets[combatant.slot] = asset
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
        player: Boolean,
        condition: String,
        summonAtNanos: Long,
        nowNanos: Long,
        spriteOverride: ShowdownSpriteCache.SpriteAsset? = null,
        pokemonOverride: String? = null
    ) {
        val pokemon = pokemonOverride ?: if (player) session.playerPokemon else session.opponentPokemon
        val faintProgress = faintProgress(pokemon, condition, nowNanos)
        if (faintProgress >= 1f) return
        val sprite = if (pokemonOverride != null) {
            spriteOverride ?: if (player) playerPlaceholder else opponentPlaceholder
        } else if (player) {
            playerSprite ?: playerPlaceholder
        } else {
            opponentSprite ?: opponentPlaceholder
        }
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

    private fun drawTrainer(canvas: Canvas, centerX: Float, centerY: Float, scale: Float, player: Boolean) {
        val sprite = if (player) playerTrainerSprite else opponentTrainerSprite
        if (sprite != null) {
            val trainerWidth = 155f * scale
            val trainerHeight = 245f * scale
            sprite.draw(
                canvas,
                RectF(centerX - trainerWidth / 2f, centerY - trainerHeight / 2f, centerX + trainerWidth / 2f, centerY + trainerHeight / 2f),
                SystemClock.elapsedRealtime(),
                player
            )
            return
        }
        val accent = if (player) CYAN else MAGENTA
        paint.color = Color.argb(130, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(centerX, centerY - 48f * scale, 26f * scale, paint)
        paint.color = Color.argb(220, 13, 24, 51)
        canvas.drawRoundRect(RectF(centerX - 34f * scale, centerY - 18f * scale, centerX + 34f * scale, centerY + 76f * scale), 15f * scale, 15f * scale, paint)
    }

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
        val details = if (playerSide) session.playerDetails() else session.opponentDetails()
        val activeEffects = (if (playerSide) session.playerActiveCombatants() else session.opponentActiveCombatants())
            .flatMap { combatant ->
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
        trainer: String,
        pokemon: String,
        level: String,
        gender: String,
        hp: String,
        condition: String,
        fraction: Float,
        scale: Float,
        alpha: Float,
        trainerSprite: ShowdownSpriteCache.SpriteAsset?,
        trainerAtStart: Boolean,
        party: List<BattleSession.PokemonDetails>
    ) {
        val layer = canvas.saveLayerAlpha(bounds, (alpha * 255f).toInt())
        val height = bounds.height()
        paint.color = Color.argb(232, 16, 20, 26)
        canvas.drawRoundRect(bounds, height * 0.15f, height * 0.15f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.rgb(104, 111, 120)
        canvas.drawRoundRect(RectF(bounds.left + scale, bounds.top + scale, bounds.right - scale, bounds.bottom - scale), height * 0.15f, height * 0.15f, paint)
        paint.style = Paint.Style.FILL
        val portraitWidth = height * 0.42f
        val portraitHeight = height * 0.62f
        val portraitTop = bounds.centerY() - portraitHeight / 2f
        val portraitBounds = if (trainerAtStart) {
            RectF(bounds.left + 9f * scale, portraitTop, bounds.left + portraitWidth, portraitTop + portraitHeight)
        } else {
            RectF(bounds.right - portraitWidth, portraitTop, bounds.right - 9f * scale, portraitTop + portraitHeight)
        }
        trainerSprite?.draw(canvas, portraitBounds, SystemClock.elapsedRealtime(), alpha = 255)
        val textLeft = if (trainerAtStart) portraitBounds.right + 10f * scale else bounds.left + 20f * scale
        val textRight = if (trainerAtStart) bounds.right - 20f * scale else portraitBounds.left - 10f * scale
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        val levelLabel = "Lv.$level$gender"
        paint.textSize = height * 0.19f
        val levelWidth = paint.measureText(levelLabel)
        val nameAvailableWidth = (textRight - textLeft - levelWidth - 12f * scale).coerceAtLeast(0f)
        var nameTextSize = height * 0.27f
        while (nameTextSize > height * 0.15f) {
            paint.textSize = nameTextSize
            if (paint.measureText(pokemon) <= nameAvailableWidth) break
            nameTextSize -= scale
        }
        paint.textSize = nameTextSize
        val displayedPokemon = ellipsizeToWidth(pokemon, nameAvailableWidth, paint)
        paint.color = INK
        canvas.drawText(displayedPokemon, textLeft, bounds.top + height * 0.29f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = height * 0.19f
        paint.color = Color.rgb(232, 232, 232)
        canvas.drawText(levelLabel, textRight, bounds.top + height * 0.29f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = height * 0.17f
        paint.color = MUTED
        val hpLabel = hp.substringBefore(' ')
        val hpWidth = paint.measureText(hpLabel)
        val trainerAvailableWidth = (textRight - textLeft - hpWidth - 12f * scale).coerceAtLeast(0f)
        canvas.drawText(
            ellipsizeToWidth(trainer.uppercase(Locale.ROOT), trainerAvailableWidth, paint),
            textLeft,
            bounds.top + height * 0.51f,
            paint
        )
        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.rgb(238, 238, 238)
        canvas.drawText(hpLabel, textRight, bounds.top + height * 0.51f, paint)
        paint.textAlign = Paint.Align.LEFT
        val barTop = bounds.top + height * 0.55f
        val barBottom = barTop + height * 0.15f
        val track = RectF(textLeft, barTop, textRight, barBottom)
        paint.shader = LinearGradient(track.left, track.top, track.left, track.bottom, Color.rgb(55, 63, 72), Color.rgb(12, 17, 22), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(track, height * 0.07f, height * 0.07f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(150, 229, 238, 245)
        canvas.drawRoundRect(RectF(track.left + scale, track.top + scale, track.right - scale, track.bottom - scale), height * 0.06f, height * 0.06f, paint)
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
            canvas.drawRoundRect(fill, height * 0.045f, height * 0.045f, paint)
            paint.shader = null
        }
        val ballSize = height * 0.17f
        val ballGap = ballSize * 0.12f
        val ballStart = textRight - ballSize * 6f - ballGap * 5f
        val ballTop = bounds.top + height * 0.75f
        drawPartyIndicators(canvas, party, ballStart, ballTop, ballSize, ballGap)
        canvas.restoreToCount(layer)
    }

    private fun drawActiveStatusCards(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        player: Boolean,
        combatants: List<BattleSession.ActiveCombatant>
    ) {
        val cardLeft = if (player) width * 0.015f else width * 0.685f
        val cardRight = if (player) width * 0.315f else width * 0.985f
        val cardHeight = height * if (combatants.size > 2) 0.06f else 0.085f
        val cardGap = height * if (combatants.size > 2) 0.008f else 0.012f
        val totalHeight = cardHeight * combatants.size + cardGap * (combatants.size - 1)
        val firstTop = if (player) height - totalHeight - height * 0.015f else height * 0.02f
        combatants.forEachIndexed { index, combatant ->
            val alpha = statusCardAlpha(combatant.name, combatant.condition, System.nanoTime()) *
                BattleSceneTiming.summonStatusCardAlpha(combatant.entryAtNanos, System.nanoTime())
            if (alpha > 0f) {
                drawCompactStatusCard(
                    canvas,
                    RectF(cardLeft, firstTop + index * (cardHeight + cardGap), cardRight, firstTop + index * (cardHeight + cardGap) + cardHeight),
                    combatant,
                    index,
                    player,
                    scale,
                    alpha
                )
            }
        }
    }

    private fun drawCompactStatusCard(
        canvas: Canvas,
        bounds: RectF,
        combatant: BattleSession.ActiveCombatant,
        index: Int,
        player: Boolean,
        scale: Float,
        alpha: Float
    ) {
        val layer = canvas.saveLayerAlpha(bounds, (alpha * 255f).toInt())
        val accent = if (player) CYAN else MAGENTA
        val height = bounds.height()
        paint.color = Color.argb(238, 16, 20, 26)
        canvas.drawRoundRect(bounds, height * 0.16f, height * 0.16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(RectF(bounds.left + scale, bounds.top + scale, bounds.right - scale, bounds.bottom - scale), height * 0.16f, height * 0.16f, paint)
        paint.style = Paint.Style.FILL
        val left = bounds.left + 14f * scale
        val right = bounds.right - 14f * scale
        val label = "${index + 1}  ${BattleSession.displayPokemonName(combatant.name, combatant.species)}${when {
            combatant.gMaxed -> " · G-MAX"
            combatant.dynamaxed -> " · MAX"
            else -> ""
        }}"
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(height * 0.25f, scale, 10.5f)
        paint.color = INK
        canvas.drawText(ellipsizeToWidth(label, right - left - 92f * scale, paint), left, bounds.top + height * 0.34f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = readableTextSize(height * 0.19f, scale, 9.5f)
        paint.color = accent
        canvas.drawText("Lv.${combatant.level}${combatant.gender}", right, bounds.top + height * 0.34f, paint)
        paint.textAlign = Paint.Align.LEFT
        val track = RectF(left, bounds.top + height * 0.56f, right - 66f * scale, bounds.top + height * 0.70f)
        paint.color = Color.rgb(47, 55, 65)
        canvas.drawRoundRect(track, height * 0.08f, height * 0.08f, paint)
        val fraction = healthFraction(combatant.hp)
        val colors = healthColors(fraction)
        val fill = RectF(track.left, track.top, track.left + track.width() * fraction, track.bottom)
        if (fill.right > fill.left) {
            paint.shader = LinearGradient(fill.left, fill.top, fill.left, fill.bottom, colors.highlight, colors.shadow, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(fill, height * 0.08f, height * 0.08f, paint)
            paint.shader = null
        }
        paint.textSize = readableTextSize(height * 0.18f, scale, 9.5f)
        paint.color = if (combatant.condition == "READY") MUTED else Color.rgb(255, 192, 103)
        canvas.drawText(combatant.condition, left, bounds.top + height * 0.89f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = readableTextSize(height * 0.18f, scale, 9.5f)
        paint.color = INK
        canvas.drawText(combatant.hp.substringBefore(' '), right, bounds.top + height * 0.89f, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.restoreToCount(layer)
    }

    private fun healthFraction(condition: String): Float {
        val values = condition.substringBefore(' ').split('/', limit = 2)
        val current = values.getOrNull(0)?.toFloatOrNull() ?: return if (condition.contains("FNT", true)) 0f else 1f
        val maximum = values.getOrNull(1)?.toFloatOrNull() ?: return 1f
        return if (maximum > 0f) (current / maximum).coerceIn(0f, 1f) else 0f
    }

    private fun readableTextSize(designPixels: Float, scale: Float, minimumSp: Float = 12f): Float = maxOf(
        designPixels * scale,
        minimumSp * resources.displayMetrics.density * resources.configuration.fontScale
    )

    private fun drawPartyIndicators(canvas: Canvas, party: List<BattleSession.PokemonDetails>, start: Float, top: Float, size: Float, gap: Float) {
        val sheet = pokeballSheet ?: return
        repeat(6) { index ->
            val pokemon = party.getOrNull(index)
            val spriteIndex = when {
                pokemon == null -> 0
                pokemon.condition.contains("FNT", true) -> 2
                pokemon.condition != "READY" -> 1
                else -> 0
            }
            val left = start + index * (size + gap)
            val cellWidth = sheet.width / 3
            val glyphSize = 12
            source.set(spriteIndex * cellWidth + 14, 10, spriteIndex * cellWidth + 14 + glyphSize, 10 + glyphSize)
            destination.set(left, top, left + size, top + size)
            canvas.drawBitmap(sheet, source, destination, paint)
        }
    }

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
        if (!session.battleFeedVisible) return
        val feedMessage = session.battleFeedText() ?: return
        val age = (System.nanoTime() - session.latestBattleEventAtNanos) / 1_000_000_000f
        val arrival = min(1f, age / 0.18f)
        val alpha = min(1f, 0.3f + arrival)
        val playerCardRight = width * 0.315f
        val sideGap = maxOf(48f * scale, width * 0.025f)
        val settledLeft = maxOf(width * 0.33f, playerCardRight + sideGap)
        val left = settledLeft + (1f - arrival) * width * 0.035f
        val right = width * 0.97f
        val top = height * 0.81f
        val bottom = min(height * 0.965f, height * 0.98f - 24f * scale)
        val bounds = RectF(left, top, right, bottom)
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
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textSize = 42f * scale
        val lines = BattleFeedText.wrap(feedMessage, bounds.width() - 48f * scale, 2, paint::measureText)
            .ifEmpty { listOf("…") }
        val lineHeight = 50f * scale
        val firstBaseline = bounds.centerY() - lines.size * lineHeight / 2f - (paint.ascent() + paint.descent()) / 2f + lineHeight / 2f
        canvas.save()
        canvas.clipRect(bounds)
        lines.forEachIndexed { index, line ->
            val baseline = firstBaseline + index * lineHeight
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

    private companion object {
        val SHOWDOWN_EFFECTS = listOf("pokeball.png")
        const val INK = 0xFFF0F7FF.toInt()
        const val CYAN = 0xFF4AE7FF.toInt()
        const val MAGENTA = 0xFFFF49B0.toInt()
        const val MUTED = 0xFFBBD1EA.toInt()
    }
}
