package me.ambassator.phonepointer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.IBinder
import android.view.Display
import android.view.WindowManager
import android.widget.Toast

class PointerAccessibilityService : AccessibilityService() {

    // Make internal or public to be accessible from MainActivity
    internal var presentation: OverlayPresentation? = null
    
    // We don't need LocalBinder / onBind for AccessibilityService as it's system bound.
    // We use the static instance.

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "Pointer Service Connected", Toast.LENGTH_SHORT).show()
        
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                checkDisplays()
            }

            override fun onDisplayRemoved(displayId: Int) {
                checkDisplays()
            }

            override fun onDisplayChanged(displayId: Int) {
                // Resize or config change
            }
        }, null)
        
        checkDisplays()
    }

    private fun checkDisplays() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays
        
        // Find a secondary display (usually TYPE_EXTERNAL or TYPE_VIRTUAL/OVERLAY that isn't the main one)
        // For simplicity, we pick the first non-default display.
        val externalDisplay = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        
        if (externalDisplay != null) {
            if (presentation == null || presentation?.display?.displayId != externalDisplay.displayId) {
                presentation?.dismiss()
                try {
                    presentation = OverlayPresentation(this, externalDisplay)
                    presentation?.show()
                    Toast.makeText(this, "Connected to Display ${externalDisplay.displayId}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to show overlay: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            presentation?.dismiss()
            presentation = null
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // No-op
    }

    override fun onInterrupt() {
        presentation?.dismiss()
        presentation = null
        instance = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        presentation?.dismiss()
        presentation = null
        instance = null
        return super.onUnbind(intent)
    }

    // DO NOT OVERRIDE onBind.
    
    // Static accessor
    companion object {
        var instance: PointerAccessibilityService? = null
            private set
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        presentation?.dismiss()
    }

    // --- API called by MainActivity ---
    
    // We only update cursor visualization here.
    // Input injection is handled by MainActivity via Shizuku.

    fun onSensorMove(dx: Float, dy: Float) {
        presentation?.updateCursor(dx, dy)
    }
}
