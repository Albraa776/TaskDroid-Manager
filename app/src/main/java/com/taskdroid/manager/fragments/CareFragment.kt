package com.taskdroid.manager.fragments

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.taskdroid.manager.R
import com.taskdroid.manager.ui.fmt
import com.taskdroid.manager.ui.infoCard
import com.taskdroid.manager.ui.kv
import com.taskdroid.manager.ui.sectionTitle
import com.taskdroid.manager.util.MemStorage
import com.taskdroid.manager.util.Thermal
import com.taskdroid.manager.util.WeatherHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CareFragment : BaseInfoFragment() {
    private var weather: WeatherHelper.Weather? = null
    private var weatherLoading = false
    private var lastWeatherFetch = 0L
    private var weatherScope: Job? = null

    override val intervalMs = 2000L

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        weatherScope = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now - lastWeatherFetch > 10 * 60 * 1000L && !weatherLoading) {
                    weatherLoading = true
                    val ctx = requireContext()
                    val w = withContext(Dispatchers.IO) { WeatherHelper.fetch(ctx) }
                    weather = w
                    lastWeatherFetch = now
                    weatherLoading = false
                }
                delay(60_000)
            }
        }
    }

    override fun onStop() {
        weatherScope?.cancel()
        super.onStop()
    }

    override fun populate() {
        content.removeAllViews()
        content.sectionTitle("Device Care · Storage · RAM · Temps · Location")
        buildWeather()
        buildStorage()
        buildMemory()
        buildThermal()
        buildAppsInfo()
    }

    private fun buildWeather() {
        content.infoCard("CURRENT PLACE / LOCATION TEMPERATURE") {
            val w = weather
            when {
                weatherLoading && w == null -> {
                    kv("Fetching", "Requesting Open-Meteo weather for your location…")
                }
                w != null && w.tempC.isNaN() -> {
                    kv("Location", w.place)
                    kv("Weather", w.summary)
                }
                w != null -> {
                    kv("Location", w.place)
                    kv("Current temperature", "${w.tempC.fmt(1)} °C")
                    kv("Feels like", "${w.feelsLikeC.fmt(1)} °C")
                    if (w.humidity >= 0) kv("Humidity", "${w.humidity}%")
                    kv("Wind", "${w.windKmh.fmt(0)} km/h")
                    kv("Conditions", w.summary)
                    kv("Source", w.source)
                }
                else -> {
                    kv("Info", "Weather loads automatically every 10 min (needs Location permission).")
                }
            }
        }
    }

    private fun buildStorage() {
        val data = MemStorage.storageDetails(requireContext())
        content.infoCard("STORAGE") {
            data.forEach { (label, map) ->
                val pctFull = (map["Used %"]?.trimEnd('%')?.toIntOrNull() ?: 0) >= 90
                kv(
                    label,
                    "${map["Used"]} / ${map["Total"]}  (${map["Used %"]} used · ${map["Free"]} free)",
                    if (pctFull) R.color.danger else R.color.text_primary
                )
            }
        }
    }

    private fun buildMemory() {
        val mem = MemStorage.memDetails(requireContext())
        content.infoCard("RAM (MEMORY)") {
            mem.forEach { (k, v) -> kv(k, v) }
            val swap = MemStorage.swapInfo()
            if (swap.isNotEmpty()) {
                swap.forEach { (k, v) -> kv(k, v) }
            }
        }
    }

    private fun buildThermal() {
        content.infoCard("DEVICE TEMPERATURE (thermal zones)") {
            val zones = Thermal.thermalZones()
            if (zones.isEmpty()) {
                kv("Thermal zones", "not readable (OEM restriction). Battery temp is in the Battery tab.")
            } else {
                zones.forEach { z ->
                    val c = z.tempC
                    val color = when {
                        c == null -> R.color.text_primary
                        c < 45f -> R.color.success
                        c < 60f -> R.color.warn
                        else -> R.color.danger
                    }
                    kv(z.type, c?.let { "${it.fmt(1)} °C" } ?: "n/a", color)
                }
            }
        }
    }

    private fun buildAppsInfo() {
        try {
            val pm = requireContext().packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val user = apps.count { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            val system = apps.size - user
            content.infoCard("APPS & PROCESS") {
                kv("Installed apps", "${apps.size}  ($user user · $system system)")
                kv("CPU cores", "${Runtime.getRuntime().availableProcessors()}")
            }
        } catch (_: Throwable) {
        }
    }
}