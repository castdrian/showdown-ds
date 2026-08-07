package com.showdown.ds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

class CommandDeckView(
    context: Context,
    private val session: BattleSession,
    private val spriteCache: ShowdownSpriteCache,
    private val interactionListener: InteractionListener
) : View(context) {
    private data class MovePalette(val highlight: Int, val base: Int, val shadow: Int, val edge: Int)

    private data class Effectiveness(val label: String, val color: Int)

    interface InteractionListener {
        fun onNavigation()
        fun onConfirmation()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private val tabBounds = arrayOfNulls<RectF>(4)
    private val moveBounds = arrayOfNulls<RectF>(4)
    private val menuBounds = arrayOfNulls<RectF>(10)
    private val gimmickBounds = arrayOfNulls<RectF>(4)
    private val teamSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset>()
    private val requestedTeamSprites = mutableSetOf<String>()
    private var activityChatBounds: RectF? = null
    private var zPowerSymbol: Bitmap? = null
    private var pressedMoveIndex: Int? = null
    private var pressStartedAt = 0L
    private var releasedMoveIndex: Int? = null
    private var releaseStartedAt = 0L

    init {
        spriteCache.requestEffect("z-symbol.png") { asset ->
            zPowerSymbol = asset
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val scale = minOf(width / 1240f, height / 1080f)
        drawBackground(canvas, width, height)
        drawTopBand(canvas, width, scale)
        drawTabs(canvas, width, scale)
        drawActivePanel(canvas, width, height, scale)
        if (pressedMoveIndex != null || releasedMoveIndex != null || session.selectedGimmick != null) postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedMoveIndex = moveBounds.indexOfFirst { it?.contains(x, y) == true }.takeIf { it >= 0 }
                if (pressedMoveIndex != null) {
                    pressStartedAt = SystemClock.elapsedRealtime()
                    releasedMoveIndex = null
                    postInvalidateOnAnimation()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                releasedMoveIndex = pressedMoveIndex
                releaseStartedAt = SystemClock.elapsedRealtime()
                pressedMoveIndex = null
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_UP -> {
                releasedMoveIndex = pressedMoveIndex
                releaseStartedAt = SystemClock.elapsedRealtime()
                pressedMoveIndex = null
                postInvalidateOnAnimation()
            }
            else -> return true
        }
        tabBounds.forEachIndexed { index, bounds ->
            if (bounds?.contains(x, y) == true) {
                session.selectPanel(TABS[index])
                interactionListener.onNavigation()
                return true
            }
        }
        if (session.panel == BattleSession.Panel.MOVES) {
            moveBounds.forEachIndexed { index, bounds ->
                if (bounds?.contains(x, y) == true) {
                    session.selectMoveWithTouch(index)
                    interactionListener.onConfirmation()
                    return true
                }
            }
            gimmickBounds.forEachIndexed { index, bounds ->
                if (bounds?.contains(x, y) == true) {
                    session.availableGimmicks().getOrNull(index)?.let(session::selectGimmick)
                    interactionListener.onConfirmation()
                    return true
                }
            }
        }
        if (session.panel == BattleSession.Panel.MENU) {
            menuBounds.forEachIndexed { index, bounds ->
                if (bounds?.contains(x, y) == true) {
                    session.selectMenuItem(index)
                    session.confirmSelection()
                    interactionListener.onConfirmation()
                    return true
                }
            }
        }
        if (session.panel == BattleSession.Panel.ACTIVITY && activityChatBounds?.contains(x, y) == true) {
            session.openChatComposer()
            interactionListener.onConfirmation()
            return true
        }
        return true
    }

    private fun drawBackground(canvas: Canvas, width: Float, height: Float) {
        paint.shader = LinearGradient(0f, 0f, width, height, Color.rgb(9, 27, 44), Color.rgb(17, 83, 79), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }

    private fun drawTopBand(canvas: Canvas, width: Float, scale: Float) {
        val band = RectF(20f * scale, 18f * scale, width - 20f * scale, 112f * scale)
        paint.shader = LinearGradient(band.left, band.top, band.right, band.bottom, Color.argb(226, 27, 42, 66), Color.argb(205, 12, 28, 48), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(band, 24f * scale, 24f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(95, 229, 245, 255)
        canvas.drawRoundRect(band, 24f * scale, 24f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 39f * scale
        paint.color = PAPER
        canvas.drawText("What will ${session.playerPokemon} do?", 42f * scale, 54f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 25f * scale
        paint.color = MUTED
        canvas.drawText(session.status, 43f * scale, 89f * scale, paint)
        val turnWidth = 142f * scale
        val turn = RectF(width - 44f * scale - turnWidth, 39f * scale, width - 44f * scale, 92f * scale)
        paint.color = Color.argb(185, 56, 197, 181)
        canvas.drawRoundRect(turn, 18f * scale, 18f * scale, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.color = PAPER
        paint.textSize = 24f * scale
        canvas.drawText("Turn ${session.turn}", turn.centerX(), turn.centerY() + 8f * scale, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTabs(canvas: Canvas, width: Float, scale: Float) {
        val top = 132f * scale
        val gap = 12f * scale
        val left = 34f * scale
        val tabWidth = (width - left * 2f - gap * 3f) / 4f
        val tabHeight = 68f * scale
        TABS.forEachIndexed { index, panel ->
            val tabLeft = left + index * (tabWidth + gap)
            val bounds = RectF(tabLeft, top, tabLeft + tabWidth, top + tabHeight)
            tabBounds[index] = bounds
            val selected = session.panel == panel
            if (selected) {
                paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, Color.rgb(47, 191, 178), Color.rgb(17, 112, 105), Shader.TileMode.CLAMP)
            } else {
                paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, Color.argb(188, 30, 54, 74), Color.argb(160, 13, 33, 50), Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(bounds, 16f * scale, 16f * scale, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * scale
            paint.color = if (selected) Color.argb(178, 224, 255, 250) else Color.argb(90, 224, 244, 255)
            canvas.drawRoundRect(bounds, 16f * scale, 16f * scale, paint)
            paint.style = Paint.Style.FILL
            if (selected) {
                paint.shader = LinearGradient(
                    bounds.left,
                    bounds.top + 5f * scale,
                    bounds.left,
                    bounds.centerY(),
                    Color.argb(82, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(
                    RectF(bounds.left + 4f * scale, bounds.top + 4f * scale, bounds.right - 4f * scale, bounds.bottom - 4f * scale),
                    13f * scale,
                    13f * scale,
                    paint
                )
                paint.shader = null
            }
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = 25f * scale
            paint.color = if (selected) PAPER else Color.rgb(214, 232, 242)
            canvas.drawText(tabName(panel), tabLeft + tabWidth / 2f, top + tabHeight * 0.60f, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawActivePanel(canvas: Canvas, width: Float, height: Float, scale: Float) {
        when (session.panel) {
            BattleSession.Panel.MOVES -> drawMoves(canvas, width, height, scale)
            BattleSession.Panel.TEAM -> drawTeam(canvas, width, scale)
            BattleSession.Panel.ACTIVITY -> drawActivity(canvas, width, height, scale)
            BattleSession.Panel.MENU -> drawMenu(canvas, width, scale)
        }
    }

    private fun drawMoves(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val moves = session.moves()
        val left = 38f * scale
        val top = 216f * scale
        val gap = 16f * scale
        val cardWidth = (width - left * 2f - gap) / 2f
        val cardHeight = minOf(266f * scale, (height - top - 240f * scale - gap) / 2f)
        repeat(4) { index ->
            val row = index / 2
            val column = index % 2
            val x = left + column * (cardWidth + gap)
            val y = top + row * (cardHeight + gap)
            val bounds = RectF(x, y, x + cardWidth, y + cardHeight)
            moveBounds[index] = bounds
            val move = moves.getOrNull(index)
            if (move == null) drawUnavailableMove(canvas, bounds, scale) else drawMoveCard(canvas, bounds, move, index == session.focusedMove, movePressProgress(index), scale)
        }
        drawGimmicks(canvas, RectF(left, top + cardHeight * 2f + gap + 22f * scale, width - left, height - 32f * scale), scale)
    }

    private fun drawMoveCard(canvas: Canvas, bounds: RectF, move: BattleSession.MoveOption, focused: Boolean, pressProgress: Float, scale: Float) {
        val palette = movePalette(move.type)
        val pressed = pressProgress > 0f
        val pressDepth = pressProgress * 10f * scale
        val card = RectF(bounds).apply { offset(0f, pressDepth) }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(210, Color.red(palette.shadow), Color.green(palette.shadow), Color.blue(palette.shadow))
        canvas.drawRoundRect(RectF(bounds.left, bounds.top + 13f * scale, bounds.right, bounds.bottom + 13f * scale), 24f * scale, 24f * scale, paint)
        paint.shader = LinearGradient(
            card.left,
            card.top,
            card.left,
            card.bottom,
            intArrayOf(palette.highlight, palette.base, palette.shadow),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(card, 24f * scale, 24f * scale, paint)
        paint.shader = null
        paint.shader = LinearGradient(
            card.left,
            card.top,
            card.left,
            card.top + card.height() * 0.34f,
            Color.argb(if (pressed) 20 else 62, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(card.left + 3f * scale, card.top + 3f * scale, card.right - 3f * scale, card.bottom - 3f * scale), 21f * scale, 21f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (focused) 5f * scale else 3f * scale
        paint.color = if (focused) PAPER else palette.edge
        canvas.drawRoundRect(RectF(card.left + 2f * scale, card.top + 2f * scale, card.right - 2f * scale, card.bottom - 2f * scale), 21f * scale, 21f * scale, paint)
        paint.style = Paint.Style.FILL
        drawMovePressAnimation(canvas, card, palette, pressProgress, scale)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = moveNameSize(move.name, scale)
        drawOutlinedText(canvas, move.name, card.centerX(), card.top + card.height() * 0.37f, Color.rgb(8, 18, 28), PAPER, 3f * scale)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 37f * scale
        drawOutlinedText(canvas, "PP ${move.pp}/${move.maxPp}", card.centerX(), card.top + card.height() * 0.61f, Color.rgb(8, 18, 28), PAPER, 2f * scale)
        val effectiveness = effectiveness(move.type)
        val labelBounds = RectF(card.left + 26f * scale, card.bottom - 66f * scale, card.right - 26f * scale, card.bottom - 20f * scale)
        paint.color = effectiveness.color
        canvas.drawRoundRect(labelBounds, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(170, 255, 255, 255)
        canvas.drawRoundRect(labelBounds, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 26f * scale
        drawOutlinedText(canvas, effectiveness.label, labelBounds.centerX(), labelBounds.centerY() + 9f * scale, Color.rgb(7, 17, 26), PAPER, 2f * scale)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawOutlinedText(canvas: Canvas, text: String, centerX: Float, baseline: Float, outline: Int, fill: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = outline
        canvas.drawText(text, centerX, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawText(text, centerX, baseline, paint)
    }

    private fun drawMovePressAnimation(canvas: Canvas, card: RectF, palette: MovePalette, progress: Float, scale: Float) {
        if (progress <= 0f) return
        val expansion = (1f - progress) * 16f * scale
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (3f + 5f * progress) * scale
        paint.color = Color.argb((70f + 140f * progress).toInt(), Color.red(palette.highlight), Color.green(palette.highlight), Color.blue(palette.highlight))
        canvas.drawRoundRect(RectF(card.left - expansion, card.top - expansion, card.right + expansion, card.bottom + expansion), 28f * scale, 28f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            card.centerX(),
            card.centerY(),
            card.width() * (0.28f + progress * 0.54f),
            intArrayOf(Color.argb((92f * progress).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(card, 24f * scale, 24f * scale, paint)
        paint.shader = null
    }

    private fun movePressProgress(index: Int): Float {
        val now = SystemClock.elapsedRealtime()
        if (pressedMoveIndex == index) return ((now - pressStartedAt) / 92f).coerceIn(0f, 1f)
        if (releasedMoveIndex == index) {
            val progress = ((now - releaseStartedAt) / 150f).coerceIn(0f, 1f)
            if (progress == 1f) releasedMoveIndex = null
            return 1f - progress
        }
        return 0f
    }

    private fun drawGimmicks(canvas: Canvas, bounds: RectF, scale: Float) {
        gimmickBounds.fill(null)
        val gimmicks = session.availableGimmicks()
        if (gimmicks.isEmpty()) return
        val phase = SystemClock.elapsedRealtime() / 1000f
        val gap = 14f * scale
        val width = (bounds.width() - gap * (gimmicks.size - 1)) / gimmicks.size
        gimmicks.forEachIndexed { index, gimmick ->
            val left = bounds.left + index * (width + gap)
            val card = RectF(left, bounds.top, left + width, bounds.bottom)
            gimmickBounds[index] = card
            val selected = session.selectedGimmick == gimmick
            val glow = (0.56f + 0.44f * kotlin.math.sin(phase * 4f + index)).coerceIn(0f, 1f)
            if (selected) {
                paint.shader = LinearGradient(
                    card.left,
                    card.top,
                    card.right,
                    card.bottom,
                    intArrayOf(0xFFFF7F7F.toInt(), 0xFFFFCC7F.toInt(), 0xFFFFFFA0.toInt(), 0xFFA8FFB2.toInt(), 0xFF99FFFF.toInt(), 0xFF8CCEFF.toInt(), 0xFF8F8FFF.toInt(), 0xFFD18CFF.toInt(), 0xFFFF85FF.toInt(), 0xFFFF7F7F.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            } else {
                paint.shader = LinearGradient(card.left, card.top, card.right, card.bottom, Color.rgb(31, 38, 46), Color.rgb(14, 20, 28), Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(card, 26f * scale, 26f * scale, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (selected) 7f * scale else 3f * scale
            paint.color = if (selected) Color.argb((180f + 75f * glow).toInt(), 255, 255, 255) else Color.rgb(136, 136, 136)
            canvas.drawRoundRect(card, 26f * scale, 26f * scale, paint)
            paint.style = Paint.Style.FILL
            val emblemSize = minOf(card.height() * 0.37f, if (gimmicks.size > 2) 72f * scale else 108f * scale)
            val emblemY = card.top + card.height() * 0.29f
            drawGimmickAsset(canvas, gimmick, card.centerX(), emblemY, emblemSize)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = if (gimmicks.size > 2) 26f * scale else 46f * scale
            paint.color = if (selected) Color.rgb(22, 22, 22) else PAPER
            canvas.drawText(gimmick.label, card.centerX(), card.top + card.height() * 0.66f, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawUnavailableMove(canvas: Canvas, bounds: RectF, scale: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(160, 36, 20, 48)
        canvas.drawRoundRect(bounds, 22f * scale, 22f * scale, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 27f * scale
        paint.color = Color.rgb(224, 191, 220)
        canvas.drawText("Unavailable", bounds.centerX(), bounds.centerY(), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun effectiveness(type: String): Effectiveness {
        val multiplier = session.opponentDetails().types.fold(1f) { total, defender -> total * typeMultiplier(type, defender) }
        return when {
            multiplier == 0f -> Effectiveness("No effect", 0xFF536370.toInt())
            multiplier > 1f -> Effectiveness("Super effective", 0xFF23885F.toInt())
            multiplier < 1f -> Effectiveness("Not very effective", 0xFFC45D3D.toInt())
            else -> Effectiveness("Effective", 0xFF226F9E.toInt())
        }
    }

    private fun typeMultiplier(attacking: String, defending: String): Float = when (attacking) {
        "NORMAL" -> when (defending) { "ROCK", "STEEL" -> 0.5f; "GHOST" -> 0f; else -> 1f }
        "FIGHTING" -> when (defending) { "NORMAL", "ROCK", "STEEL", "ICE", "DARK" -> 2f; "FLYING", "POISON", "BUG", "PSYCHIC", "FAIRY" -> 0.5f; "GHOST" -> 0f; else -> 1f }
        "FLYING" -> when (defending) { "FIGHTING", "BUG", "GRASS" -> 2f; "ROCK", "STEEL", "ELECTRIC" -> 0.5f; else -> 1f }
        "POISON" -> when (defending) { "GRASS", "FAIRY" -> 2f; "POISON", "GROUND", "ROCK", "GHOST" -> 0.5f; "STEEL" -> 0f; else -> 1f }
        "GROUND" -> when (defending) { "POISON", "ROCK", "STEEL", "FIRE", "ELECTRIC" -> 2f; "BUG", "GRASS" -> 0.5f; "FLYING" -> 0f; else -> 1f }
        "ROCK" -> when (defending) { "FLYING", "BUG", "FIRE", "ICE" -> 2f; "FIGHTING", "GROUND", "STEEL" -> 0.5f; else -> 1f }
        "BUG" -> when (defending) { "GRASS", "PSYCHIC", "DARK" -> 2f; "FIGHTING", "FLYING", "POISON", "GHOST", "STEEL", "FIRE", "FAIRY" -> 0.5f; else -> 1f }
        "GHOST" -> when (defending) { "GHOST", "PSYCHIC" -> 2f; "DARK" -> 0.5f; "NORMAL" -> 0f; else -> 1f }
        "STEEL" -> when (defending) { "ROCK", "ICE", "FAIRY" -> 2f; "STEEL", "FIRE", "WATER", "ELECTRIC" -> 0.5f; else -> 1f }
        "FIRE" -> when (defending) { "BUG", "STEEL", "GRASS", "ICE" -> 2f; "ROCK", "FIRE", "WATER", "DRAGON" -> 0.5f; else -> 1f }
        "WATER" -> when (defending) { "GROUND", "ROCK", "FIRE" -> 2f; "WATER", "GRASS", "DRAGON" -> 0.5f; else -> 1f }
        "GRASS" -> when (defending) { "GROUND", "ROCK", "WATER" -> 2f; "FLYING", "POISON", "BUG", "STEEL", "FIRE", "GRASS", "DRAGON" -> 0.5f; else -> 1f }
        "ELECTRIC" -> when (defending) { "FLYING", "WATER" -> 2f; "GRASS", "ELECTRIC", "DRAGON" -> 0.5f; "GROUND" -> 0f; else -> 1f }
        "PSYCHIC" -> when (defending) { "FIGHTING", "POISON" -> 2f; "STEEL", "PSYCHIC" -> 0.5f; "DARK" -> 0f; else -> 1f }
        "ICE" -> when (defending) { "FLYING", "GROUND", "GRASS", "DRAGON" -> 2f; "STEEL", "FIRE", "WATER", "ICE" -> 0.5f; else -> 1f }
        "DRAGON" -> when (defending) { "DRAGON" -> 2f; "STEEL" -> 0.5f; "FAIRY" -> 0f; else -> 1f }
        "DARK" -> when (defending) { "GHOST", "PSYCHIC" -> 2f; "FIGHTING", "DARK", "FAIRY" -> 0.5f; else -> 1f }
        "FAIRY" -> when (defending) { "FIGHTING", "DRAGON", "DARK" -> 2f; "POISON", "STEEL", "FIRE" -> 0.5f; else -> 1f }
        else -> 1f
    }

    private fun moveNameSize(name: String, scale: Float) = when {
        name.length > 15 -> 44f * scale
        name.length > 11 -> 50f * scale
        else -> 58f * scale
    }

    private fun drawGimmickAsset(
        canvas: Canvas,
        gimmick: BattleSession.BattleGimmick,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        if (gimmick != BattleSession.BattleGimmick.Z_POWER) return
        val symbol = zPowerSymbol ?: return
        source.set(0, 0, symbol.width, symbol.height)
        destination.set(centerX - size * 0.78f, centerY - size * 0.52f, centerX + size * 0.78f, centerY + size * 0.52f)
        canvas.drawBitmap(symbol, source, destination, paint)
    }

    private fun drawTeam(canvas: Canvas, width: Float, scale: Float) {
        val left = 44f * scale
        val top = 220f * scale
        val gap = 14f * scale
        val cardWidth = (width - left * 2f - gap * 2f) / 3f
        val cardHeight = 378f * scale
        session.team().forEachIndexed { index, pokemon ->
            val row = index / 3
            val column = index % 3
            val x = left + column * (cardWidth + gap)
            val y = top + row * (cardHeight + gap)
            val bounds = RectF(x, y, x + cardWidth, y + cardHeight)
            val focused = index == session.focusedTeam
            val details = session.teamMemberDetails(index)
            requestTeamSprite(pokemon)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                if (focused) Color.rgb(29, 113, 123) else Color.rgb(19, 53, 73),
                if (focused) Color.rgb(9, 67, 82) else Color.rgb(6, 28, 47),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(bounds, 24f * scale, 24f * scale, paint)
            paint.shader = null
            if (focused) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * scale
                paint.color = Color.rgb(224, 231, 237)
                canvas.drawRoundRect(RectF(bounds.left + 3f * scale, bounds.top + 3f * scale, bounds.right - 3f * scale, bounds.bottom - 3f * scale), 21f * scale, 21f * scale, paint)
                paint.style = Paint.Style.FILL
            }
            val spriteBounds = RectF(bounds.left + 16f * scale, bounds.top + 22f * scale, bounds.left + 150f * scale, bounds.top + 156f * scale)
            teamSprites[pokemon]?.draw(canvas, spriteBounds, SystemClock.elapsedRealtime())
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = if (pokemon.length > 11) 28f * scale else 34f * scale
            paint.color = PAPER
            canvas.drawText(pokemon, bounds.left + 164f * scale, bounds.top + 55f * scale, paint)
            paint.textSize = 24f * scale
            paint.color = MUTED
            canvas.drawText("Lv. ${details.level}${details.gender.ifBlank { "" }}", bounds.left + 164f * scale, bounds.top + 92f * scale, paint)
            drawTeamHp(canvas, RectF(bounds.left + 164f * scale, bounds.top + 112f * scale, bounds.right - 20f * scale, bounds.top + 140f * scale), details.hp, scale)
            var typeX = bounds.left + 18f * scale
            details.types.forEach { type ->
                val typeWidth = maxOf(92f * scale, paint.measureText(type) + 28f * scale)
                val typeBounds = RectF(typeX, bounds.top + 177f * scale, typeX + typeWidth, bounds.top + 217f * scale)
                paint.color = movePalette(type).base
                canvas.drawRoundRect(typeBounds, 13f * scale, 13f * scale, paint)
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                paint.textSize = 20f * scale
                drawOutlinedText(canvas, type, typeBounds.centerX(), typeBounds.centerY() + 7f * scale, Color.rgb(7, 18, 26), PAPER, 1.5f * scale)
                paint.textAlign = Paint.Align.LEFT
                typeX = typeBounds.right + 8f * scale
            }
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.textSize = 24f * scale
            paint.color = MUTED
            canvas.drawText("Ability: ${ellipsize(details.ability, 22)}", bounds.left + 18f * scale, bounds.top + 260f * scale, paint)
            canvas.drawText("Item: ${ellipsize(details.item, 24)}", bounds.left + 18f * scale, bounds.top + 300f * scale, paint)
            paint.textSize = 22f * scale
            val state = when {
                details.condition.contains("FNT", true) -> "Fainted"
                session.decisionKind == BattleSession.DecisionKind.SWITCH -> "Choose to switch in"
                session.decisionKind == BattleSession.DecisionKind.TEAM_PREVIEW -> "Team preview"
                pokemon.equals(session.playerPokemon, true) -> "In battle"
                else -> "Available"
            }
            paint.color = if (details.condition.contains("FNT", true)) MAGENTA else Color.rgb(150, 231, 205)
            canvas.drawText(state, bounds.left + 18f * scale, bounds.bottom - 27f * scale, paint)
        }
    }

    private fun requestTeamSprite(species: String) {
        if (teamSprites.containsKey(species) || !requestedTeamSprites.add(species)) return
        spriteCache.requestDexSprite(species) { sprite ->
            if (sprite != null) teamSprites[species] = sprite
            postInvalidateOnAnimation()
        }
    }

    private fun drawTeamHp(canvas: Canvas, bounds: RectF, hp: String, scale: Float) {
        val ratio = hp.substringBefore(' ').split('/').let { values ->
            values.getOrNull(0)?.toFloatOrNull()?.div(values.getOrNull(1)?.toFloatOrNull() ?: 1f)?.coerceIn(0f, 1f) ?: 0f
        }
        paint.color = Color.rgb(8, 19, 28)
        canvas.drawRoundRect(bounds, 12f * scale, 12f * scale, paint)
        val color = when {
            ratio > 0.5f -> Color.rgb(65, 205, 105)
            ratio > 0.2f -> Color.rgb(237, 183, 53)
            else -> Color.rgb(224, 76, 78)
        }
        paint.color = color
        canvas.drawRoundRect(RectF(bounds.left + 3f * scale, bounds.top + 3f * scale, bounds.left + maxOf(7f * scale, (bounds.width() - 6f * scale) * ratio), bounds.bottom - 3f * scale), 9f * scale, 9f * scale, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 24f * scale
        drawOutlinedText(canvas, "HP ${hp.substringBefore(' ')}", bounds.centerX(), bounds.centerY() + 8f * scale, Color.rgb(5, 14, 22), PAPER, 1.8f * scale)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawActivity(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val messages = session.activityMessages()
        val left = 44f * scale
        val top = 236f * scale
        val buttonHeight = 88f * scale
        val bottom = height - buttonHeight - 58f * scale
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(222, 7, 39, 54)
        canvas.drawRoundRect(RectF(left, top, width - left, bottom), 24f * scale, 24f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = 32f * scale
        paint.color = CYAN
        canvas.drawText("Activity", left + 28f * scale, top + 45f * scale, paint)
        var rowY = top + 94f * scale
        val start = maxOf(0, minOf(messages.size - 6, session.focusedMessage - 5))
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = 26f * scale
        for (index in start until messages.size) {
            if (rowY >= bottom - 24f * scale) break
            val focused = index == session.focusedMessage
            if (focused) {
                paint.color = Color.rgb(20, 119, 126)
                canvas.drawRoundRect(RectF(left + 14f * scale, rowY - 29f * scale, width - left - 14f * scale, rowY + 14f * scale), 10f * scale, 10f * scale, paint)
            }
            paint.color = if (focused || index % 2 == 0) PAPER else MUTED
            canvas.drawText(ellipsize(messages[index], 76), left + 28f * scale, rowY, paint)
            rowY += 48f * scale
        }
        activityChatBounds = RectF(left, height - buttonHeight - 28f * scale, width - left, height - 28f * scale)
        paint.shader = LinearGradient(
            activityChatBounds!!.left,
            activityChatBounds!!.top,
            activityChatBounds!!.right,
            activityChatBounds!!.bottom,
            Color.rgb(35, 150, 160),
            Color.rgb(16, 89, 111),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(activityChatBounds!!, 24f * scale, 24f * scale, paint)
        paint.shader = null
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 30f * scale
        paint.color = PAPER
        canvas.drawText("Send a message", activityChatBounds!!.centerX(), activityChatBounds!!.centerY() + 10f * scale, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawMenu(canvas: Canvas, width: Float, scale: Float) {
        val entries = listOf(
            "Find ${session.matchFormat.menuLabel}",
            "Battle format",
            "Battle chat",
            "Forfeit",
            "Sound effects: ${if (session.soundEffectsEnabled) "On" else "Off"}",
            "Music: ${if (session.musicEnabled) "On" else "Off"}",
            "Haptics: ${if (session.hapticsEnabled) "On" else "Off"}",
            "Tap confirmation: ${if (session.touchConfirmationEnabled) "On" else "Off"}",
            "Sprites: ${if (session.spriteStyle == BattleSession.SpriteStyle.MODERN_3D) "3D" else "Classic"}",
            "Server settings"
        )
        val left = 42f * scale
        val top = 224f * scale
        val gap = 16f * scale
        val cardWidth = (width - left * 2f - gap) / 2f
        val cardHeight = 122f * scale
        entries.forEachIndexed { index, entry ->
            val row = index / 2
            val column = index % 2
            val x = left + column * (cardWidth + gap)
            val y = top + row * (cardHeight + gap)
            val bounds = RectF(x, y, x + cardWidth, y + cardHeight)
            menuBounds[index] = bounds
            val focused = index == session.focusedMenuItem
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                if (focused) Color.rgb(45, 162, 157) else Color.argb(230, 26, 51, 71),
                if (focused) Color.rgb(14, 96, 111) else Color.argb(220, 10, 30, 48),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(bounds, 24f * scale, 24f * scale, paint)
            paint.shader = null
            if (focused) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * scale
                paint.color = Color.argb(220, 224, 255, 250)
                canvas.drawRoundRect(RectF(bounds.left + 3f * scale, bounds.top + 3f * scale, bounds.right - 3f * scale, bounds.bottom - 3f * scale), 20f * scale, 20f * scale, paint)
                paint.style = Paint.Style.FILL
            }
            drawMenuIcon(canvas, index, bounds.left + 48f * scale, bounds.centerY(), 22f * scale, if (index == 3) MAGENTA else PAPER)
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = if (entry.length > 24) 23f * scale else 29f * scale
            paint.color = if (index == 3) MAGENTA else PAPER
            canvas.drawText(entry, bounds.left + 84f * scale, bounds.centerY() + 9f * scale, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawMenuIcon(canvas: Canvas, index: Int, centerX: Float, centerY: Float, size: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        when (index) {
            0 -> {
                canvas.drawCircle(centerX - size * 0.18f, centerY - size * 0.18f, size * 0.47f, paint)
                canvas.drawLine(centerX + size * 0.16f, centerY + size * 0.16f, centerX + size * 0.62f, centerY + size * 0.62f, paint)
            }
            1 -> repeat(3) { row -> canvas.drawLine(centerX - size * 0.62f, centerY + (row - 1) * size * 0.46f, centerX + size * 0.62f, centerY + (row - 1) * size * 0.46f, paint) }
            2 -> canvas.drawRoundRect(RectF(centerX - size * 0.68f, centerY - size * 0.5f, centerX + size * 0.68f, centerY + size * 0.4f), size * 0.22f, size * 0.22f, paint)
            3 -> {
                canvas.drawLine(centerX - size * 0.55f, centerY + size * 0.62f, centerX - size * 0.55f, centerY - size * 0.65f, paint)
                canvas.drawLine(centerX - size * 0.52f, centerY - size * 0.59f, centerX + size * 0.58f, centerY - size * 0.26f, paint)
                canvas.drawLine(centerX + size * 0.58f, centerY - size * 0.26f, centerX - size * 0.52f, centerY + size * 0.06f, paint)
            }
            4 -> {
                canvas.drawLine(centerX - size * 0.68f, centerY - size * 0.18f, centerX - size * 0.32f, centerY - size * 0.18f, paint)
                canvas.drawLine(centerX - size * 0.32f, centerY - size * 0.18f, centerX + size * 0.18f, centerY - size * 0.58f, paint)
                canvas.drawLine(centerX - size * 0.32f, centerY + size * 0.18f, centerX + size * 0.18f, centerY + size * 0.58f, paint)
                canvas.drawArc(RectF(centerX - size * 0.04f, centerY - size * 0.45f, centerX + size * 0.78f, centerY + size * 0.45f), -55f, 110f, false, paint)
            }
            5 -> {
                canvas.drawLine(centerX + size * 0.15f, centerY - size * 0.64f, centerX + size * 0.15f, centerY + size * 0.45f, paint)
                canvas.drawLine(centerX + size * 0.15f, centerY - size * 0.64f, centerX + size * 0.65f, centerY - size * 0.46f, paint)
                canvas.drawCircle(centerX - size * 0.3f, centerY + size * 0.5f, size * 0.28f, paint)
            }
            6 -> {
                canvas.drawCircle(centerX, centerY, size * 0.23f, paint)
                canvas.drawCircle(centerX, centerY, size * 0.55f, paint)
            }
            7 -> {
                canvas.drawCircle(centerX, centerY - size * 0.22f, size * 0.25f, paint)
                canvas.drawLine(centerX, centerY + size * 0.04f, centerX, centerY + size * 0.62f, paint)
                canvas.drawLine(centerX, centerY + size * 0.27f, centerX + size * 0.45f, centerY + size * 0.27f, paint)
            }
            8 -> {
                canvas.drawCircle(centerX, centerY, size * 0.48f, paint)
                canvas.drawLine(centerX - size * 0.65f, centerY, centerX + size * 0.65f, centerY, paint)
                canvas.drawLine(centerX, centerY - size * 0.65f, centerX, centerY + size * 0.65f, paint)
            }
            else -> repeat(3) { row -> canvas.drawRoundRect(RectF(centerX - size * 0.58f, centerY - size * 0.6f + row * size * 0.48f, centerX + size * 0.58f, centerY - size * 0.28f + row * size * 0.48f), size * 0.08f, size * 0.08f, paint) }
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun tabName(panel: BattleSession.Panel) = when (panel) {
        BattleSession.Panel.MOVES -> "Fight"
        BattleSession.Panel.TEAM -> "Pokémon"
        BattleSession.Panel.ACTIVITY -> "Activity"
        BattleSession.Panel.MENU -> "Menu"
    }

    private fun movePalette(type: String) = when (type) {
        "FIGHTING" -> MovePalette(0xFFF06B82.toInt(), 0xFFD3425F.toInt(), 0xFF702131.toInt(), 0xFFFFB5C1.toInt())
        "FLYING" -> MovePalette(0xFFB9D3FF.toInt(), 0xFF7398D0.toInt(), 0xFF2E4D7C.toInt(), 0xFFE1EDFF.toInt())
        "POISON" -> MovePalette(0xFFE497F5.toInt(), 0xFFB763CF.toInt(), 0xFF592469.toInt(), 0xFFF3C9FF.toInt())
        "GROUND" -> MovePalette(0xFFFFC989.toInt(), 0xFFD69654.toInt(), 0xFF70401D.toInt(), 0xFFFFE0BB.toInt())
        "ROCK" -> MovePalette(0xFFF0D078.toInt(), 0xFFB99A4B.toInt(), 0xFF5D4A20.toInt(), 0xFFFFE6A6.toInt())
        "BUG" -> MovePalette(0xFFD2E468.toInt(), 0xFF92A43A.toInt(), 0xFF46551A.toInt(), 0xFFE7F8A3.toInt())
        "GHOST" -> MovePalette(0xFFB198DE.toInt(), 0xFF70559E.toInt(), 0xFF352456.toInt(), 0xFFD8CBFF.toInt())
        "STEEL" -> MovePalette(0xFFD7E2EB.toInt(), 0xFF8899A8.toInt(), 0xFF3F4D58.toInt(), 0xFFF0F6FA.toInt())
        "FIRE" -> MovePalette(0xFFFF9970.toInt(), 0xFFEA6848.toInt(), 0xFF742616.toInt(), 0xFFFFC9B7.toInt())
        "WATER" -> MovePalette(0xFF71B7FF.toInt(), 0xFF3B82D0.toInt(), 0xFF163B78.toInt(), 0xFFBFE1FF.toInt())
        "GRASS" -> MovePalette(0xFF8FEA92.toInt(), 0xFF4EA75A.toInt(), 0xFF1D592B.toInt(), 0xFFC3F7BF.toInt())
        "ELECTRIC" -> MovePalette(0xFFFFE36C.toInt(), 0xFFD8A416.toInt(), 0xFF6C4D08.toInt(), 0xFFFFF2B3.toInt())
        "PSYCHIC" -> MovePalette(0xFFFF95B7.toInt(), 0xFFD85583.toInt(), 0xFF6E1D3C.toInt(), 0xFFFFC5D8.toInt())
        "ICE" -> MovePalette(0xFFB8F0FA.toInt(), 0xFF53B9CC.toInt(), 0xFF1C6071.toInt(), 0xFFD8FAFF.toInt())
        "DRAGON" -> MovePalette(0xFFA494FF.toInt(), 0xFF6959CC.toInt(), 0xFF30256F.toInt(), 0xFFD4CCFF.toInt())
        "DARK" -> MovePalette(0xFF9D8073.toInt(), 0xFF705548.toInt(), 0xFF34221D.toInt(), 0xFFD5B9A9.toInt())
        "FAIRY" -> MovePalette(0xFFFFB6E3.toInt(), 0xFFDD76B6.toInt(), 0xFF732B58.toInt(), 0xFFFFD6EF.toInt())
        else -> MovePalette(0xFFE5E8E6.toInt(), 0xFF999C99.toInt(), 0xFF454946.toInt(), 0xFFF7F8F6.toInt())
    }

    private fun ellipsize(text: String, maximum: Int) = if (text.length <= maximum) text else "${text.take(maximum - 1)}…"

    private companion object {
        val TABS = arrayOf(BattleSession.Panel.MOVES, BattleSession.Panel.TEAM, BattleSession.Panel.ACTIVITY, BattleSession.Panel.MENU)
        const val PAPER = 0xFFEFF8FF.toInt()
        const val CYAN = 0xFF3EE5FF.toInt()
        const val MAGENTA = 0xFFFF4AB0.toInt()
        const val MUTED = 0xFF97B1D1.toInt()
    }
}
