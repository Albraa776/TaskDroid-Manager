package com.taskdroid.manager.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

object WeatherHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class Weather(
        val tempC: Double,
        val feelsLikeC: Double,
        val humidity: Int,
        val windKmh: Double,
        val code: Int,
        val summary: String,
        val place: String,
        val source: String,
    )

    fun hasLocation(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun lastLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            providers.addAll(lm.getProviders(true))
        } else {
            @Suppress("DEPRECATION")
            providers.add(lm.getProvider("network")?.let { LocationManager.NETWORK_PROVIDER } ?: "")
            @Suppress("DEPRECATION")
            providers.add(lm.getProvider("gps")?.let { LocationManager.GPS_PROVIDER } ?: "")
        }
        var best: Location? = null
        for (p in providers) {
            if (p.isBlank()) continue
            try {
                @Suppress("DEPRECATION")
                val l = lm.getLastKnownLocation(p)
                if (l != null && (best == null || l.time > best.time)) best = l
            } catch (_: Throwable) {
            }
        }
        return best
    }

    suspend fun fetch(context: Context): Weather? = withContext(Dispatchers.IO) {
        if (!hasLocation(context)) {
            return@withContext Weather(
                tempC = Double.NaN, feelsLikeC = Double.NaN, humidity = -1,
                windKmh = Double.NaN, code = -1, summary = "Location permission not granted",
                place = "Unknown", source = "no-location"
            )
        }
        val loc = lastLocation(context)
        val lat = loc?.latitude
        val lon = loc?.longitude
        if (lat == null || lon == null) {
            return@withContext Weather(
                tempC = Double.NaN, feelsLikeC = Double.NaN, humidity = -1,
                windKmh = Double.NaN, code = -1,
                summary = "No location fix yet. Move outdoors / enable GPS and pull to refresh.",
                place = "Unknown", source = "no-fix"
            )
        }
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true&hourly=temperature_2m,apparent_temperature,relativehumidity_2m,weathercode,windspeed_10m".format(lat, lon)
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val cw = json.optJSONObject("current_weather")
                val hourly = json.optJSONObject("hourly")
                val temp = cw?.optDouble("temperature", Double.NaN) ?: Double.NaN
                val wind = cw?.optDouble("windspeed", Double.NaN) ?: Double.NaN
                val code = cw?.optInt("weathercode", -1) ?: -1
                val temps = hourly?.optJSONArray("temperature_2m")
                val feels = hourly?.optJSONArray("apparent_temperature")
                val humA = hourly?.optJSONArray("relativehumidity_2m")
                val idx = (System.currentTimeMillis() / 3600_000L).toInt()
                val feelsC = if (feels != null && idx < feels.length()) feels.getDouble(idx) else temp
                val hum = if (humA != null && idx < humA.length()) humA.getInt(idx) else -1

                val place = reversePlace(lat, lon)
                Weather(
                    tempC = temp, feelsLikeC = feelsC, humidity = hum,
                    windKmh = wind, code = code, summary = codeToText(code),
                    place = place, source = "Open-Meteo"
                )
            }
        } catch (e: Throwable) {
            Weather(
                tempC = Double.NaN, feelsLikeC = Double.NaN, humidity = -1,
                windKmh = Double.NaN, code = -1, summary = "Network error: ${e.message}",
                place = "Unknown", source = "error"
            )
        }
    }

    private fun reversePlace(lat: Double, lon: Double): String {
        return try {
            val url = "https://api.open-meteo.com/v1/geocoding?latitude=%.4f&longitude=%.4f&count=1&language=en&format=json".format(lat, lon)
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "{}")
                val arr = json.optJSONArray("results")
                if (arr != null && arr.length() > 0) {
                    val o = arr.getJSONObject(0)
                    val name = o.optString("name", "?")
                    val admin = o.optString("admin1", "")
                    val country = o.optString("country", "")
                    listOf(name, admin, country).filter { it.isNotBlank() }.joinToString(", ")
                } else String.format(Locale.US, "%.3f, %.3f", lat, lon)
            }
        } catch (_: Throwable) {
            String.format(Locale.US, "%.3f, %.3f", lat, lon)
        }
    }

    fun codeToText(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51 -> "Light drizzle"
        53 -> "Drizzle"
        55 -> "Heavy drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Slight rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Slight snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Slight showers"
        81 -> "Showers"
        82 -> "Violent showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm + hail"
        else -> "Unknown ($code)"
    }
}