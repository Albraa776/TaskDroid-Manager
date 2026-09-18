package com.taskdroid.manager.util

import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Thermal {
    data class Zone(val name: String, val type: String, val tempC: Float? )

    suspend fun thermalZones(): List<Zone> = withContext(Dispatchers.IO) {
        try {
            val dir = java.io.File("/sys/class/thermal")
            val zones = dir.listFiles()?.filter { it.name.startsWith("thermal_zone") } ?: return@withContext emptyList()
            zones.mapNotNull { z ->
                try {
                    val type = java.io.File(z, "type").readText().trim()
                    val raw = java.io.File(z, "temp").readText().trim().toFloat()
                    val tempC = if (raw > 1000) raw / 1000f else raw
                    Zone(z.name, type, tempC)
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun batteryTempC(context: Context): Float? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val e = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE)
            if (e == Int.MIN_VALUE) null else e / 10f
        } catch (_: Throwable) {
            null
        }
    }

    fun cpufreqSnapshot(): Map<Int, Pair<String, String>> {
        val out = linkedMapOf<Int, Pair<String, String>>()
        val cores = RootUtil.readFile("/sys/devices/system/cpu/present").firstOrNull()
        val count = if (cores != null) {
            Regex("\\d+").findAll(cores).let { m ->
                val nums = m.map { it.value.toInt() }.toList()
                if (nums.size > 1) nums.max() - nums.min() + 1 else nums.firstOrNull() ?: 1
            }
        } else Runtime.getRuntime().availableProcessors()

        for (i in 0 until count) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
            val online = RootUtil.readFileBest("/sys/devices/system/cpu/cpu$i/online")
            val cur = RootUtil.readFileBest("$base/scaling_cur_freq", "$base/cpuinfo_cur_freq")
            val max = RootUtil.readFileBest("$base/scaling_max_freq", "$base/cpuinfo_max_freq")
            val gov = RootUtil.readFileBest("$base/scaling_governor")
            val onlineText = online ?: "on"
            val freq = "@" + (cur?.let { (it.toLongOrNull() ?: 0) / 1000 }?.toString() ?: "?") + " GHz"
            out[i] = Pair(onlineText + freq, gov ?: "")
        }
        return out
    }
}

object CpuMonitor {
    private var last = LongArray(0)
    private var lastTotal = 0L
    private var lastIdle = 0L

    fun usage(): Float {
        return try {
            val l = RootUtil.readFile("/proc/stat").firstOrNull { it.startsWith("cpu ") } ?: return 0f
            val parts = l.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return 0f
            val idle = parts[3] + (parts.getOrNull(5) ?: 0)
            val total = parts.sum()
            if (lastTotal == 0L) {
                lastTotal = total
                lastIdle = idle
                return 0f
            }
            val dTotal = total - lastTotal
            val dIdle = idle - lastIdle
            lastTotal = total
            lastIdle = idle
            if (dTotal <= 0) return 0f
            ((dTotal - dIdle) * 100f / dTotal).coerceIn(0f, 100f)
        } catch (_: Throwable) {
            0f
        }
    }

    fun perCoreUsage(): List<Float> {
        return try {
            val lines = RootUtil.readFile("/proc/stat").filter { it.startsWith("cpu") && !it.startsWith("cpu ") }
            lines.map { l ->
                val parts = l.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                if (parts.size < 4) 0f
                else {
                    val idle = parts[3] + (parts.getOrNull(5) ?: 0)
                    val total = parts.sum()
                    if (total <= 0) 0f
                    else (((total - idle) * 100f) / total).coerceIn(0f, 100f)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun numCoresConfig(): Int {
        return Runtime.getRuntime().availableProcessors()
    }
}

object Traffic {
    @Volatile var lastRxWifi = 0L
    @Volatile var lastTxWifi = 0L
    @Volatile var lastRxMobile = 0L
    @Volatile var lastTxMobile = 0L
    @Volatile var lastRxTotal = 0L
    @Volatile var lastTxTotal = 0L
    @Volatile var lastTick = 0L
    @Volatile var totalBoot = 0L

    data class Rate(val downWifi: Long, val upWifi: Long, val downMobile: Long, val upMobile: Long,
                    val downTotal: Long, val upTotal: Long, val timestampMs: Long)

    fun sample(): Rate {
        val now = System.currentTimeMillis()
        val rxM = android.net.TrafficStats.getMobileRxBytes()
        val txM = android.net.TrafficStats.getMobileTxBytes()
        val rxT = android.net.TrafficStats.getTotalRxBytes()
        val txT = android.net.TrafficStats.getTotalTxBytes()
        val rxWifi = (rxT - rxM).coerceAtLeast(0)
        val txWifi = (txT - txM).coerceAtLeast(0)

        if (lastTick == 0L) {
            lastRxWifi = rxWifi; lastTxWifi = txWifi
            lastRxMobile = rxM; lastTxMobile = txM
            lastRxTotal = rxT; lastTxTotal = txT
            lastTick = now
            totalBoot = rxT + txT
            return Rate(0, 0, 0, 0, 0, 0, now)
        }

        val dt = (now - lastTick).coerceAtLeast(1) / 1000f
        val rate = Rate(
            downWifi = ((rxWifi - lastRxWifi).coerceAtLeast(0) / dt).toLong(),
            upWifi = ((txWifi - lastTxWifi).coerceAtLeast(0) / dt).toLong(),
            downMobile = ((rxM - lastRxMobile).coerceAtLeast(0) / dt).toLong(),
            upMobile = ((txM - lastTxMobile).coerceAtLeast(0) / dt).toLong(),
            downTotal = ((rxT - lastRxTotal).coerceAtLeast(0) / dt).toLong(),
            upTotal = ((txT - lastTxTotal).coerceAtLeast(0) / dt).toLong(),
            timestampMs = now
        )

        lastRxWifi = rxWifi; lastTxWifi = txWifi
        lastRxMobile = rxM; lastTxMobile = txM
        lastRxTotal = rxT; lastTxTotal = txT
        lastTick = now
        totalBoot = rxT + txT

        return rate
    }
}