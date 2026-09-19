package com.taskdroid.manager.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.lang.reflect.InvocationTargetException

object TelephonyUtil {
    fun wifiInfo(ctx: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wm.isWifiEnabled) {
                out["WiFi"] = "OFF"
                return out
            }
            val info = wm.connectionInfo
            if (info.networkId == -1 || wm.connectionInfo.ssid?.contains("<unknown") == true) {
                out["WiFi"] = "Connected (network name requires location permission)"
                return out
            }
            out["SSID"] = info.ssid?.removeSurrounding("\"") ?: "unknown"
            out["BSSID"] = info.bssid ?: "unknown"
            out["Signal"] = "${info.rssi} dBm  (${wifiLevel(info.rssi)})"
            out["Frequency"] = "${info.frequency} MHz (${bandName(info.frequency)})"
            out["Link Speed"] = "${info.linkSpeed} Mbps"
            out["IP Address"] = ipToString(info.ipAddress)
            out["Network ID"] = info.networkId.toString()
        } catch (_: Throwable) {
            out["WiFi"] = "unavailable"
        }
        return out
    }

    private fun wifiLevel(rssi: Int): String {
        if (rssi <= -80) return "Poor"
        if (rssi <= -67) return "Fair"
        if (rssi <= -55) return "Good"
        return if (rssi <= 0) "Excellent" else "Unknown"
    }

    private fun bandName(freq: Int): String {
        return if (freq < 3000) "2.4 GHz" else "5 GHz"
    }

    private fun ipToString(ip: Int): String {
        return try {
            "${(ip and 0xFF)}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    fun mobileNetworkState(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            val type = info?.type
            val subtype = info?.subtypeName
            when {
                type == null -> "No connectivity"
                info.isRoaming -> "Roaming ($type/${subtype})"
                else -> "$type/${subtype}"
            }
        } catch (_: Throwable) {
            "unknown"
        }
    }

    fun activeNetworkType(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps == null) "None"
            else when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val up = caps.linkUpstreamBandwidthKbps
                    if (up >= 100_000) "Cellular (very high speed)"
                    else "Cellular"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth PAN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN tunnel"
                else -> "Other"
            }
        } catch (_: Throwable) {
            "unknown"
        }
    }

    fun networkNameForType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM (2G)"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2.5G)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2.75G)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3.5G)"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3.5G)"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3.5G)"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+ (3.75G)"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA (2G/3G)"
            TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EV-DO (3G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA (3G)"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            else -> "Unknown ($type)"
        }
    }

    fun radioInfo(ctx: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val phoneType = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM / UMTS / LTE (with CDMA support on some networks)"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                TelephonyManager.PHONE_TYPE_NONE -> "No active phone radio"
                4 -> "IMS"
                else -> "Unknown"
            }
            out["Radio Type"] = phoneType
            out["Operator"] = tm.networkOperatorName ?: "unknown"
            out["Operator Code (MCC-MNC)"] = tm.networkOperator ?: "N/A"

            @Suppress("DEPRECATION")
            out["Data Network"] = networkNameForType(tm.dataNetworkType)
            @Suppress("DEPRECATION")
            out["Voice Network"] = networkNameForType(tm.voiceNetworkType)
            @Suppress("DEPRECATION")
            out["Phone Type"] = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                else -> "Other"
            }
            out["IMEI (slot 0)"] = try { tm.imei } catch (_: Throwable) { "blocked" }
            out["Meid"] = try { tm.meid } catch (_: Throwable) { "blocked" }
            @Suppress("DEPRECATION")
            out["Data Roaming"] = if (griv(tm, "getNetworkRoaming") == "true") "YES" else "No"
            out["Network Country Iso"] = tm.networkCountryIso ?: "N/A"
            out["Sim Country Iso"] = tm.simCountryIso ?: "N/A"
            out["Sim Operator"] = tm.simOperatorName ?: "N/A"
            out["Sim Operator Code"] = tm.simOperator ?: "N/A"
            out["Sim State"] = simStateName(tm.simState)
            out["Is Multi-SIM Capable"] = griv(tm, "isMultiSimEnabled") ?: "unknown"
            out["Is Voice Capable"] = griv(tm, "isVoiceCapable") ?: "unknown"
        } catch (_: Throwable) {
            out["Radio"] = "unavailable"
        }
        return out
    }

    private fun simStateName(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "Absent (no SIM)"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "Locked (PIN required)"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "Locked (PUK required)"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Locked (network)"
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently disabled"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Card IO error"
        else -> "Unknown ($state)"
    }

    private fun griv(obj: Any, name: String): String? {
        return try {
            val m = obj.javaClass.getMethod(name)
            m.invoke(obj).toString()
        } catch (e: Throwable) {
            null
        }
    }

    fun simSlots(ctx: Context): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subs = SubscriptionManager.from(ctx).activeSubscriptionInfoList ?: emptyList()
            val count = subs.size
            val physicalCount = griv(tm, "getSimCount")?.toIntOrNull() ?: 0
            for (s in subs) {
                val m = linkedMapOf<String, String>()
                m["Slot"] = "SIM ${s.simSlotIndex + 1}"
                m["Carrier"] = s.carrierName?.toString() ?: "Unknown carrier"
                m["Display Name"] = s.displayName.toString()
                m["MCC"] = s.mcc.toString()
                m["MNC"] = s.mnc.toString()
                m["Data Roaming"] = if (s.dataRoaming == 1) "Enabled" else "Disabled"
                m["Sub Type"] = if (Build.VERSION.SDK_INT >= 29 && s.isEmbedded) "eSIM (embedded)" else "Physical SIM"
                m["Subscription Id"] = s.subscriptionId.toString()
                out.add(m)
            }
            if (out.isEmpty()) {
                val m = linkedMapOf<String, String>()
                m["Slot"] = "SIM ${if (physicalCount > 0) "0" else "?"}"
                m["Carrier"] = tm.simOperatorName ?: "Not detected"
                m["SIM Count"] = physicalCount.toString()
                out.add(m)
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun simSummary(ctx: Context): String {
        return try {
            val subs = SubscriptionManager.from(ctx).activeSubscriptionInfoList
            val n = subs?.size ?: 0
            when {
                n == 0 -> "No active SIM slot detected"
                n == 1 -> "Single SIM"
                n == 2 -> "Dual SIM"
                else -> "$n SIM slots"
            }
        } catch (_: Throwable) {
            "unknown"
        }
    }

fun supportedNetworks(ctx: Context): List<String> {
    val out = mutableListOf<String>()
    try {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var types: List<Int> = emptyList()
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                types = (tm.javaClass.getMethod("getAllNetworkTypes").invoke(tm) as IntArray).toList()
            } catch (_: Throwable) {
            }
        }
        if (types.isEmpty()) {
            @Suppress("DEPRECATION")
            types = listOf(tm.networkType)
        }
        out.addAll(types.map { networkNameForType(it) })
    } catch (_: Throwable) {
    }
    return out.distinct()
}

    fun lteVolteInfo(ctx: Context): Map<String, String> {
        val out = linkedMapOf<String, String>()
        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networks = supportedNetworks(ctx)
            val lteSupported = networks.any { it.contains("LTE") }
            val nrSupported = networks.any { it.contains("5G NR") }
            out["LTE (4G) Support"] = if (lteSupported) "YES" else "check manual / " + networks.joinToString(", ").ifBlank { "unknown" }
            out["5G NR Support"] = if (nrSupported) "YES (supported by modem)" else "Not advertised"

            var volte = "unknown"
            try {
                val cfg = ctx.getSystemService(Context.CARRIER_CONFIG_SERVICE) as CarrierConfigManager
                val subId = SubscriptionManager.getDefaultSubscriptionId()
                val b = cfg.getConfigForSubId(subId)
                if (b != null) {
                    val v = b.getBoolean("carrier_volte_available_bool")
                    volte = if (v) "Available (carrier config)" else "Not advertised"
                }
            } catch (_: Throwable) {
            }
            if (volte == "unknown") {
                val p1 = RootUtil.getProp("persist.radio.calls.on.ims")
                val p2 = RootUtil.getProp("ro.config.hw_volte_on")
                val p3 = RootUtil.getProp("persist.rcs.feature")
                val ims = RootUtil.getProp("gsm.sim.operator.alpha")
                volte = when {
                    p1 == "1" || p1?.lowercase() == "true" -> "Enabled (IMS calling property)"
                    p2 == "1" -> "Enabled (Huawei property)"
                    p3?.contains("1") == true -> "Possibly enabled (RCS)"
                    else -> "Not advertised (carrier/modem dependent)"
                }
            }
            out["VoLTE"] = volte

            val imsReg = RootUtil.getProp("persist.ims.regserver.regstate")
            out["IMS Registration"] = when {
                imsReg.equals("0", true) || imsReg.equals("-1", false) -> "Not registered"
                imsReg.equals("2", true) || imsReg == "1" -> "REGISTERED"
                !imsReg.isNullOrBlank() -> imsReg
                else -> "check via system logs"
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun lteBands(ctx: Context): String {
        val attempts = linkedMapOf<String, String>()
        try {
            for (p in listOf("gsm.lte.bands", "gsm.radio.bands", "ro.sim1_lte_band", "ro.lte.bands",
                "persist.vendor.radio.lte.bands", "ro.board.lte_cap")) {
                val v = RootUtil.getProp(p)
                if (!v.isNullOrBlank()) attempts[p] = v
            }
        } catch (_: Throwable) {
        }
        try {
            val b = dumpsysLteBands()
            if (!b.isNullOrBlank()) attempts["runtime (RIL)"] = b
        } catch (_: Throwable) {
        }
        try {
            val r = refleactLteBands(ctx)
            if (!r.isNullOrBlank()) attempts["hidden ITelephony API"] = r
        } catch (_: Throwable) {
        }
        if (attempts.isEmpty()) return "Not exposed (requires root/RIL access or OEM support)"
        return attempts.entries.joinToString("\n") { "${it.key} | ${it.value}" }
    }

    private fun dumpsysLteBands(): String? {
        val out = RootUtil.shellNoSplit("dumpsys", "radio") ?: return null
        val lines = out.lines().filter {
            it.contains("lte") && (it.contains("band", true)) ||
                (it.contains("SupportedBands") || it.contains("Bands") && it.contains("["))
        }
        val useful = lines.filter { it.contains("eUtra") || it.contains("Band") || it.contains("lte") }
        val res = useful.take(20).joinToString("\n").trim()
        return if (res.isBlank()) {
            RootUtil.shell("dumpsys telephony.registry")?.lines()
                ?.filter { it.contains("lte", true) || it.contains("band", true) }
                ?.take(15)?.joinToString("\n")?.trim()
        } else res
    }

    private fun refleactLteBands(ctx: Context): String? {
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subId = SubscriptionManager.getDefaultSubscriptionId()
            val itmCls = Class.forName("com.android.internal.telephony.ITelephony")
            val mgr = Class.forName("android.os.ServiceManager")
            val getService = mgr.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "phone") as android.os.IBinder
            val stub = itmCls.getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
            val bandMethod = stub.javaClass.getMethod("getLteBandInfo", Int::class.java)
            @Suppress("UNCHECKED_CAST")
            val bands = bandMethod.invoke(stub, subId) as? List<*>
            if (bands.isNullOrEmpty()) {
                // try another hidden: getLteOnCapability or network int list
                val n = itmCls.getMethod("getLteOnCapability", Int::class.java)
                "capability=${n.invoke(stub, subId)}"
            } else bands.joinToString(", ") { "$it (B${it})" }
        } catch (e: InvocationTargetException) {
            "hidden API blocked: ${e.targetException?.message ?: e.message}"
        } catch (e: Throwable) {
            "hidden API blocked: ${e.message}"
        }
    }
}