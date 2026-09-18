package com.taskdroid.manager.fragments

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.taskdroid.manager.R
import com.taskdroid.manager.ui.LiveChartView
import com.taskdroid.manager.ui.dp
import com.taskdroid.manager.ui.fmt
import com.taskdroid.manager.util.TelephonyUtil
import com.taskdroid.manager.util.Traffic
import com.taskdroid.manager.util.formatBytes
import com.taskdroid.manager.util.infoCard
import com.taskdroid.manager.util.kv
import com.taskdroid.manager.util.sectionTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkFragment : BaseInfoFragment() {
    private var downChart: LiveChartView? = null
    private var upChart: LiveChartView? = null
    private var summaryRow: LinearLayout? = null
    private var graphsBuilt = false

    override fun populate() {
        if (!graphsBuilt) {
            content.removeAllViews()
            graphsBuilt = true
            content.sectionTitle("Internet & Live Graph")
            buildGraphs()
            loadRadioDetails()
            return
        }
        val rate = Traffic.sample()
        downChart?.addPoint(rate.downTotal / 1024f)
        upChart?.addPoint(rate.upTotal / 1024f)
        summaryRow?.removeAllViews()
        summaryRow?.kv("Download speed", "${formatBytes(rate.downTotal)}/s  (${(rate.downTotal / 1024f).fmt(1)} KB/s)")
        summaryRow?.kv("Upload speed", "${formatBytes(rate.upTotal)}/s  (${(rate.upTotal / 1024f).fmt(1)} KB/s)")
        summaryRow?.kv("Data used since boot", formatBytes(Traffic.totalBoot))
    }

    private fun buildGraphs() {
        val card = content.infoCard("LIVE DATA RATE  (last 60 s)")
        val inner = card.getChildAt(0) as LinearLayout

        downChart = LiveChartView(requireContext()).apply {
            title = "Download"
            unit = " KB/s"
            autoRange = true
            capacity = 60
            displayMaxPoints = 60
            lineColor = ContextCompat.getColor(requireContext(), R.color.success)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120))
        }
        upChart = LiveChartView(requireContext()).apply {
            title = "Upload"
            unit = " KB/s"
            autoRange = true
            capacity = 60
            displayMaxPoints = 60
            lineColor = ContextCompat.getColor(requireContext(), R.color.accent)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120))
        }
        inner.addView(downChart)
        inner.addView(upChart)

        summaryRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(summaryRow)
    }

    private fun loadRadioDetails() {
        CoroutineScope(Dispatchers.IO).launch {
            val ctx = requireContext()
            val cfg = TelephonyUtil.wifiInfo(ctx)
            val lteInfo = TelephonyUtil.lteVolteInfo(ctx)
            val bands = TelephonyUtil.lteBands(ctx)
            val networks = TelephonyUtil.supportedNetworks(ctx)
            val simSlots = TelephonyUtil.simSlots(ctx)
            val activeType = TelephonyUtil.activeNetworkType(ctx)

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    content.sectionTitle("Connectivity")
                    content.infoCard("ACTIVE CONNECTION") {
                        kv("Network", TelephonyUtil.mobileNetworkState(ctx))
                        kv("Transport", activeType)
                        kv("SIM Setup", TelephonyUtil.simSummary(ctx))
                    }
                    content.infoCard("SIM SLOTS") {
                        if (simSlots.isEmpty()) {
                            kv("No slots", "No active subscription found")
                        } else {
                            simSlots.forEachIndexed { i, s ->
                                if (i > 0) rowDivider()
                                s.forEach { (k, v) -> kv(k, v) }
                            }
                        }
                    }
                    content.infoCard("LTE / 5G / VOLTE") {
                        lteInfo.forEach { (k, v) -> kv(k, v) }
                        kv("Supported radio networks", networks.joinToString(", ").ifBlank { "unknown" })
                    }
                    content.infoCard("LTE BANDS") {
                        addSelectableText(bands)
                    }
                    content.infoCard("WiFi DETAILS") {
                        if (cfg.isEmpty()) kv("WiFi", "OFF or unavailable") else cfg.forEach { (k, v) -> kv(k, v) }
                    }
                }
            }
        }
    }

    private fun rowDivider() {
        val v = android.view.View(context)
        v.setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
        addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(8); bottomMargin = dp(8)
        })
    }

    private fun addSelectableText(text: String) {
        addView(TextView(context).apply {
            this.text = text
            textSize = 12f
            textIsSelectable = true
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, dp(4), 0, 0)
        })
    }
}