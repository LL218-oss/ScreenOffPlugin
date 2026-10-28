package com.trae.screenoff

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * 全屏熄屏动画层。
 * mode 0: 经典电视关机 —— 上下黑幕合拢，中间留一条发光白线，随后白线向中心收缩熄灭
 * mode 1: 圆形扩散 —— 黑色圆形从屏幕中心扩散铺满
 * mode 2: 淡出变黑 —— 整屏逐渐变暗直到全黑
 */
class ScreenOffOverlay(
    context: Context,
    private val mode: Int,
    private val onFinished: () -> Unit
) : View(context) {

    private val blackPaint = Paint().apply { color = Color.BLACK }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(20f, 0f, 0f, Color.WHITE)
    }
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        // 光晕效果需要软件层
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (mode == 0) 800L else 600L
            interpolator = if (mode == 0) LinearInterpolator() else DecelerateInterpolator(1.2f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onFinished()
            })
        }
        post { animator?.start() }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        when (mode) {
            0 -> drawCrt(canvas, w, h)
            1 -> drawCircle(canvas, w, h)
            else -> canvas.drawColor(Color.argb((progress * 255).toInt(), 0, 0, 0))
        }
    }

    private fun drawCrt(canvas: Canvas, w: Float, h: Float) {
        val closePhase = 0.55f
        val cx = w / 2f
        val lineY = h / 2f
        if (progress < closePhase) {
            // 上下黑幕合拢，中间留一条发光白线
            val t = progress / closePhase
            val e = t * t * (3f - 2f * t)
            val half = h / 2f * e
            canvas.drawRect(0f, 0f, w, half, blackPaint)
            canvas.drawRect(0f, h - half, w, h, blackPaint)
            val lineH = dp(3f)
            canvas.drawRect(0f, lineY - lineH / 2f, w, lineY + lineH / 2f, linePaint)
        } else {
            // 全黑后，白线向中心收缩并熄灭
            canvas.drawRect(0f, 0f, w, h, blackPaint)
            val t = (progress - closePhase) / (1f - closePhase)
            val keep = 1f - t
            val lineW = w * keep
            val lineH = dp(3f) * keep + 1.5f
            linePaint.alpha = (255 * keep).toInt().coerceIn(0, 255)
            if (lineW > 0.5f && keep > 0f) {
                canvas.drawRect(
                    cx - lineW / 2f, lineY - lineH / 2f,
                    cx + lineW / 2f, lineY + lineH / 2f, linePaint
                )
            }
            linePaint.alpha = 255
        }
    }

    private fun drawCircle(canvas: Canvas, w: Float, h: Float) {
        if (progress <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val maxR = (Math.hypot(w.toDouble(), h.toDouble()) / 2.0).toFloat() * 1.05f
        val r = progress * maxR
        val soft = dp(48f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r + soft,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.88f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, r + soft, paint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
