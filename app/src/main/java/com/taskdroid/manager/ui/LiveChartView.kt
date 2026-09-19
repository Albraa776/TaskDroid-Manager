package com.taskdroid.manager.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.taskdroid.manager.R
import kotlin.collections.ArrayDeque
import kotlin.math.max
import kotlin.math.min

class LiveChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    val data = ArrayDeque<Float>()
    var capacity: Int = 120
        set(value) {
            field = value
            while (data.size > field) data.removeFirst()
        }
    var minValue = 0f
    var maxValue = 100f
    var autoRange = false
    var unit = ""
    var title = ""
    var lineColor = ContextCompat.getColor(context, R.color.primary)
        set(value) {
            field = value
            linePaint.color = value
            mFillShader = null
        }
    var displayMaxPoints = 100

    private var linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor; strokeWidth = 2.5f; style = Paint.Style.STROKE }
    private var gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.divider); strokeWidth = 1f }
    private var textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 11 * resources.displayMetrics.scaledDensity
    }
    private var valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        textSize = 13 * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }
    private var fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mFillShader: Shader? = null

    private fun ensureFill(w: Int, h: Int) {
        if (mFillShader == null || mFillShader !is LinearGradient) {
            mFillShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.argb(130, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)),
                Color.argb(0, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = mFillShader
        }
    }

    @Synchronized
    fun addPoint(v: Float) {
        if (data.size >= capacity) data.removeFirst()
        if (v.isNaN()) return
        data.addLast(v)
        postInvalidateOnAnimation()
    }

    fun clear() = synchronized(this) { data.clear(); postInvalidateOnAnimation() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        ensureFill(w.toInt(), h.toInt())

        // grid
        val steps = 4
        for (i in 0..steps) {
            val y = h * i / steps
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        val pts = synchronized(this) { data.toList() }
        if (pts.isEmpty()) {
            canvas.drawText(title, dp(10), dp(16), textPaint)
            canvas.drawText("collecting data…", dp(10), h - dp(10), textPaint)
            return
        }

        val (mLo, mHi) = computeRange(pts)
        val range = (mHi - mLo).takeIf { it > 0 } ?: 1f
        val shown = pts.takeLast(displayMaxPoints)
        val n = shown.size
        val plotW = w - dp(8)
        val plotH = h - dp(22)

        val path = Path()
        val fill = Path()
        for (i in shown.indices) {
            val x = dp(4) + plotW * i / max(1, (displayMaxPoints - 1))
            val v = shown[i].coerceIn(mLo, mHi)
            val y = dp(20) + plotH - plotH * ((v - mLo) / range)
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, dp(20) + plotH)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(dp(4) + plotW * max(1, (n - 1)) / max(1, (displayMaxPoints - 1)), dp(20) + plotH)
        fill.close()

        canvas.save()
        canvas.clipRect(dp(4), dp(20).toFloat(), w - dp(4), h - dp(2))
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(path, linePaint)
        canvas.restore()

        // y labels
        for (i in 0..steps) {
            val y = dp(20) + plotH - plotH * i / steps
            val valAt = mLo + range * i / steps
            val lbl = if (autoRange) valAt.fmt(1) else (mLo + range * i / steps).fmt(0)
            canvas.drawText(lbl + if (i == steps) unit else "", dp(4), y - dp(2), textPaint)
        }

        canvas.drawText(title, dp(4), dp(12), textPaint)
        val latest = pts.last()
        canvas.drawText(
            latest.fmt(1) + unit,
            w - dp(4) - valuePaint.measureText(latest.fmt(1) + unit),
            dp(12),
            valuePaint
        )
    }

    private fun computeRange(pts: List<Float>): Pair<Float, Float> {
        if (!autoRange) return minValue to maxValue
        var lo = pts.minOrNull() ?: 0f
        var hi = pts.maxOrNull() ?: 100f
        val pad = (hi - lo) * 0.15f
        lo -= pad
        hi += pad
        if (hi - lo < 1f) {
            hi = lo + 1f
        }
        return lo to hi
    }

    private fun dp(v: Int): Float = (v * resources.displayMetrics.density)
}