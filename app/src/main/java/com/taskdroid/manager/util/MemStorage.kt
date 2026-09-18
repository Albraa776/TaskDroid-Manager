package com.taskdroid.manager.util

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

object MemStorage {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return if (i == 0) "${v.toInt()} ${units[i]}" else String.format("%.2f %s", v, units[i])
    }

    fun memInfo(context: Context): Triple<Long, Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Triple(mi.totalMem, mi.availMem, mi.totalMem - mi.availMem)
    }

    fun memDetails(context: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        out["Total RAM"] = formatBytes(mi.totalMem)
        out["Available RAM"] = formatBytes(mi.availMem)
        out["Used RAM"] = formatBytes(mi.totalMem - mi.availMem)
        out["RAM Load"] = "${(100 * (mi.totalMem - mi.availMem) / mi.totalMem)}%"
        if (mi.lowMemory) out["Memory Status"] = "LOW MEMORY"
        out["Low Memory Threshold"] = formatBytes(mi.threshold)
        return out
    }

    fun storageFor(path: String): Triple<Long, Long, Long> {
        return try {
            val st = StatFs(path)
            val total = st.totalBytes
            val free = st.availableBytes
            Triple(total, free, total - free)
        } catch (_: Throwable) {
            Triple(0, 0, 0)
        }
    }

    fun storageDetails(context: Context): Map<String, Map<String, String>> {
        val paths = linkedMapOf(
            "Internal Storage" to Environment.getDataDirectory().absolutePath,
            "External / Shared" to Environment.getExternalStorageDirectory().absolutePath,
            "System Partition" to "/system",
            "Cache Partition" to "/cache",
        )

        val out = linkedMapOf<String, Map<String, String>>()
        for ((label, path) in paths) {
            val (total, free, used) = storageFor(path)
            if (total <= 0) continue
            out[label] = linkedMapOf(
                "Total" to formatBytes(total),
                "Used" to formatBytes(used),
                "Free" to formatBytes(free),
                "Used %" to if (total > 0) "${100 * used / total}%" else "0%"
            )
        }

        if (Environment.isExternalStorageEmulated()) {
            val ex = Environment.getExternalStorageDirectory()
            val s = storageFor(ex.absolutePath)
            if (s.first > 0) {
                out["Emulated Storage"] = linkedMapOf(
                    "Total" to formatBytes(s.first),
                    "Used" to formatBytes(s.third),
                    "Free" to formatBytes(s.second),
                    "Used %" to "${100 * s.third / s.first}%"
                )
            }
        }
        return out
    }

    fun swapInfo(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        try {
            val lines = java.io.File("/proc/meminfo").readLines()
            for (l in lines) {
                if ("SwapTotal" in l || "SwapFree" in l || "MemAvailable" in l || "MemFree" in l || "Buffers" in l ||
                    "Cached" in l || "Shmem" in l || "SReclaimable" in l || "Zswap" in l || "Zram" in l
                ) {
                    val key = l.substringBefore(":").trim()
                    val value = l.substringAfter(":").trim().replace("kB", " KB")
                    out[key] = value
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }
}