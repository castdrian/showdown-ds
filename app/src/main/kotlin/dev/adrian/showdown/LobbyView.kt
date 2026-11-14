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
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.min

/** Accessibility text for [LobbyView]. */
object LobbyAccessibility {
    private const val REPLAY_LOADING = "Showdown replay. Loading battle."
    private const val LOBBY_PREFIX = "Showdown lobby. "

    fun describe(replayLoading: Boolean, status: String): String =
        if (replayLoading) REPLAY_LOADING else "$LOBBY_PREFIX$status"
}

/**
 * The upper screen outside a battle: the lobby card, or the loading screen while a replay prepares.
 * During a battle the Showdown client WebView covers it.
 */
class LobbyView(
    context: Context,
    private val session: BattleSession
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private val logo: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.showdown_logo)

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.contentDescription = LobbyAccessibility.describe(isReplayLoading(), session.status)
        info.isFocusable = true
        info.isClickable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        drawLobby(canvas, width, height, min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT))
    }

    private fun isReplayLoading() = session.isReplayMode() && !session.hasBattleProtocolTranscript()

    private fun drawLobby(canvas: Canvas, width: Float, height: Float, scale: Float) {
        val replayLoading = isReplayLoading()
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
        const val DESIGN_WIDTH = 1920f
        const val DESIGN_HEIGHT = 1080f
        const val INK = 0xFFF0F7FF.toInt()
        const val CYAN = 0xFF4AE7FF.toInt()
        const val MUTED = 0xFFBBD1EA.toInt()
    }
}
