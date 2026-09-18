package com.taskdroid.manager.fragments

import android.os.Build
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.taskdroid.manager.R
import com.taskdroid.manager.ui.LiveChartView
import com.taskdroid.manager.ui.dp
import com.taskdroid.manager.ui.fmt
import com.taskdroid.manager.ui.infoCard
import com.taskdroid.manager.ui.kv
import com.taskdroid.manager.ui.sectionTitle
import com.taskdroid.manager.util.CpuMonitor
import com.taskdroid.manager.util.DeviceInfo
import com.taskdroid.manager.util.Thermal

class CpuFragment : BaseInfoFragment() {
    private var chart: LiveChartView? = null
    private var graphsBuilt = false
    private var detailsLoaded = false
    private var lastFreqRead = 0L
    private var cachedFreq = ""
    private var statsInner: LinearLayout? = null

    override fun populate() {
        if (!graphsBuilt) {
            content.removeAllViews()
            graphsBuilt = true
            content.sectionTitle("CPU Live")
            buildGraph()
            loadSocDetails()

            val statsCard = content.infoCard("CPU USAGE")
            statsInner = statsCard.getChildAt(0) as LinearLayout
        }
        val cpu = CpuMonitor.usage()
        chart?.addPoint(cpu)
        updateData()
    }

    private fun buildGraph() {
        val card = content.infoCard("CPU USAGE  (last 120 s)")
        val inner = card.getChildAt(0) as LinearLayout
        chart = LiveChartView(requireContext()).apply {
            title = "CPU load"
            unit = "%"
            capacity = 120
            displayMaxPoints = 120
            lineColor = ContextCompat.getColor(requireContext(), R.color.primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140))
        }
        inner.addView(chart)
    }

    private fun loadSocDetails() {
        if (detailsLoaded) return
        detailsLoaded = true
        val soc = DeviceInfo.detectSoc()
        content.sectionTitle("SoC")
        content.infoCard("SYSTEM-ON-CHIP") {
            kv("Vendor", soc.vendor)
            kv("Model", soc.model)
            kv("Platform", soc.platform)
            kv("Board", soc.board)
            kv("Hardware line", soc.hardware.ifBlank { "n/a" })
            kv("Architecture", soc.architecture)
            kv("Supported ABIs", soc.abis)
            kv("Cores (config)", soc.cores)
            kv("CPU family", Build.HARDWARE)
        }
    }

    private fun updateData() {
        val freq = freqSnapshot()
        val perCore = CpuMonitor.perCoreUsage()
        val totalCore = perCore.size
        val inner = statsInner ?: return
        inner.removeAllViews()

        inner.addView(TextView(inner.context).apply {
            text = "${CpuMonitor.usage().fmt(1)}%"
            textSize = 36f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(ContextCompat.getColor(inner.context, R.color.primary))
        })
        inner.kv("Active cores (reading)", "$totalCore")
        inner.kv("Frequencies", freq.ifBlank { "not readable without permissions" })

        val coreCard = content.infoCardOrGet("PER-CORE LOAD")
        coreCard.removeAllViews()
        perCore.take(16).forEachIndexed { i, v ->
            val barView = LinearLayout(inner.context).apply { orientation = LinearLayout.VERTICAL }
            val lvl = TextView(inner.context).apply {
                text = "Core $i  ${v.fmt(0)}%"
                textSize = 12f
                setTextColor(ContextCompat.getColor(inner.context, R.color.text_secondary))
            }
            val bar = android.widget.ProgressBar(inner.context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = (v.coerceIn(0f, 100f) * 10).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(inner.context, R.color.accent))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(inner.context, R.color.surface_variant))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6))
            }
            barView.addView(lvl)
            barView.addView(bar)
            coreCard.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }

    private fun LinearLayout.infoCardOrGet(header: String): LinearLayout {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                val inner = child.getChildAt(0) as? LinearLayout ?: continue
                val first = inner.getChildAt(0) as? TextView
                if (first?.text?.toString() == header) return inner
            }
        }
        return infoCard(header).getChildAt(0) as LinearLayout
    }

    private fun freqSnapshot(): String {
        val now = System.currentTimeMillis()
        if (now - lastFreqRead > 5000 || cachedFreq.isBlank()) {
            val snap = Thermal.cpufreqSnapshot()
            cachedFreq = if (snap.isEmpty()) ""
            else snap.entries.joinToString("  ·  ") { "cpu${it.key} ${it.value.first}${if (it.value.second.isNotBlank()) " [${it.value.second}]" else ""}" }
            lastFreqRead = now
        }
        return cachedFreq
    }
}