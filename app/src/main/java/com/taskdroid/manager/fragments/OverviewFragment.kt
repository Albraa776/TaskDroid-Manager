package com.taskdroid.manager.fragments

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.taskdroid.manager.R
import com.taskdroid.manager.ui.bar
import com.taskdroid.manager.ui.infoCard
import com.taskdroid.manager.ui.kv
import com.taskdroid.manager.ui.sectionTitle
import com.taskdroid.manager.util.CpuMonitor
import com.taskdroid.manager.util.DeviceInfo
import com.taskdroid.manager.util.MemStorage
import com.taskdroid.manager.util.TelephonyUtil
import com.taskdroid.manager.util.Thermal

class OverviewFragment : BaseInfoFragment() {
    private var lastThermalRead = 0L
    private var cachedThermalMax = 0f

    private fun battery(): BatteryManager =
        requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    override fun populate() {
        content.removeAllViews()
        val ctx = requireContext()

        content.sectionTitle("Device Care Dashboard")

        val (totalMem, availMem, _) = MemStorage.memInfo(ctx)
        val memUsedPct = if (totalMem > 0) 100 * (totalMem - availMem) / totalMem else 0

        val st = MemStorage.storageFor(android.os.Environment.getDataDirectory().absolutePath)
        val storageUsedPct = if (st.first > 0) 100 * st.third / st.first else 0

        val bm = battery()
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val thermalMax = thermalMax()
        val cpu = CpuMonitor.usage()

        val storageScore = (100 - storageUsedPct).coerceIn(0, 100)
        val memScore = (100 - memUsedPct).coerceIn(0, 100)
        val battScore = level.coerceIn(0, 100)
        val cpuScore = (100 - cpu.toInt()).coerceIn(0, 100)
        val thermalScore = when {
            thermalMax <= 40f -> 100
            thermalMax <= 50f -> 85
            thermalMax <= 60f -> 60
            else -> 30
        }
        val careScore = (storageScore * 0.25 + memScore * 0.25 + battScore * 0.2 + cpuScore * 0.2 + thermalScore * 0.1).toInt()

        val scoreColor = when {
            careScore >= 80 -> R.color.success
            careScore >= 55 -> R.color.warn
            else -> R.color.danger
        }

        content.infoCard("DEVICE CARE SCORE") {
            val s = TextView(requireContext()).apply {
                text = "$careScore/100"
                textSize = 40f
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), scoreColor))
            }
            addView(s)
            kv("Status", when {
                careScore >= 80 -> "Excellent"
                careScore >= 55 -> "Good - minor attention"
                else -> "Needs attention"
            })
        }

        content.bar("Battery", "$level%", level.toFloat(), R.color.success)
        content.bar("RAM used", "${MemStorage.formatBytes(totalMem - availMem)} of ${MemStorage.formatBytes(totalMem)}  ($memUsedPct%)", memUsedPct.toFloat(), R.color.accent)
        content.bar("Storage used", "${MemStorage.formatBytes(st.third)} of ${MemStorage.formatBytes(st.first)}  ($storageUsedPct%)", storageUsedPct.toFloat(), R.color.warn)
        content.bar("CPU load", "$cpu%", cpu, R.color.primary)

        content.infoCard("TEMPERATURES") {
            val battTemp = Thermal.batteryTempC(ctx)
            kv("Battery", battTemp?.let { "${it} °C" } ?: "unknown")
            kv("Hottest thermal zone", if (thermalMax > 0) "${thermalMax} °C" else "unknown")
            kv("CPU temp (zone)", thermalMax.takeIf { it > 0 }?.let { "${it} °C" } ?: "unknown")
        }

        val soc = DeviceInfo.detectSoc()
        content.infoCard("DEVICE") {
            kv("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
            kv("SoC", "${soc.vendor} · ${soc.model}")
            kv("RAM", MemStorage.formatBytes(MemStorage.memInfo(ctx).first))
            kv("Storage", MemStorage.formatBytes(st.first))
            kv("Android", Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT)
            kv("Network", TelephonyUtil.activeNetworkType(ctx) + " · " + TelephonyUtil.simSummary(ctx))
        }
    }

    private fun thermalMax(): Float {
        val now = System.currentTimeMillis()
        if (now - lastThermalRead > 5000) {
            val zones = Thermal.thermalZones()
            cachedThermalMax = zones.mapNotNull { it.tempC }.maxOrNull() ?: 0f
            lastThermalRead = now
        }
        return cachedThermalMax
    }
}