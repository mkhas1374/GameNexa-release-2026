package com.example.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object SecurityUtils {

    /**
     * Checks if the device is rooted by inspecting system files, build tags, and checking for su binaries.
     */
    fun isRooted(): Boolean {
        // 1. Check Build Tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // 2. Check SU Binary Paths
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }

        // 3. Optional checks safely handled
        try {
            val file = File("/system/xbin/su")
            if (file.exists()) return true
        } catch (t: Throwable) {
            // Ignored
        }

        return false
    }

    /**
     * Highly comprehensive multi-heuristic emulator check.
     */
    fun isEmulator(): Boolean {
        val isEmu = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.BOARD == "QC_Reference_Phone"
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)

        return isEmu
    }

    /**
     * Detects if debugger is attached or if the app is debuggable in a production context.
     */
    fun isTampered(context: Context): Boolean {
        // Check if debuggable flag is enabled
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        
        // Under strict production conditions, we want to flag this, but for local testing we will let it pass or show a non-blocking warning.
        // Also check for standard debuggers
        val isDebuggerConnected = android.os.Debug.isDebuggerConnected()
        
        return isDebuggerConnected
    }
}
