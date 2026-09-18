package com.taskdroid.manager.util

import android.os.Build

object DeviceInfo {
    fun getProp(vararg names: String): String? {
        for (n in names) {
            val v = RootUtil.getProp(n)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    data class SocInfo(
        val vendor: String,
        val model: String,
        val hardware: String,
        val platform: String,
        val board: String,
        val architecture: String,
        val abis: String,
        val cores: String,
        val cpuInfo: String,
    )

    fun detectSoc(): SocInfo {
        val cpuInfo = RootUtil.readFile("/proc/cpuinfo").joinToString("\n")
        val hwLine = RootUtil.readFile("/proc/cpuinfo")
            .firstOrNull { it.startsWith("Hardware") || it.startsWith("Processor") || it.startsWith("model name") }
            ?.substringAfter(":")
            ?.trim()
            ?: ""

        val vendorProp = getProp("ro.soc.manufacturer", "ro.board.platform", "ro.hardware", "ro.product.board", "ro.chipname")
        val modelProp = getProp("ro.soc.model", "ro.soc.model_name", "ro.chipname")
        val platform = getProp("ro.board.platform", "ro.hardware.platform")
        val board = getProp("ro.product.board", "ro.hardware")

        val vendor = detectVendor((vendorProp ?: "") + " " + (modelProp ?: "") + " " + (platform ?: "") + " " + hwLine)
        val model = modelProp
            ?: (if (hwLine.isNotBlank()) hwLine else vendorProp ?: "Unknown SoC")

        val cores = RootUtil.readFileBest(
            "/sys/devices/system/cpu/present",
            "/sys/devices/system/cpu/possible"
        ) ?: Runtime.getRuntime().availableProcessors().toString()
        val arch = Build.SUPPORTED_ABIS.joinToString(", ")

        return SocInfo(
            vendor = vendor,
            model = model,
            hardware = hwLine,
            platform = platform ?: "Unknown",
            board = board ?: "Unknown",
            architecture = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64-bit (ARM64/x86_64)" else "32-bit",
            abis = arch,
            cores = cores,
            cpuInfo = cpuInfo.ifBlank { "unavailable" }
        )
    }

    fun detectVendor(raw: String): String {
        val s = raw.lowercase()
        return when {
            (s.contains("qualcomm") || s.contains("qcom") || s.contains("qsc") ||
                s.contains("sdm") || s.contains("msm") ||
                Regex("sm[0-9]{4}").containsMatchIn(s)) -> "Qualcomm (Snapdragon)"

            (s.contains("mediatek") || s.contains("helio") || s.contains("dimensity") ||
                Regex("mt[0-9]{4}").containsMatchIn(s)) -> "MediaTek"

            (s.contains("unisoc") || s.contains("spreadtrum") || s.contains("sprd") ||
                s.contains("sc9830") || s.contains("sc9850") || s.contains("sc9860") ||
                s.contains("ucl") || s.contains("tangula")) -> "Unisoc (Spreadtrum)"

            (s.contains("hisilicon") || s.contains("kirin") || s.contains("hisi")) -> "HiSilicon (Kirin)"

            (s.contains("exynos") || s.contains("s5e")) -> "Samsung Exynos"

            (s.contains("tensor") || s.contains("gs101") || s.contains("gs201") || s.contains("gs301")) -> "Google Tensor"

            (s.contains("rockchip") || Regex("rk[0-9]{3}").containsMatchIn(s)) -> "Rockchip"

            (s.contains("intel") || s.contains("x86")) -> "Intel"

            else -> "General-purpose ARM"
        }
    }

    fun bootloaderState(): Triple<String, String, String> {
        var lock = getProp("ro.boot.verifiedbootstate") ?: "unknown"
        val vbmeta = getProp("ro.boot.vbmeta.device_state") ?: "unknown"
        val flashLocked = getProp("ro.boot.flash.locked")
        val unlockProgress = getProp("ro.boot.unlock_progress")
        val fingerprint = Build.FINGERPRINT

        val testKeys = fingerprint.contains("test-keys", ignoreCase = true)

        val normalized = when {
            lock.equals("green", true) -> "LOCKED"
            lock.equals("orange", true) -> "UNLOCKED"
            vbmeta.equals("locked", true) -> "LOCKED"
            vbmeta.equals("unlocked", true) -> "UNLOCKED"
            flashLocked == "1" -> "LOCKED"
            flashLocked == "0" -> "UNLOCKED"
            unlockProgress?.let { it != "0" && it.isNotBlank() } == true -> "UNLOCKED"
            else -> "Unknown"
        }

        val details = if (testKeys) "unofficial/test-keys build (system image not stock)" else "stock factory image"

        val raw = listOfNotNull(
            "verifiedbootstate=${lock}",
            "vbmeta.device_state=${vbmeta}",
            "flash.locked=${flashLocked}",
            "unlock_progress=${unlockProgress}"
        ).joinToString(" | ")

        return Triple(normalized, details, raw)
    }

    fun securityPatch(): String = Build.VERSION.SECURITY_PATCH

    fun buildFingerprint(): String = Build.FINGERPRINT

    fun kernelVersion(): String {
        val v = RootUtil.shell("uname -r")
        return v ?: "unavailable"
    }

    fun seLinux(): String {
        val e = RootUtil.readFileBest("/sys/fs/selinux/enforce", "/sys/fs/selinux/enforce")
        return when {
            e == "1" -> "Enforcing"
            e == "0" -> "Permissive"
            e != null -> "Enforcing ($e)"
            else -> {
                val s = RootUtil.shell("getenforce")
                s ?: "Unknown"
            }
        }
    }
}