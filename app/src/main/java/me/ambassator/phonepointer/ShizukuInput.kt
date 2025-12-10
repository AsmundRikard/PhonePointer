package me.ambassator.phonepointer

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

object ShizukuInput {

    private const val TAG = "ShizukuInput"

    fun checkPermission(): Boolean {
        if (Shizuku.isPreV11()) return false
        return try {
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Shizuku permission granted")
                true
            } else {
                Log.d(TAG, "Requesting Shizuku permission")
                Shizuku.requestPermission(0)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku check error", e)
            false
        }
    }

    private fun executeShell(command: String) {
        thread {
            try {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Shizuku permission not granted")
                    return@thread
                }
                
                Log.d(TAG, "Executing: $command")
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                
                // Read output for debugging
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = reader.readText()
                val error = errorReader.readText()
                
                val exitCode = process.waitFor()
                
                if (output.isNotEmpty()) Log.d(TAG, "Output: $output")
                if (error.isNotEmpty()) Log.e(TAG, "Error: $error")
                Log.d(TAG, "Exit code: $exitCode")
                
                reader.close()
                errorReader.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute: $command", e)
            }
        }
    }

    fun tap(displayId: Int, x: Float, y: Float) {
        val cmd = "input -d $displayId tap ${x.toInt()} ${y.toInt()}"
        executeShell(cmd)
    }

    fun swipe(displayId: Int, x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int = 300) {
        val cmd = "input -d $displayId swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} $durationMs"
        executeShell(cmd)
    }

    fun keyEvent(displayId: Int, keyCode: Int) {
        val cmd = "input -d $displayId keyevent $keyCode"
        executeShell(cmd)
    }
    
    // Long press for potential resize/drag operations
    fun longPress(displayId: Int, x: Float, y: Float, durationMs: Int = 1000) {
        val cmd = "input -d $displayId swipe ${x.toInt()} ${y.toInt()} ${x.toInt()} ${y.toInt()} $durationMs"
        executeShell(cmd)
    }
    
    // Drag operation for resizing windows
    fun drag(displayId: Int, x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int = 500) {
        val cmd = "input -d $displayId swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} $durationMs"
        executeShell(cmd)
    }
}
