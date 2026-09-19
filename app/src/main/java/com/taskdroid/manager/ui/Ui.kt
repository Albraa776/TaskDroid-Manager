package com.taskdroid.manager.ui

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.taskdroid.manager.R

fun LinearLayout.sectionTitle(heading: String, subtitle: String? = null) {
    val tv = TextView(context).apply {
        setTextColor(ContextCompat.getColor(context, R.color.primary))
        textSize = 16f
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        text = heading
        setPadding(dp(4), dp(18), dp(4), dp(6))
    }
    if (subtitle != null) {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv)
        col.addView(TextView(context).apply {
            text = subtitle
            textSize = 12f
            setPadding(dp(4), 0, dp(4), dp(8))
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })
        addView(col)
    } else {
        addView(tv)
    }
}

fun LinearLayout.infoCard(header: String, block: LinearLayout.() -> Unit = {}): MaterialCardView {
    val card = MaterialCardView(context).apply {
        radius = dp(14).toFloat()
        cardElevation = 0f
        strokeWidth = 1
        strokeColor = ContextCompat.getColor(context, R.color.divider)
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
    }
    val inner = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    inner.addView(TextView(context).apply {
        text = header
        textSize = 14f
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        setTextColor(ContextCompat.getColor(context, R.color.accent))
        setPadding(0, 0, 0, dp(8))
    })

    inner.block()
    card.addView(inner)
    addView(card)
    return card
}

fun LinearLayout.kv(label: String, value: String, valueColor: Int = R.color.text_primary) {
    if (value.isBlank()) return
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
    }
    val l = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        setPadding(0, dp(3), dp(8), dp(3))
    }
    val v = TextView(context).apply {
        text = value
        textSize = 13f
        setTextColor(ContextCompat.getColor(context, valueColor))
        setPadding(0, dp(3), 0, dp(3))
        setTextIsSelectable(true)
    }
    row.addView(l, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.45f))
    row.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f))
    addView(row)
}

fun LinearLayout.statusRow(label: String, statusOk: Boolean, badText: Array<String> = arrayOf()) {
    val color = if (statusOk) R.color.success else R.color.danger
    kv(label, if (statusOk) "OK" else "Attention", color)
}

fun LinearLayout.bigValue(title: String, value: String, unit: String = "", accent: Int = R.color.success) {
    val card = infoCard(title) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.BOTTOM
        }
        val v = TextView(context).apply {
            text = value
            textSize = 34f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(ContextCompat.getColor(context, accent))
        }
        val u = TextView(context).apply {
            text = unit
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(dp(6), 0, 0, dp(6))
        }
        row.addView(v)
        if (unit.isNotBlank()) row.addView(u)
        addView(row)
    }
}

fun dp(value: Int): Int = (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

fun LinearLayout.divider() {
    val v = View(context)
    v.setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
    addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
        topMargin = dp(10)
        bottomMargin = dp(10)
    })
}

fun LinearLayout.bar(label: String, valueText: String, pct: Float, color: Int = R.color.accent) {
    val card = infoCard(label) {
        val labelRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        labelRow.addView(TextView(context).apply {
            text = valueText
            textSize = 14f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })
        addView(labelRow)
        val bar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = (pct.coerceIn(0f, 100f) * 10).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, color))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_variant))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply {
                topMargin = dp(6)
            }
        }
        addView(bar)
    }
}

fun Float.fmt(decimals: Int = 1): String = String.format(java.util.Locale.US, "%.${decimals}f", this)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (i == 0) "${v.toInt()} ${units[i]}" else String.format(java.util.Locale.US, "%.2f %s", v, units[i])
}