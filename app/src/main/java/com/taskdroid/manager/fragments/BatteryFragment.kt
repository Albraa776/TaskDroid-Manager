package com.taskdroid.manager.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.taskdroid.manager.R
import com.taskdroid.manager.ui.LiveChartView
import com.taskdroid.manager.ui.dp
import com.taskdroid.manager.ui.fmt
import com.taskdroid.manager.ui.infoCard
import com.taskdroid.manager.ui.kv
import com.taskdroid.manager.ui.sectionTitle

class BatteryFragment : BaseInfoFragment() {
    private var levelChart: LiveChartView? = null
    private var tempChart: LiveChartView? = null
    private var graphsBuilt = false
    private var lastIntent: Intent? = null
    private var statusInner: LinearLayout? = null
    private var levelInner: LinearLayout? = null

    override fun populate() {
        if (!graphsBuilt) {
            content.removeAllViews()
            graphsBuilt = true
            content.sectionTitle("Battery Live")
            buildGraphs()
        }
        updateData()
    }

    private fun batteryIntent(): Intent? {
        if (lastIntent != null) return lastIntent
        lastIntent = requireContext().registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        return lastIntent
    }

    private fun buildGraphs() {
        val card = content.infoCard("LIVE GRAPHS  (last 60 s)")
        val inner = card.getChildAt(0) as LinearLayout
        val chartHeight = (120 * requireContext().resources.displayMetrics.density).toInt()

        levelChart = LiveChartView(requireContext()).apply {
            title = "Battery level"
            unit = "%"
            capacity = 60
            displayMaxPoints = 60
            lineColor = ContextCompat.getColor(requireContext(), R.color.success)
        }
        tempChart = LiveChartView(requireContext()).apply {
            title = "Battery temperature"
            unit = " °C"
            autoRange = true
            capacity = 60
            displayMaxPoints = 60
            lineColor = ContextCompat.getColor(requireContext(), R.color.danger)
        }
        levelChart?.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, chartHeight)
        tempChart?.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, chartHeight)
        inner.addView(levelChart)
        inner.addView(tempChart)

        val levelCard = content.infoCard("BATTERY LEVEL")
        levelInner = levelCard.getChildAt(0) as LinearLayout

        val statusCard = content.infoCard("STATUS")
        statusInner = statusCard.getChildAt(0) as LinearLayout
    }

    private fun updateData() {
        val ctx = requireContext()
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = try { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Throwable) { -1 }
        val tempC = try { bm.getIntProperty(7) } catch (_: Throwable) { Int.MIN_VALUE }.let { if (it == Int.MIN_VALUE) null else it / 10f }
        val volt = try { bm.getLongProperty(8) } catch (_: Throwable) { -1L }
        val current = try { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Throwable) { 0L }
        val currentAvg = try { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) } catch (_: Throwable) { 0L }
        val chargeCounter = try { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) } catch (_: Throwable) { -1L }
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            try { bm.getIntProperty(11) } catch (_: Throwable) { -1 }
        } else -1

        levelChart?.addPoint(level.toFloat())
        tempChart?.addPoint(tempC ?: 0f)

        val intent = batteryIntent()
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown"

        val li = levelInner ?: return
        li.removeAllViews()
        li.addView(android.widget.TextView(li.context).apply {
            text = if (level >= 0) "$level%" else "unknown"
            textSize = 42f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(ContextCompat.getColor(li.context,
                if (level >= 0 && level <= 20) R.color.danger
                else if (level in 21..40) R.color.warn
                else R.color.success))
        })

        val si = statusInner ?: return
        si.removeAllViews()
        si.kv("Charging state", statusName(status))
        si.kv("Power source", pluggedName(plugged))
        si.kv("Health", healthName(health))
        si.kv("Battery temperature", tempC?.let { "${it} °C" } ?: "unknown")
        si.kv("Voltage", if (volt > 0) "${(volt / 1000f).fmt(2)} V" else "unknown")
        si.kv("Current (instant)", "${current} mA ${if (current > 0) "(charging)" else if (current < 0) "(discharging)" else ""}")
        si.kv("Current (avg)", "${currentAvg} mA")
        si.kv("Charge counter", if (chargeCounter > 0) "${chargeCounter / 1000f} mAh" else "unknown")
        if (cycles >= 0) si.kv("Charge cycles", "$cycles")
        si.kv("Technology", tech)
    }

    private fun statusName(s: Int): String = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
        BatteryManager.BATTERY_STATUS_FULL -> "FULL"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT CHARGING"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unknown"
        else -> "Unknown ($s)"
    }

    private fun pluggedName(p: Int): String = when (p) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC power"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
        -1 -> "unknown"
        else -> "Not plugged"
    }

    private fun healthName(h: Int): String = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
        -1 -> "unknown"
        else -> "Unknown ($h)"
    }
}