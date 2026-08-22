package dev.adrian.showdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class ShowdownPokedexSpriteView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sprite: ShowdownSpriteCache.SpriteAsset? = null
    private var animationsPaused = false

    fun setSprite(value: ShowdownSpriteCache.SpriteAsset?) {
        if (sprite !== value) sprite?.stopAnimation()
        sprite = value
        if (animationsPaused) sprite?.stopAnimation()
        invalidate()
    }

    fun setAnimationsPaused(paused: Boolean) {
        animationsPaused = paused
        if (paused) sprite?.stopAnimation()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Color.rgb(8, 25, 39)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 18f, 18f, paint)
        sprite?.draw(
            canvas,
            RectF(12f, 8f, width.toFloat() - 12f, height.toFloat() - 8f),
            System.currentTimeMillis(),
            animate = !animationsPaused
        )
        if (sprite?.isAnimated == true && !animationsPaused) postInvalidateDelayed(RenderCadence.animatedFrameDelayMillis)
    }
}
