package com.taskdroid.manager.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootUtil {
    fun shell(cmd: String, timeoutMs: Long = 3000): String? {
        return try {
            val process = ProcessBuilder(cmd.split(" ")).redirectErrorStream(true).start()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return null
            }
            process.inputStream.use { input ->
                BufferedReader(InputStreamReader(input)).readText().trim()
            }.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    fun shellNoSplit(vararg args: String): String? {
        return try {
            val process = ProcessBuilder(*args).redirectErrorStream(true).start()
            if (!process.waitFor(4000, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return null
            }
            process.inputStream.use { input ->
                BufferedReader(InputStreamReader(input)).readText().trim()
            }.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    fun getProp(name: String): String? {
        var v = shell("getprop $name")
        if (v.isNullOrEmpty()) v = shell("getprop$name")
        return v?.trim()?.takeIf { it.isNotBlank() }
    }

    fun readFile(path: String, maxLines: Int = 400): List<String> {
        return try {
            val f = java.io.File(path)
            if (!f.canRead()) return emptyList()
            f.readLines().take(maxLines)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun readFileBest(vararg paths: String): String? {
        for (p in paths) {
            val v = readFile(p).firstOrNull()?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }
}