package com.taskdroid.manager.fragments

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.taskdroid.manager.R
import com.taskdroid.manager.ui.infoCard
import com.taskdroid.manager.ui.kv
import com.taskdroid.manager.ui.sectionTitle
import com.taskdroid.manager.util.DeviceInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemFragment : BaseInfoFragment() {
    private var loaded = false
    private var gain: Triple<String, String, String>? = null

    override val intervalMs = 4000L

    override fun populate() {
        if (!loaded) {
            content.removeAllViews()
            loaded = true
            build()
        }
    }

    private fun build() {
        content.sectionTitle("Device System Info")

        content.infoCard("HARDWARE") {
            kv("Manufacturer", Build.MANUFACTURER)
            kv("Model", Build.MODEL)
            kv("Device", Build.DEVICE)
            kv("Product", Build.PRODUCT)
            kv("Board", Build.BOARD)
            kv("Hardware", Build.HARDWARE)
            kv("Radio version", Build.getRadioVersion() ?: "n/a")
            kv("Bootloader version", Build.BOOTLOADER)
            kv("Chipset name", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.ifBlank { "read in CPU tab" } else "read in CPU tab")
        }

        content.infoCard("SOFTWARE") {
            kv("Android version", "${Build.VERSION.RELEASE} (${codename(Build.VERSION.SDK_INT)})")
            kv("SDK / API level", Build.VERSION.SDK_INT.toString())
            kv("Security patch", DeviceInfo.securityPatch())
            kv("Build ID", Build.ID)
            kv("Build display", Build.DISPLAY)
            kv("Build time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(Build.TIME)))
            kv("Kernel version", DeviceInfo.kernelVersion())
            kv("Java VM", System.getProperty("java.vm.name", "Android"))
            kv("SELinux", DeviceInfo.seLinux())
            kv("Build type", Build.TYPE)
            kv("Tags", Build.TAGS)
        }

        content.infoCard("BOOTLOADER / SECURITY STATE") {
            val info = bootloaderInfo()
            kv("Bootloader status", info.first)
            kv("Notes", info.second)
            kv("Raw props", info.third)
            kv("Fingerprint", DeviceInfo.buildFingerprint())
        }

        content.infoCard("SENSORS (${sensorList().size})") {
            if (sensorList().isEmpty()) {
                kv("Sensors", "none detected")
            } else {
                sensorList().forEach { s ->
                    kv(s.first, s.second)
                }
            }
        }

        content.infoCard("UPTIME & ENVIRONMENT") {
            val upt = SystemClock.elapsedRealtime()
            val days = upt / 86400000
            val hours = (upt % 86400000) / 3600000
            val mins = (upt % 3600000) / 60000
            val secs = (upt % 60000) / 1000
            kv("Uptime", "%dd %02dh %02dm %02ds".format(Locale.US, days, hours, mins, secs))
            kv("Boot time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(System.currentTimeMillis() - upt)))
        }
    }

    private fun bootloaderInfo(): Triple<String, String, String> {
        if (gain != null) return gain!!
        gain = DeviceInfo.bootloaderState()
        return gain!!
    }

    private fun sensorList(): List<Pair<String, String>> {
        val sm = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val all = sm.getSensorList(Sensor.TYPE_ALL)
        return if (all.isEmpty()) emptyList()
        else all.map { it.name to sensorType(it.type) }.sortedBy { it.first }
    }

    private fun sensorType(t: Int): String = when (t) {
        Sensor.TYPE_ACCELEROMETER -> "acceleration"
        Sensor.TYPE_GYROSCOPE -> "gyro"
        Sensor.TYPE_MAGNETIC_FIELD -> "magnetometer"
        Sensor.TYPE_LIGHT -> "light"
        Sensor.TYPE_PROXIMITY -> "proximity"
        Sensor.TYPE_PRESSURE -> "barometer"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "ambient temp"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "humidity"
        Sensor.TYPE_STEP_COUNTER -> "step counter"
        Sensor.TYPE_STEP_DETECTOR -> "step detector"
        Sensor.TYPE_HEART_RATE -> "heart rate"
        Sensor.TYPE_ORIENTATION -> "orientation"
        Sensor.TYPE_LINEAR_ACCELERATION -> "linear accel"
        Sensor.TYPE_ROTATION_VECTOR -> "rotation vector"
        Sensor.TYPE_GRAVITY -> "gravity"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "motion detect"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "game rotation"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "geo mag rotation"
        Sensor.TYPE_POSE_6DOF -> "6DoF pose"
        else -> "sensor"
    }

    private fun codename(sdk: Int): String = when (sdk) {
        26 -> "Oreo"; 27 -> "Oreo"; 28 -> "Pie"; 29 -> "Android 10"; 30 -> "11"; 31 -> "12"
        32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; else -> ""
    }
}