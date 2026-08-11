package dev.adrian.showdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class CommandDeckView(
    context: Context,
    private val session: BattleSession,
    private val spriteCache: ShowdownSpriteCache,
    private val interactionListener: InteractionListener
) : View(context) {
    private data class MovePalette(val highlight: Int, val base: Int, val shadow: Int, val edge: Int)

    interface InteractionListener {
        fun onNavigation()
        fun onConfirmation()
        fun onCancelChoice()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private val tabBounds = arrayOfNulls<RectF>(4)
    private val moveBounds = arrayOfNulls<RectF>(4)
    private val teamBounds = arrayOfNulls<RectF>(6)
    private val menuBounds = arrayOfNulls<RectF>(BattleSession.MENU_ITEM_COUNT)
    private val gimmickBounds = arrayOfNulls<RectF>(7)
    private val targetBounds = arrayOfNulls<RectF>(4)
    private val teamSprites = mutableMapOf<String, ShowdownSpriteCache.SpriteAsset>()
    private val requestedTeamSprites = mutableSetOf<String>()
    private val typeIcons = mutableMapOf<String, Bitmap?>()
    private var activityChatBounds: RectF? = null
    private var cancelChoiceBounds: RectF? = null
    private var shiftBounds: RectF? = null
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
        val scale = minOf(
            width / ThorDisplayProfile.LOWER_WIDTH_PIXELS,
            height / ThorDisplayProfile.LOWER_HEIGHT_PIXELS
        )
        drawBackground(canvas, width, height)
        drawTabs(canvas, width, scale)
        drawActivePanel(canvas, width, height, scale)
        drawTopBand(canvas, width, scale)
        if (pressedMoveIndex != null || releasedMoveIndex != null || session.selectedGimmick != null) {
            postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
        } else if (session.battleClockSeconds() != null) {
            postInvalidateDelayed(1000L)
        }
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
            if (session.canShift() && shiftBounds?.contains(x, y) == true) {
                session.selectShiftWithTouch()
                interactionListener.onConfirmation()
                return true
            }
            if (session.canCancelChoice() && cancelChoiceBounds?.contains(x, y) == true) {
                interactionListener.onCancelChoice()
                return true
            }
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
            targetBounds.forEachIndexed { index, bounds ->
                if (bounds?.contains(x, y) == true) {
                    session.selectTargetWithTouch(index)
                    interactionListener.onConfirmation()
                    return true
                }
            }
        }
        if (session.panel == BattleSession.Panel.TEAM) {
            teamBounds.forEachIndexed { index, bounds ->
                if (bounds?.contains(x, y) == true) {
                    session.selectTeamWithTouch(index)
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
        paint.shader = LinearGradient(0f, 0f, width, height, Color.rgb(8, 17, 28), Color.rgb(18, 43, 47), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = RadialGradient(width * 0.84f, height * 0.12f, width * 0.72f, Color.argb(48, 78, 205, 231), Color.argb(0, 78, 205, 231), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }

    private fun drawTopBand(canvas: Canvas, width: Float, scale: Float) {
        paint.alpha = 255
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val band = RectF(20f * scale, 16f * scale, width - 20f * scale, 92f * scale)
        paint.shader = LinearGradient(band.left, band.top, band.right, band.bottom, Color.rgb(18, 41, 59), Color.rgb(7, 21, 35), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(band, 24f * scale, 24f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.argb(132, 104, 165, 190)
        canvas.drawRoundRect(band, 24f * scale, 24f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(40f, scale, 34f)
        paint.color = Color.rgb(226, 238, 244)
        paint.textAlign = Paint.Align.CENTER
        val titleBaseline = band.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(
            when {
                session.isReplayMode() -> "Replay"
                session.isBattleFinished() -> "Battle complete"
                session.isLiveBattleActive() -> "Battle"
                else -> "Lobby"
            },
            band.centerX(),
            titleBaseline,
            paint
        )
        drawBattleClock(canvas, width, scale)
    }

    private fun drawBattleClock(canvas: Canvas, width: Float, scale: Float) {
        val seconds = session.battleClockSeconds() ?: return
        val badge = RectF(width - 188f * scale, 26f * scale, width - 36f * scale, 82f * scale)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(210, 24, 70, 83)
        canvas.drawRoundRect(badge, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.rgb(132, 218, 213)
        canvas.drawRoundRect(badge, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(24f, scale, 21f)
        paint.color = PAPER
        canvas.drawText("${seconds}s", badge.centerX(), centeredTextBaseline(badge.centerY()), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTabs(canvas: Canvas, width: Float, scale: Float) {
        val top = 108f * scale
        val gap = 12f * scale
        val left = 34f * scale
        val tabWidth = (width - left * 2f - gap * 3f) / 4f
        val tabHeight = 56f * scale
        TABS.forEachIndexed { index, panel ->
            val tabLeft = left + index * (tabWidth + gap)
            val bounds = RectF(tabLeft, top, tabLeft + tabWidth, top + tabHeight)
            tabBounds[index] = bounds
            val selected = session.panel == panel
            if (selected) {
                paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, Color.rgb(29, 122, 133), Color.rgb(12, 73, 101), Shader.TileMode.CLAMP)
            } else {
                paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, Color.argb(168, 37, 67, 86), Color.argb(154, 13, 31, 48), Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (selected) 2.5f * scale else 1.25f * scale
            paint.color = if (selected) Color.rgb(131, 204, 200) else Color.argb(110, 166, 197, 216)
            canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(32f, scale, 28f)
            paint.color = if (selected) Color.rgb(225, 240, 242) else Color.rgb(198, 215, 226)
            canvas.drawText(tabName(panel), tabLeft + tabWidth / 2f, centeredTextBaseline(bounds.centerY()), paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawActivePanel(canvas: Canvas, width: Float, height: Float, scale: Float) {
        when (session.panel) {
            BattleSession.Panel.MOVES -> drawMoves(canvas, width, height, scale)
            BattleSession.Panel.TEAM -> drawTeam(canvas, width, height, scale)
            BattleSession.Panel.ACTIVITY -> drawActivity(canvas, width, height, scale)
            BattleSession.Panel.MENU -> drawMenu(canvas, width, height, scale)
        }
    }

    private fun drawMoves(canvas: Canvas, width: Float, height: Float, scale: Float) {
        if (!session.isLiveBattleActive() && !session.isBattleFinished()) {
            moveBounds.fill(null)
            gimmickBounds.fill(null)
            targetBounds.fill(null)
            drawEmptyPanel(
                canvas,
                width,
                height,
                scale,
                "No battle in progress",
                "Find a battle before choosing a move.",
                "Open Menu  ·  Find battle"
            )
            return
        }
        if (session.isBattleFinished()) {
            moveBounds.fill(null)
            gimmickBounds.fill(null)
            targetBounds.fill(null)
            drawCompletedBattle(canvas, width, height, scale)
            return
        }
        val moves = session.moves()
        val panelTop = 184f * scale
        val left = 44f * scale
        val consoleRight = 438f * scale
        val moveLeft = 470f * scale
        val moveRight = width - 38f * scale
        val gap = 12f * scale
        val cardHeight = minOf(158f * scale, (height - panelTop - 188f * scale - gap * 3f) / 4f)
        val panelBottom = panelTop + cardHeight * 4f + gap * 3f
        drawBattleConsole(canvas, RectF(left, panelTop, consoleRight, panelBottom), scale)
        repeat(4) { index ->
            val y = panelTop + index * (cardHeight + gap)
            val bounds = RectF(moveLeft, y, moveRight, y + cardHeight)
            moveBounds[index] = bounds
            val move = moves.getOrNull(index)
            if (move == null) drawUnavailableMove(canvas, bounds, scale) else drawMoveRow(canvas, bounds, move, index == session.focusedMove, movePressProgress(index), scale)
        }
    }

    private fun drawTargets(canvas: Canvas, left: Float, right: Float, top: Float, scale: Float) {
        targetBounds.fill(null)
        val targets = session.targetOptions()
        if (targets.isEmpty()) return
        val gap = 16f * scale
        val targetWidth = (right - left - gap * (targets.size - 1)) / targets.size
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = fittedTextSize("TARGET", right - left - 24f * scale, readableTextSize(24f, scale, 21f), 18f * scale)
        paint.color = Color.rgb(153, 224, 220)
        canvas.drawText("TARGET", left, top + 18f * scale, paint)
        targets.forEachIndexed { index, target ->
            val bounds = RectF(left + index * (targetWidth + gap), top + 28f * scale, left + index * (targetWidth + gap) + targetWidth, top + 82f * scale)
            targetBounds[index] = bounds
            val selected = session.status == "Target: ${target.label}"
            paint.color = if (selected) Color.rgb(31, 122, 133) else Color.rgb(20, 53, 70)
            canvas.drawRoundRect(bounds, 16f * scale, 16f * scale, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (selected) 2.5f * scale else 1.25f * scale
            paint.color = if (selected) Color.rgb(190, 255, 226) else Color.rgb(112, 176, 196)
            canvas.drawRoundRect(bounds, 16f * scale, 16f * scale, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = fittedTextSize(target.label, bounds.width() - 24f * scale, readableTextSize(24f, scale, 21f), 18f * scale)
            paint.color = PAPER
            canvas.drawText(target.label, bounds.centerX(), bounds.centerY() + 7f * scale, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawCompletedBattle(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val card = RectF(42f * scale, 246f * scale, width - 42f * scale, 590f * scale)
        paint.shader = LinearGradient(card.left, card.top, card.right, card.bottom, Color.rgb(27, 58, 74), Color.rgb(10, 26, 41), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card, 32f * scale, 32f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(184, 112, 216, 255)
        canvas.drawRoundRect(card, 32f * scale, 32f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(46f, scale)
        paint.color = PAPER
        canvas.drawText("Battle complete", card.centerX(), card.top + 128f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = readableTextSize(30f, scale)
        paint.color = Color.rgb(190, 218, 235)
        canvas.drawText(session.status, card.centerX(), card.top + 190f * scale, paint)
        paint.textSize = readableTextSize(25f, scale)
        paint.color = Color.rgb(129, 205, 236)
        canvas.drawText("Open Menu to find another battle", card.centerX(), card.top + 258f * scale, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawBattleConsole(canvas: Canvas, bounds: RectF, scale: Float) {
        paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, Color.rgb(17, 47, 63), Color.rgb(6, 21, 34), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, 25f * scale, 25f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.argb(174, 117, 187, 211)
        canvas.drawRoundRect(bounds, 25f * scale, 25f * scale, paint)
        paint.style = Paint.Style.FILL
        targetBounds.fill(null)
        gimmickBounds.fill(null)
        shiftBounds = null
        val inset = 18f * scale
        val content = RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        val targets = session.targetOptions()
        val gimmicks = session.availableGimmicks()
        var contentTop = content.top
        if (session.canShift()) {
            shiftBounds = RectF(content.left, contentTop, content.right, contentTop + 66f * scale)
            drawShiftButton(canvas, shiftBounds!!, scale)
            contentTop += 80f * scale
        }
        if (targets.isNotEmpty()) {
            drawTargets(canvas, content.left, content.right, contentTop, scale)
            contentTop += 112f * scale
        }
        if (gimmicks.isNotEmpty()) {
            val gimmickHeight = minOf(214f * scale, content.bottom - contentTop - 244f * scale)
            val gimmickBounds = RectF(content.left, contentTop, content.right, contentTop + gimmickHeight)
            drawGimmicks(canvas, gimmickBounds, scale)
            drawMoveDetails(
                canvas,
                RectF(content.left, gimmickBounds.bottom + 14f * scale, content.right, content.bottom),
                scale
            )
        } else {
            drawMoveDetails(canvas, RectF(content.left, contentTop, content.right, content.bottom), scale)
        }
    }

    private fun drawShiftButton(canvas: Canvas, bounds: RectF, scale: Float) {
        paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, Color.rgb(41, 124, 132), Color.rgb(17, 63, 77), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.rgb(129, 227, 216)
        canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(25f, scale, 22f)
        paint.color = PAPER
        canvas.drawText("SHIFT", bounds.centerX(), centeredTextBaseline(bounds.centerY()), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawMoveDetails(canvas: Canvas, bounds: RectF, scale: Float) {
        if (!session.decisionAvailable) {
            drawUtilityMessage(canvas, bounds, scale)
            return
        }
        val move = session.moves().getOrNull(session.focusedMove)
        if (move == null) {
            drawUtilityMessage(canvas, bounds, scale)
            return
        }
        val palette = movePalette(move.type)
        val compact = bounds.height() < 520f * scale
        val veryCompact = bounds.height() < 400f * scale
        val detailPadding = if (compact) 20f * scale else 24f * scale
        val detailContent = RectF(
            bounds.left + detailPadding,
            bounds.top + detailPadding,
            bounds.right - detailPadding,
            bounds.bottom - detailPadding
        )
        paint.color = Color.argb(178, 3, 14, 24)
        canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.25f * scale
        paint.color = Color.argb(150, Color.red(palette.edge), Color.green(palette.edge), Color.blue(palette.edge))
        canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
        paint.style = Paint.Style.FILL
        if (veryCompact) {
            drawCompactMoveDetails(canvas, detailContent, move, scale)
            return
        }
        var sectionTop = detailContent.top
        val infoCellHeight = 136f * scale
        drawMoveMetrics(
            canvas,
            RectF(detailContent.left, sectionTop, detailContent.right, sectionTop + infoCellHeight),
            move,
            scale
        )
        val metricToContextGap = if (compact) 34f * scale else 48f * scale
        sectionTop += infoCellHeight + metricToContextGap
        drawMoveContext(
            canvas,
            RectF(detailContent.left, sectionTop, detailContent.right, minOf(detailContent.bottom, sectionTop + infoCellHeight)),
            move,
            scale
        )
        sectionTop += infoCellHeight + if (compact) 10f * scale else 16f * scale
        if (!compact) drawEffectSummary(canvas, detailContent, scale, sectionTop)
        if (move.disabled) {
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(23f, scale, 20f)
            paint.color = MAGENTA
            canvas.drawText("DISABLED", detailContent.left, centeredTextBaseline(detailContent.bottom - 24f * scale), paint)
        }
    }

    private fun drawCompactMoveDetails(
        canvas: Canvas,
        detailContent: RectF,
        move: BattleSession.MoveOption,
        scale: Float
    ) {
        val content = RectF(detailContent)
        val accuracy = move.accuracy.takeUnless { it == "—" }?.let { "$it%" } ?: "—"
        drawCompactMetricLine(canvas, content, "PWR ${move.power}  ·  ACC $accuracy", scale)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawMoveMetrics(canvas: Canvas, bounds: RectF, move: BattleSession.MoveOption, scale: Float) {
        val gap = 8f * scale
        val cellWidth = (bounds.width() - gap) / 2f
        val accuracy = move.accuracy.takeUnless { it == "—" }?.let { "$it%" } ?: "—"
        drawMoveMetricCell(
            canvas,
            RectF(bounds.left, bounds.top, bounds.left + cellWidth, bounds.bottom),
            "POWER",
            move.power,
            scale
        )
        drawMoveMetricCell(
            canvas,
            RectF(bounds.right - cellWidth, bounds.top, bounds.right, bounds.bottom),
            "ACCURACY",
            accuracy,
            scale
        )
    }

    private fun drawMoveMetricCell(canvas: Canvas, bounds: RectF, label: String, value: String, scale: Float) {
        drawMoveInfoCell(canvas, bounds, label, value, scale)
    }

    private fun drawMoveContext(canvas: Canvas, bounds: RectF, move: BattleSession.MoveOption, scale: Float) {
        if (bounds.height() < 48f * scale) return
        val gap = 8f * scale
        val cellWidth = (bounds.width() - gap) / 2f
        drawMoveContextCell(
            canvas,
            RectF(bounds.left, bounds.top, bounds.left + cellWidth, bounds.bottom),
            "CATEGORY",
            moveCategoryLabel(move.category),
            scale
        )
        drawMoveContextCell(
            canvas,
            RectF(bounds.right - cellWidth, bounds.top, bounds.right, bounds.bottom),
            "TARGET",
            moveTargetLabel(move.target),
            scale
        )
    }

    private fun drawMoveContextCell(canvas: Canvas, bounds: RectF, label: String, value: String, scale: Float) {
        drawMoveInfoCell(canvas, bounds, label, value, scale)
    }

    private fun drawMoveInfoCell(canvas: Canvas, bounds: RectF, label: String, value: String, scale: Float) {
        val radius = 18f * scale
        val inner = RectF(bounds).apply { inset(2f * scale, 2f * scale) }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            inner.left,
            inner.top,
            inner.right,
            inner.bottom,
            Color.argb(226, 22, 63, 78),
            Color.argb(204, 4, 24, 38),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(inner, radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.75f * scale
        paint.color = Color.argb(194, 128, 218, 222)
        canvas.drawRoundRect(inner, radius, radius, paint)

        val horizontalPadding = 24f * scale
        val verticalPadding = 16f * scale
        val content = RectF(
            inner.left + horizontalPadding,
            inner.top + verticalPadding,
            inner.right - horizontalPadding,
            inner.bottom - verticalPadding
        )
        val labelHeight = 28f * scale
        val dividerY = content.top + labelHeight + 8f * scale
        val valueArea = RectF(content.left, dividerY + 8f * scale, content.right, content.bottom)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(108, 145, 230, 226)
        canvas.drawRoundRect(
            RectF(content.left, dividerY, content.right, dividerY + 1.5f * scale),
            1f * scale,
            1f * scale,
            paint
        )
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        val labelSize = fittedTextSize(
            label,
            content.width(),
            readableTextSize(16f, scale, 14f, 12f).coerceAtMost(labelHeight * 0.78f),
            12f * scale
        )
        paint.textSize = labelSize
        paint.color = Color.rgb(184, 238, 235)
        canvas.drawText(label, content.centerX(), centeredTextBaseline(content.top + labelHeight / 2f), paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        val valueSize = fittedTextSize(
            value,
            valueArea.width(),
            readableTextSize(29f, scale, 22f, 18f).coerceAtMost(valueArea.height() * 0.72f),
            18f * scale
        )
        paint.textSize = valueSize
        paint.color = PAPER
        canvas.drawText(value, valueArea.centerX(), centeredTextBaseline(valueArea.centerY()), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun moveCategoryLabel(category: String): String = when (category.lowercase()) {
        "physical" -> "PHYSICAL"
        "special" -> "SPECIAL"
        "status" -> "STATUS"
        else -> category.trim().uppercase().ifBlank { "UNKNOWN" }
    }

    private fun moveTargetLabel(target: String): String = when (target.lowercase()) {
        "normal" -> "ONE FOE"
        "any" -> "ANY TARGET"
        "self" -> "SELF"
        "ally" -> "ALLY"
        "adjacentally" -> "ADJACENT ALLY"
        "adjacentallyorself" -> "ALLY OR SELF"
        "adjacentfoe" -> "ADJACENT FOE"
        "adjacentfoes" -> "ADJACENT FOES"
        "alladjacent" -> "ALL ADJACENT"
        "alladjacentfoes" -> "ALL FOES"
        "randomnormal" -> "RANDOM FOE"
        "allies" -> "ALLIES"
        "foes" -> "ALL FOES"
        "foe" -> "FOE"
        "all" -> "ALL TARGETS"
        else -> target.trim().replace('_', ' ').replace('-', ' ').uppercase().ifBlank { "DEFAULT" }
    }

    private fun drawCompactMetricLine(canvas: Canvas, bounds: RectF, text: String, scale: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(168, 2, 13, 22)
        canvas.drawRoundRect(bounds, 14f * scale, 14f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.25f * scale
        paint.color = Color.argb(130, 141, 196, 211)
        canvas.drawRoundRect(bounds, 14f * scale, 14f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = fittedTextSize(
            text,
            bounds.width() - 24f * scale,
            readableTextSize(18f, scale, 16f, 15f).coerceAtMost(bounds.height() - 6f * scale),
            18f * scale
        )
        paint.color = PAPER
        drawOutlinedText(
            canvas,
            text,
            bounds.centerX(),
            centeredTextBaseline(bounds.centerY()),
            Color.rgb(3, 14, 22),
            PAPER,
            1.5f * scale
        )
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawEffectSummary(canvas: Canvas, detailContent: RectF, scale: Float, top: Float) {
        val info = session.battleInfo()
        val effects = mutableListOf<String>()
        info.weather.takeIf { it.isNotBlank() }?.let { effects += "Weather $it" }
        info.terrain.takeIf { it.isNotBlank() }?.let { effects += "Terrain $it" }
        info.fieldEffects.forEach { effects += "Field $it" }
        info.playerSideConditions.takeIf { it.isNotEmpty() }?.let { effects += "Your side ${it.joinToString(" · ")}" }
        info.opponentSideConditions.takeIf { it.isNotEmpty() }?.let { effects += "Opp. side ${it.joinToString(" · ")}" }
        info.playerBoosts.takeIf { it.isNotEmpty() }?.let { effects += "Your boosts ${formatBoosts(it)}" }
        info.opponentBoosts.takeIf { it.isNotEmpty() }?.let { effects += "Opp. boosts ${formatBoosts(it)}" }
        if (effects.isEmpty()) return
        val summary = RectF(
            detailContent.left,
            top,
            detailContent.right,
            minOf(detailContent.bottom, top + 70f * scale)
        )
        if (summary.height() < 42f * scale) return
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(156, 3, 14, 24)
        canvas.drawRoundRect(summary, 16f * scale, 16f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.25f * scale
        paint.color = Color.argb(118, 107, 181, 196)
        canvas.drawRoundRect(summary, 16f * scale, 16f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = readableTextSize(18f, scale, 16f, 16f).coerceAtMost(summary.height() - 8f * scale)
        val visibleEffects = effects.take(1).let { values ->
            if (effects.size > values.size) values + "+${effects.size - values.size} more" else values
        }
        val text = "FIELD  ${visibleEffects.joinToString(" · ")}"
        val lines = wrapText(text, summary.width() - 24f * scale)
        paint.textAlign = Paint.Align.CENTER
        val lineHeight = paint.textSize * 1.05f
        lines.forEachIndexed { index, line ->
            val centerY = summary.centerY() + (index - (lines.lastIndex / 2f)) * lineHeight
            drawOutlinedText(
                canvas,
                line,
                summary.centerX(),
                centeredTextBaseline(centerY),
                Color.rgb(3, 14, 22),
                PAPER,
                1.5f * scale
            )
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun formatBoosts(boosts: Map<String, Int>): String = boosts.entries
        .sortedBy { it.key }
        .joinToString(" · ") { entry ->
            val amount = if (entry.value > 0) "+${entry.value}" else entry.value.toString()
            "${BOOST_NAMES[entry.key] ?: entry.key} $amount"
        }

    private fun drawUtilityMessage(canvas: Canvas, bounds: RectF, scale: Float) {
        cancelChoiceBounds = null
        val hasCancel = session.canCancelChoice()
        val cancelBounds = if (hasCancel) {
            RectF(
                bounds.left + 18f * scale,
                bounds.bottom - 104f * scale,
                bounds.right - 18f * scale,
                bounds.bottom - 20f * scale
            )
        } else {
            null
        }
        cancelChoiceBounds = cancelBounds
        val textTop = bounds.top + 24f * scale
        val textBottom = (cancelBounds?.top ?: bounds.bottom) - 28f * scale
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(24f, scale, 21f)
        val title = if (session.decisionAvailable) "LAST ACTION" else "WAITING"
        val titleHeight = paint.descent() - paint.ascent()
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        var messageSize = readableTextSize(30f, scale, 26f)
        val message = if (session.decisionAvailable) session.latestBattleEvent else session.status
        var messageLines: List<String>
        var messageLineHeight: Float
        var messageHeight: Float
        val groupGap = 18f * scale
        val availableHeight = (textBottom - textTop).coerceAtLeast(1f)
        do {
            paint.textSize = messageSize
            messageLines = wrapText(message, bounds.width() - 48f * scale)
            messageLineHeight = paint.textSize * 1.2f
            messageHeight = messageLineHeight * messageLines.size
            val groupHeight = titleHeight + groupGap + messageHeight
            if (groupHeight <= availableHeight || messageSize <= 12f * scale) break
            messageSize = (messageSize - 1f * scale).coerceAtLeast(12f * scale)
        } while (true)
        val groupHeight = titleHeight + groupGap + messageHeight
        val groupTop = textTop + ((availableHeight - groupHeight) / 2f).coerceAtLeast(0f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(24f, scale, 21f).coerceAtMost(titleHeight)
        paint.color = Color.rgb(153, 224, 220)
        canvas.drawText(title, bounds.centerX(), centeredTextBaseline(groupTop + titleHeight / 2f), paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = messageSize
        paint.color = PAPER
        messageLines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                bounds.centerX(),
                centeredTextBaseline(groupTop + titleHeight + groupGap + messageLineHeight * (index + 0.5f)),
                paint
            )
        }
        if (cancelBounds != null) {
            paint.shader = LinearGradient(
                cancelBounds.left,
                cancelBounds.top,
                cancelBounds.right,
                cancelBounds.bottom,
                Color.rgb(112, 64, 103),
                Color.rgb(67, 31, 67),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(cancelBounds, 18f * scale, 18f * scale, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * scale
            paint.color = Color.argb(188, 245, 157, 215)
            canvas.drawRoundRect(cancelBounds, 18f * scale, 18f * scale, paint)
            paint.style = Paint.Style.FILL
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            val buttonTextInset = 18f * scale
            paint.textSize = readableTextSize(22f, scale, 18f, 14f).coerceAtMost(26f * scale)
            paint.color = PAPER
            val buttonLabel = fitTextToWidth("CANCEL CHOICE", cancelBounds.width() - buttonTextInset * 2f)
            val baseline = cancelBounds.centerY() - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(buttonLabel, cancelBounds.centerX(), baseline, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawMoveRow(canvas: Canvas, bounds: RectF, move: BattleSession.MoveOption, focused: Boolean, pressProgress: Float, scale: Float) {
        val palette = movePalette(move.type)
        val pressDepth = pressProgress * 6f * scale
        val card = RectF(bounds).apply { offset(0f, pressDepth) }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(232, 1, 10, 18)
        canvas.drawPath(moveRowPath(RectF(bounds).apply { offset(0f, 8f * scale) }, scale), paint)
        val surface = RectF(card.left + 2f * scale, card.top + 2f * scale, card.right - 2f * scale, card.bottom - 6f * scale)
        paint.shader = LinearGradient(
            surface.left,
            surface.top,
            surface.right,
            surface.bottom,
            intArrayOf(palette.highlight, palette.base, palette.shadow),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(moveRowPath(surface, scale), paint)
        paint.shader = null
        if (move.disabled) {
            paint.color = Color.argb(132, 3, 12, 20)
            canvas.drawPath(moveRowPath(surface, scale), paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (focused) 3f * scale else 1.5f * scale
        paint.color = if (focused) Color.rgb(225, 255, 247) else Color.argb(174, Color.red(palette.edge), Color.green(palette.edge), Color.blue(palette.edge))
        canvas.drawPath(moveRowPath(surface, scale), paint)
        paint.style = Paint.Style.FILL
        if (focused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * scale
            paint.color = Color.argb(154, 118, 239, 223)
            canvas.drawPath(moveRowPath(RectF(surface).apply { inset(-4f * scale, -4f * scale) }, scale), paint)
            paint.style = Paint.Style.FILL
        }
        drawMovePressAnimation(canvas, surface, palette, pressProgress, scale)
        val iconChip = RectF(surface.right - 84f * scale, surface.top + 14f * scale, surface.right - 24f * scale, surface.top + 54f * scale)
        paint.color = Color.argb(108, 0, 14, 25)
        canvas.drawRoundRect(iconChip, 16f * scale, 16f * scale, paint)
        typeIcon(move.type)?.let { icon ->
            source.set(0, 0, icon.width, icon.height)
            destination.set(iconChip.centerX() - 17f * scale, iconChip.centerY() - 17f * scale, iconChip.centerX() + 17f * scale, iconChip.centerY() + 17f * scale)
            paint.alpha = 255
            canvas.drawBitmap(icon, source, destination, paint)
        }
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = moveNameSize(move.name, scale)
        val moveNameLeft = surface.left + 42f * scale
        drawSoftText(
            canvas,
            fitTextToWidth(move.name, iconChip.left - moveNameLeft - 16f * scale),
            moveNameLeft,
            surface.top + 54f * scale,
            PAPER,
            0.85f * scale
        )
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(30f, scale, 26f)
        drawSoftText(canvas, "PP ${move.pp} / ${move.maxPp}", surface.left + 42f * scale, surface.bottom - 23f * scale, PAPER, 0.65f * scale)
    }

    private fun moveRowPath(bounds: RectF, scale: Float): Path {
        return Path().apply {
            addRoundRect(bounds, 20f * scale, 20f * scale, Path.Direction.CW)
        }
    }

    private fun drawSoftText(canvas: Canvas, text: String, centerX: Float, baseline: Float, color: Int, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.setShadowLayer(radius, 0f, radius * 0.45f, Color.argb(72, 3, 11, 20))
        canvas.drawText(text, centerX, baseline, paint)
        paint.clearShadowLayer()
    }

    private fun typeIcon(type: String): Bitmap? = typeIcons.getOrPut(type) {
        val resourceId = resources.getIdentifier("type_${type.lowercase()}", "drawable", context.packageName)
        if (resourceId == 0) null else BitmapFactory.decodeResource(resources, resourceId)?.let { bitmap ->
            if (type.equals("FAIRY", true)) centeredFairyIcon(bitmap) else bitmap
        }
    }

    private fun centeredFairyIcon(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val background = IntArray(width * height)
        val glyph = IntArray(width * height)
        val bubble = source.getPixel(width / 2, 0)
        val bubbleRed = Color.red(bubble)
        val bubbleGreen = Color.green(bubble)
        val bubbleBlue = Color.blue(bubble)
        var glyphTop = height
        var glyphBottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val pixel = source.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                val whiteAmount = ((Color.green(pixel) - bubbleGreen).toFloat() / (255f - bubbleGreen)).coerceIn(0f, 1f)
                val glyphAlpha = if (whiteAmount < 0.08f) 0 else (alpha * whiteAmount).toInt()
                background[index] = Color.argb(alpha, bubbleRed, bubbleGreen, bubbleBlue)
                glyph[index] = Color.argb(glyphAlpha, 255, 255, 255)
                if (glyphAlpha > 20) {
                    glyphTop = minOf(glyphTop, y)
                    glyphBottom = maxOf(glyphBottom, y)
                }
            }
        }
        val glyphCenterY = if (glyphBottom >= glyphTop) (glyphTop + glyphBottom + 1) / 2f else height / 2f
        val glyphOffsetY = (height / 2f - glyphCenterY + FAIRY_GLYPH_OPTICAL_OFFSET_PIXELS).roundToInt()
        val centered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        centered.setPixels(background, 0, width, 0, 0, width, height)
        val centeredGlyph = IntArray(width * height)
        for (y in 0 until height) {
            val centeredY = y + glyphOffsetY
            if (centeredY !in 0 until height) continue
            for (x in 0 until width) {
                centeredGlyph[centeredY * width + x] = glyph[y * width + x]
            }
        }
        val glyphBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        glyphBitmap.setPixels(centeredGlyph, 0, width, 0, 0, width, height)
        Canvas(centered).drawBitmap(glyphBitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        glyphBitmap.recycle()
        return centered
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
        paint.color = Color.argb((64f + 126f * progress).toInt(), Color.red(FOCUS), Color.green(FOCUS), Color.blue(FOCUS))
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
        if (gimmicks.isEmpty()) {
            paint.color = Color.argb(92, 8, 25, 39)
            canvas.drawRoundRect(bounds, 18f * scale, 18f * scale, paint)
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = readableTextSize(20f, scale, 18f)
            paint.color = Color.rgb(123, 158, 178)
            canvas.drawText("No special action this turn", bounds.centerX(), bounds.centerY() + 6f * scale, paint)
            paint.textAlign = Paint.Align.LEFT
            return
        }
        val phase = SystemClock.elapsedRealtime() / 1000f
        val gap = 10f * scale
        val width = (bounds.width() - gap * (gimmicks.size - 1)) / gimmicks.size
        gimmicks.forEachIndexed { index, gimmick ->
            val left = bounds.left + index * (width + gap)
            val card = RectF(left, bounds.top, left + width, bounds.bottom)
            gimmickBounds[index] = card
            val selected = session.selectedGimmick == gimmick
            val glow = (0.56f + 0.44f * kotlin.math.sin(phase * 4f + index)).coerceIn(0f, 1f)
            val typePalette = if (gimmick == BattleSession.BattleGimmick.TERASTALLIZATION) {
                movePalette(session.terastallizeType().uppercase())
            } else {
                null
            }
            if (selected) {
                paint.shader = typePalette?.let { palette ->
                    LinearGradient(card.left, card.top, card.right, card.bottom, intArrayOf(palette.highlight, palette.base, palette.shadow), floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
                } ?: LinearGradient(
                    card.left,
                    card.top,
                    card.right,
                    card.bottom,
                    intArrayOf(0xFFFF7F7F.toInt(), 0xFFFFCC7F.toInt(), 0xFFFFFFA0.toInt(), 0xFFA8FFB2.toInt(), 0xFF99FFFF.toInt(), 0xFF8CCEFF.toInt(), 0xFF8F8FFF.toInt(), 0xFFD18CFF.toInt(), 0xFFFF85FF.toInt(), 0xFFFF7F7F.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            } else {
                paint.shader = typePalette?.let { palette ->
                    LinearGradient(card.left, card.top, card.right, card.bottom, palette.base, palette.shadow, Shader.TileMode.CLAMP)
                } ?: LinearGradient(card.left, card.top, card.right, card.bottom, Color.rgb(39, 70, 88), Color.rgb(13, 31, 47), Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(card, 26f * scale, 26f * scale, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (selected) 5f * scale else 2f * scale
            paint.color = if (selected) Color.argb((180f + 75f * glow).toInt(), 255, 255, 255) else Color.rgb(127, 184, 202)
            canvas.drawRoundRect(card, 26f * scale, 26f * scale, paint)
            paint.style = Paint.Style.FILL
            val emblemSize = minOf(
                card.height() * 0.42f,
                if (gimmicks.size > 2) minOf(62f * scale, card.width() * 0.78f) else 118f * scale
            )
            val emblemY = card.top + card.height() * 0.34f
            drawGimmickIcon(canvas, gimmick, card.centerX(), emblemY, emblemSize, selected, scale)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(if (gimmicks.size > 2) 22f else 36f, scale, 20f)
            paint.color = if (selected) Color.rgb(22, 22, 22) else PAPER
            val label = if (gimmicks.size > 2) compactGimmickLabel(gimmick) else session.gimmickLabel(gimmick)
            val labelInset = if (gimmicks.size > 2) 8f * scale else 24f * scale
            paint.textSize = fittedTextSize(label, card.width() - labelInset, paint.textSize, 10f * scale)
            canvas.drawText(label, card.centerX(), card.top + card.height() * 0.79f, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawUnavailableMove(canvas: Canvas, bounds: RectF, scale: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(160, 30, 43, 58)
        canvas.drawPath(moveRowPath(bounds, scale), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = readableTextSize(30f, scale, 26f)
        paint.color = Color.rgb(224, 191, 220)
        canvas.drawText("Unavailable", bounds.centerX(), bounds.centerY(), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun moveNameSize(name: String, scale: Float) = when {
        name.length > 15 -> readableTextSize(34f, scale, 28f)
        name.length > 11 -> readableTextSize(38f, scale, 30f)
        else -> readableTextSize(42f, scale, 32f)
    }

    private fun drawGimmickIcon(
        canvas: Canvas,
        gimmick: BattleSession.BattleGimmick,
        centerX: Float,
        centerY: Float,
        size: Float,
        selected: Boolean,
        scale: Float
    ) {
        val radius = size * 0.52f
        when (gimmick) {
            BattleSession.BattleGimmick.Z_POWER -> drawZPowerIcon(canvas, centerX, centerY, size, selected, scale)
            BattleSession.BattleGimmick.MEGA_EVOLUTION -> drawMegaIcon(canvas, centerX, centerY, radius, null, selected, scale)
            BattleSession.BattleGimmick.MEGA_EVOLUTION_X -> drawMegaIcon(canvas, centerX, centerY, radius, "X", selected, scale)
            BattleSession.BattleGimmick.MEGA_EVOLUTION_Y -> drawMegaIcon(canvas, centerX, centerY, radius, "Y", selected, scale)
            BattleSession.BattleGimmick.ULTRA_BURST -> drawUltraIcon(canvas, centerX, centerY, radius, selected, scale)
            BattleSession.BattleGimmick.DYNAMAX -> drawDynamaxIcon(canvas, centerX, centerY, radius, selected, scale)
            BattleSession.BattleGimmick.TERASTALLIZATION -> drawTeraIcon(canvas, centerX, centerY, radius, scale)
        }
    }

    private fun compactGimmickLabel(gimmick: BattleSession.BattleGimmick) = when (gimmick) {
        BattleSession.BattleGimmick.Z_POWER -> "Z"
        BattleSession.BattleGimmick.MEGA_EVOLUTION -> "MEGA"
        BattleSession.BattleGimmick.MEGA_EVOLUTION_X -> "M-X"
        BattleSession.BattleGimmick.MEGA_EVOLUTION_Y -> "M-Y"
        BattleSession.BattleGimmick.ULTRA_BURST -> "ULTRA"
        BattleSession.BattleGimmick.DYNAMAX -> "MAX"
        BattleSession.BattleGimmick.TERASTALLIZATION -> "TERA"
    }

    private fun drawZPowerIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float, selected: Boolean, scale: Float) {
        val symbol = zPowerSymbol
        if (symbol != null) {
            source.set(0, 0, symbol.width, symbol.height)
            val width = if (size < 50f * scale) size * 1.2f else size * 1.56f
            destination.set(centerX - width / 2f, centerY - size * 0.52f, centerX + width / 2f, centerY + size * 0.52f)
            paint.alpha = 255
            canvas.drawBitmap(symbol, source, destination, paint)
            return
        }
        drawIconHalo(canvas, centerX, centerY, size * 0.48f, Color.rgb(255, 188, 78), selected, scale)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 221, 116)
        canvas.drawPath(zigzagPath(centerX, centerY, size * 0.42f, size * 0.62f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.color = Color.rgb(255, 250, 211)
        canvas.drawPath(zigzagPath(centerX, centerY, size * 0.42f, size * 0.62f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawMegaIcon(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, variant: String?, selected: Boolean, scale: Float) {
        val keystoneColor = if (variant == null) Color.rgb(255, 183, 70) else Color.rgb(116, 210, 255)
        drawIconHalo(canvas, centerX, centerY, radius, keystoneColor, selected, scale)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            centerX,
            centerY - radius,
            centerX,
            centerY + radius,
            Color.rgb(104, 112, 132),
            Color.rgb(28, 33, 48),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(keystonePath(centerX, centerY, radius * 0.9f, radius * 0.9f), paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.color = Color.rgb(205, 222, 238)
        canvas.drawPath(keystonePath(centerX, centerY, radius * 0.9f, radius * 0.9f), paint)
        canvas.drawLine(centerX - radius * 0.62f, centerY - radius * 0.06f, centerX + radius * 0.62f, centerY - radius * 0.06f, paint)
        canvas.drawLine(centerX - radius * 0.36f, centerY - radius * 0.74f, centerX, centerY - radius * 0.06f, paint)
        canvas.drawLine(centerX + radius * 0.36f, centerY - radius * 0.74f, centerX, centerY - radius * 0.06f, paint)
        canvas.drawLine(centerX - radius * 0.38f, centerY + radius * 0.7f, centerX, centerY - radius * 0.06f, paint)
        canvas.drawLine(centerX + radius * 0.38f, centerY + radius * 0.7f, centerX, centerY - radius * 0.06f, paint)
        paint.style = Paint.Style.FILL
        paint.color = keystoneColor
        canvas.drawCircle(centerX, centerY - radius * 0.06f, radius * 0.16f, paint)
        if (variant != null) {
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = radius * 0.42f
            paint.color = Color.rgb(20, 35, 50)
            canvas.drawText(variant, centerX, centeredTextBaseline(centerY - radius * 0.06f), paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawUltraIcon(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, selected: Boolean, scale: Float) {
        drawIconHalo(canvas, centerX, centerY, radius, Color.rgb(186, 122, 255), selected, scale)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(146, 101, 245)
        canvas.drawPath(crystalPath(centerX, centerY, radius * 1.18f, radius * 1.08f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.color = Color.rgb(235, 214, 255)
        canvas.drawPath(crystalPath(centerX, centerY, radius * 1.18f, radius * 1.08f), paint)
        canvas.drawLine(centerX, centerY - radius * 1.08f, centerX, centerY + radius * 0.86f, paint)
        canvas.drawLine(centerX - radius * 0.86f, centerY + radius * 0.18f, centerX + radius * 0.86f, centerY + radius * 0.18f, paint)
        paint.strokeWidth = 3f * scale
        canvas.drawArc(RectF(centerX - radius * 1.3f, centerY - radius * 1.3f, centerX + radius * 1.3f, centerY + radius * 1.3f), 210f, 120f, false, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawDynamaxIcon(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, selected: Boolean, scale: Float) {
        drawIconHalo(canvas, centerX, centerY, radius, Color.rgb(255, 79, 89), selected, scale)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            centerX - radius * 0.24f,
            centerY - radius * 0.3f,
            radius * 1.15f,
            intArrayOf(Color.rgb(255, 145, 122), Color.rgb(229, 47, 66), Color.rgb(118, 23, 55)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius * 0.9f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.color = Color.rgb(255, 225, 219)
        canvas.drawCircle(centerX, centerY, radius * 0.9f, paint)
        canvas.drawLine(centerX - radius * 0.82f, centerY, centerX + radius * 0.82f, centerY, paint)
        canvas.drawCircle(centerX, centerY, radius * 0.23f, paint)
        paint.strokeWidth = 3f * scale
        paint.color = Color.argb(210, 255, 188, 180)
        canvas.drawArc(RectF(centerX - radius * 1.3f, centerY - radius * 1.3f, centerX + radius * 1.3f, centerY + radius * 1.3f), 202f, 136f, false, paint)
        canvas.drawArc(RectF(centerX - radius * 1.46f, centerY - radius * 1.46f, centerX + radius * 1.46f, centerY + radius * 1.46f), 22f, 136f, false, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawTeraIcon(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, scale: Float) {
        val type = session.terastallizeType()
        val palette = movePalette(type.uppercase())
        paint.style = Paint.Style.FILL
        paint.color = palette.base
        canvas.drawPath(crystalPath(centerX, centerY, radius * 1.2f, radius * 1.14f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.color = Color.rgb(240, 253, 255)
        canvas.drawPath(crystalPath(centerX, centerY, radius * 1.2f, radius * 1.14f), paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(222, 253, 255)
        canvas.drawLine(centerX, centerY - radius * 0.82f, centerX, centerY + radius * 0.72f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawIconHalo(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, color: Int, selected: Boolean, scale: Float) {
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        val haloMultiplier = if (radius < 26f * scale) 1.22f else 1.5f
        paint.shader = RadialGradient(
            centerX,
            centerY,
            radius * haloMultiplier,
            intArrayOf(Color.argb(if (selected) 150 else 118, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius * haloMultiplier, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) 3f * scale else 2f * scale
        paint.color = Color.argb(if (selected) 230 else 160, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(centerX, centerY, radius * 1.06f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun keystonePath(centerX: Float, centerY: Float, width: Float, height: Float): Path {
        return Path().apply {
            moveTo(centerX - width * 0.56f, centerY - height * 0.74f)
            lineTo(centerX + width * 0.56f, centerY - height * 0.74f)
            lineTo(centerX + width * 0.82f, centerY - height * 0.18f)
            lineTo(centerX + width * 0.62f, centerY + height * 0.58f)
            lineTo(centerX, centerY + height * 0.82f)
            lineTo(centerX - width * 0.62f, centerY + height * 0.58f)
            lineTo(centerX - width * 0.82f, centerY - height * 0.18f)
            close()
        }
    }

    private fun crystalPath(centerX: Float, centerY: Float, width: Float, height: Float): Path {
        return Path().apply {
            moveTo(centerX, centerY - height)
            lineTo(centerX + width * 0.72f, centerY - height * 0.22f)
            lineTo(centerX + width * 0.52f, centerY + height * 0.72f)
            lineTo(centerX, centerY + height)
            lineTo(centerX - width * 0.52f, centerY + height * 0.72f)
            lineTo(centerX - width * 0.72f, centerY - height * 0.22f)
            close()
        }
    }

    private fun zigzagPath(centerX: Float, centerY: Float, width: Float, height: Float): Path {
        return Path().apply {
            moveTo(centerX - width * 0.72f, centerY - height * 0.52f)
            lineTo(centerX + width * 0.78f, centerY - height * 0.52f)
            lineTo(centerX + width * 0.1f, centerY - height * 0.06f)
            lineTo(centerX + width * 0.64f, centerY - height * 0.06f)
            lineTo(centerX - width * 0.78f, centerY + height * 0.52f)
            lineTo(centerX - width * 0.12f, centerY + height * 0.06f)
            lineTo(centerX - width * 0.64f, centerY + height * 0.06f)
            close()
        }
    }

    private fun drawTeam(canvas: Canvas, width: Float, height: Float, scale: Float) {
        if (!session.isLiveBattleActive() && !session.isBattleFinished()) {
            teamBounds.fill(null)
            drawEmptyPanel(
                canvas,
                width,
                height,
                scale,
                "No active team",
                "Your battle team appears here during a live battle.",
                "Open Menu  ·  Team library"
            )
            return
        }
        val left = 44f * scale
        val top = 220f * scale
        val gap = 14f * scale
        val cardWidth = (width - left * 2f - gap * 2f) / 3f
        val cardHeight = 378f * scale
        teamBounds.fill(null)
        val previewOrder = session.teamPreviewOrder()
        val previewLimit = session.teamPreviewRequiredSize()
        session.team().forEachIndexed { index, pokemon ->
            val row = index / 3
            val column = index % 3
            val x = left + column * (cardWidth + gap)
            val y = top + row * (cardHeight + gap)
            val bounds = RectF(x, y, x + cardWidth, y + cardHeight)
            teamBounds[index] = bounds
            val focused = index == session.focusedTeam
            val previewPosition = previewOrder.indexOf(index)
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
            if (session.decisionKind == BattleSession.DecisionKind.TEAM_PREVIEW) {
                val marker = RectF(bounds.right - 90f * scale, bounds.top + 18f * scale, bounds.right - 18f * scale, bounds.top + 70f * scale)
                paint.color = if (previewPosition >= 0) Color.rgb(45, 162, 157) else Color.argb(112, 8, 24, 39)
                canvas.drawRoundRect(marker, 16f * scale, 16f * scale, paint)
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                paint.textSize = readableTextSize(24f, scale)
                paint.color = PAPER
                canvas.drawText(if (previewPosition >= 0) "${previewPosition + 1}" else "—", marker.centerX(), marker.centerY() + 8f * scale, paint)
                paint.textAlign = Paint.Align.LEFT
            }
            val spriteBounds = RectF(bounds.left + 16f * scale, bounds.top + 22f * scale, bounds.left + 150f * scale, bounds.top + 156f * scale)
            teamSprites[pokemon]?.draw(canvas, spriteBounds, SystemClock.elapsedRealtime())
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(if (pokemon.length > 11) 32f else 38f, scale, 28f)
            paint.color = PAPER
            val teamName = fitTextToWidth(pokemon, bounds.right - 20f * scale - (bounds.left + 164f * scale))
            canvas.drawText(teamName, bounds.left + 164f * scale, bounds.top + 55f * scale, paint)
            paint.textSize = readableTextSize(30f, scale, 26f)
            paint.color = MUTED
            canvas.drawText("Lv. ${details.level}${details.gender.ifBlank { "" }}", bounds.left + 164f * scale, bounds.top + 92f * scale, paint)
            drawTeamHp(canvas, RectF(bounds.left + 164f * scale, bounds.top + 112f * scale, bounds.right - 20f * scale, bounds.top + 140f * scale), details.hp, scale)
            var typeX = bounds.left + 18f * scale
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textSize = readableTextSize(24f, scale, 21f)
            details.types.forEach { type ->
                val typeWidth = maxOf(92f * scale, paint.measureText(type) + 28f * scale)
                val typeBounds = RectF(typeX, bounds.top + 177f * scale, typeX + typeWidth, bounds.top + 217f * scale)
                paint.color = movePalette(type).base
                canvas.drawRoundRect(typeBounds, 13f * scale, 13f * scale, paint)
                paint.textAlign = Paint.Align.CENTER
                drawOutlinedText(canvas, type, typeBounds.centerX(), centeredTextBaseline(typeBounds.centerY()), Color.rgb(7, 18, 26), PAPER, 1.5f * scale)
                paint.textAlign = Paint.Align.LEFT
                typeX = typeBounds.right + 8f * scale
            }
            paint.textSize = readableTextSize(30f, scale, 26f)
            val state = when {
                details.condition.contains("FNT", true) -> "Fainted"
                session.decisionKind == BattleSession.DecisionKind.SWITCH -> "Choose to switch in"
                session.decisionKind == BattleSession.DecisionKind.TEAM_PREVIEW -> if (previewPosition >= 0) "Order ${previewPosition + 1}/$previewLimit" else "Tap to order"
                pokemon.equals(session.playerPokemon, true) -> "In battle"
                else -> "Available"
            }
            paint.color = if (details.condition.contains("FNT", true)) MAGENTA else Color.rgb(150, 231, 205)
            canvas.drawText(state, bounds.left + 18f * scale, bounds.bottom - 32f * scale, paint)
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
        paint.textSize = readableTextSize(30f, scale, 26f)
        drawOutlinedText(canvas, "HP ${hp.substringBefore(' ')}", bounds.centerX(), bounds.centerY() + 8f * scale, Color.rgb(5, 14, 22), PAPER, 1.8f * scale)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawEmptyPanel(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        title: String,
        message: String,
        action: String
    ) {
        val bounds = RectF(44f * scale, 220f * scale, width - 44f * scale, height - 56f * scale)
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            Color.rgb(25, 61, 79),
            Color.rgb(8, 26, 43),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bounds, 28f * scale, 28f * scale, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = Color.argb(180, 102, 211, 231)
        canvas.drawRoundRect(bounds, 28f * scale, 28f * scale, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = readableTextSize(42f, scale, 32f)
        paint.color = PAPER
        canvas.drawText(title, bounds.centerX(), bounds.top + 150f * scale, paint)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = readableTextSize(30f, scale, 26f)
        paint.color = Color.rgb(190, 218, 235)
        canvas.drawText(fitTextToWidth(message, bounds.width() - 100f * scale), bounds.centerX(), bounds.top + 220f * scale, paint)
        paint.textSize = readableTextSize(28f, scale, 24f)
        paint.color = CYAN
        canvas.drawText(action, bounds.centerX(), bounds.top + 292f * scale, paint)
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
        paint.textSize = readableTextSize(36f, scale, 31f)
        paint.color = CYAN
        canvas.drawText("Activity", left + 28f * scale, top + 45f * scale, paint)
        var rowY = top + 94f * scale
        val start = maxOf(0, minOf(messages.size - 4, session.focusedMessage - 3))
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.textSize = readableTextSize(34f, scale, 29f)
        for (index in start until messages.size) {
            if (rowY >= bottom - 24f * scale) break
            val focused = index == session.focusedMessage
            if (focused) {
                paint.color = Color.rgb(20, 119, 126)
                canvas.drawRoundRect(RectF(left + 14f * scale, rowY - 29f * scale, width - left - 14f * scale, rowY + 14f * scale), 10f * scale, 10f * scale, paint)
            }
            paint.color = if (focused || index % 2 == 0) PAPER else MUTED
            canvas.drawText(fitTextToWidth(messages[index], width - left * 2f - 56f * scale), left + 28f * scale, rowY, paint)
            rowY += 72f * scale
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
        paint.textSize = readableTextSize(38f, scale, 33f)
        paint.color = PAPER
        canvas.drawText("Send a message", activityChatBounds!!.centerX(), activityChatBounds!!.centerY() + 10f * scale, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawMenu(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val entries = session.menuItems().mapIndexed(::menuLabel)
        val columns = BattleSession.MENU_COLUMNS
        val rows = (entries.size + columns - 1) / columns
        val left = 36f * scale
        val top = 202f * scale
        val bottomMargin = 28f * scale
        val gap = 14f * scale
        val cardWidth = (width - left * 2f - gap * (columns - 1)) / columns
        val cardHeight = (height - top - bottomMargin - gap * (rows - 1)) / rows
        menuBounds.fill(null)
        entries.forEachIndexed { index, entry ->
            val row = index / columns
            val column = index % columns
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
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            paint.color = if (index == 3 && (entry == "Forfeit" || entry == "Leave battle")) MAGENTA else PAPER
            paint.textSize = fittedTextSize(
                entry,
                bounds.width() - 64f * scale,
                readableTextSize(38f, scale, 33f),
                readableTextSize(28f, scale, 24f)
            )
            canvas.drawText(entry, bounds.left + 32f * scale, centeredTextBaseline(bounds.centerY()), paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun tabName(panel: BattleSession.Panel) = when (panel) {
        BattleSession.Panel.MOVES -> "Fight"
        BattleSession.Panel.TEAM -> "Pokémon"
        BattleSession.Panel.ACTIVITY -> "Activity"
        BattleSession.Panel.MENU -> "Menu"
    }

    private fun menuLabel(index: Int, entry: String) = when (index) {
        0 -> "Find battle"
        1 -> entry.removePrefix("Battle format ").replace("Gen 7", "G7").replace("Gen 9", "G9")
        2 -> "Battle chat"
        3 -> entry
        4 -> "SFX: ${entry.substringAfterLast(' ')}"
        5 -> "Music: ${entry.substringAfterLast(' ')}"
        6 -> "Haptics: ${entry.substringAfterLast(' ')}"
        7 -> "Sprites: ${entry.substringAfterLast(' ')}"
        8 -> "Team library"
        9 -> "Rooms"
        10 -> "Account"
        11 -> "Server"
        12 -> if (entry == "Replay controls") "Replay controls" else "Battle controls"
        else -> if (entry == "Save replay") "Save replay" else "Timer: ${entry.substringAfterLast(' ')}"
    }

    private fun movePalette(type: String): MovePalette {
        val base = ShowdownTypePalette.canonical(type)
        return MovePalette(
            blend(base, PAPER, 0.10f),
            blend(base, Color.BLACK, 0.08f),
            blend(base, Color.BLACK, 0.48f),
            blend(base, PAPER, 0.22f)
        )
    }

    private fun blend(first: Int, second: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return Color.rgb(
            (Color.red(first) * inverse + Color.red(second) * ratio).toInt(),
            (Color.green(first) * inverse + Color.green(second) * ratio).toInt(),
            (Color.blue(first) * inverse + Color.blue(second) * ratio).toInt()
        )
    }

    private fun ellipsize(text: String, maximum: Int) = if (text.length <= maximum) text else "${text.take(maximum - 1)}…"

    private fun fitTextToWidth(text: String, maximumWidth: Float): String {
        var value = text
        while (value.length > 1 && paint.measureText(value) > maximumWidth) {
            val withoutEllipsis = value.removeSuffix("…")
            value = "${withoutEllipsis.dropLast(1).trimEnd()}…"
        }
        return value
    }

    private fun fittedTextSize(text: String, maximumWidth: Float, preferredSize: Float, minimumSize: Float): Float {
        if (maximumWidth <= 0f) return minimumSize
        var size = preferredSize
        paint.textSize = size
        while (size > minimumSize && paint.measureText(text) > maximumWidth) {
            size = (size - 1f).coerceAtLeast(minimumSize)
            paint.textSize = size
        }
        if (paint.measureText(text) > maximumWidth) {
            size = (size * maximumWidth / paint.measureText(text)).coerceAtLeast(1f)
            paint.textSize = size
        }
        return size
    }

    private fun wrapText(text: String, maximumWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            if (paint.measureText(word) <= maximumWidth) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isEmpty() || paint.measureText(candidate) <= maximumWidth) {
                    current = candidate
                } else {
                    lines += current
                    current = word
                }
            } else {
                if (current.isNotEmpty()) {
                    lines += current
                    current = ""
                }
                var chunk = ""
                word.forEach { character ->
                    val candidate = chunk + character
                    if (chunk.isNotEmpty() && paint.measureText(candidate) > maximumWidth) {
                        lines += chunk
                        chunk = character.toString()
                    } else {
                        chunk = candidate
                    }
                }
                current = chunk
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun centeredTextBaseline(centerY: Float): Float = centerY - (paint.ascent() + paint.descent()) / 2f

    private fun readableTextSize(
        designPixels: Float,
        scale: Float,
        minimumPixels: Float = 12f,
        minimumDisplaySp: Float = ThorDisplayProfile.LOWER_MINIMUM_TEXT_SP
    ): Float = maxOf(
        designPixels * scale,
        minimumPixels * scale,
        ThorDisplayProfile.minimumReadablePixels(
            width.toInt(),
            height.toInt(),
            resources.displayMetrics.density * resources.configuration.fontScale,
            minimumDisplaySp
        )
    )

    private companion object {
        val TABS = arrayOf(BattleSession.Panel.MOVES, BattleSession.Panel.TEAM, BattleSession.Panel.ACTIVITY, BattleSession.Panel.MENU)
        const val PAPER = 0xFFEFF8FF.toInt()
        const val CYAN = 0xFF3EE5FF.toInt()
        const val FOCUS = 0xFF70D8FF.toInt()
        const val MAGENTA = 0xFFFF4AB0.toInt()
        const val MUTED = 0xFF97B1D1.toInt()
        const val FAIRY_GLYPH_OPTICAL_OFFSET_PIXELS = 2f
        val BOOST_NAMES = mapOf("atk" to "Atk", "def" to "Def", "spa" to "Sp. Atk", "spd" to "Sp. Def", "spe" to "Speed", "accuracy" to "Accuracy", "evasion" to "Evasion")
    }
}
