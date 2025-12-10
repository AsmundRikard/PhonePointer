package me.ambassator.phonepointer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.hardware.display.DisplayManager
import android.widget.Toast

/**
 * Accessibility Service that manages the cursor overlay on external displays.
 * Based on Taskbar's DesktopModeInputController approach.
 */
class PointerAccessibilityService : AccessibilityService(), SensorEventListener {

    companion object {
        private const val TAG = "PointerService"
        private const val CURSOR_SIZE = 16  // dp (reduced from 32)
        private const val CURSOR_SPEED = 1.5f
        private const val GYRO_SPEED = 1200f
        
        var instance: PointerAccessibilityService? = null
            private set
    }

    // Cursor state
    private var cursorView: View? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var externalWindowManager: WindowManager? = null
    private var externalDisplayId: Int = Display.DEFAULT_DISPLAY
    
    private var cursorBoundsWidth = 0
    private var cursorBoundsHeight = 0
    var cursorX = 0f
        private set
    var cursorY = 0f
        private set

    // Sensor state
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var lastGyroTimestamp: Long = 0
    private var gyroEnabled = true

    // Display management
    private lateinit var displayManager: DisplayManager
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = checkDisplays()
        override fun onDisplayRemoved(displayId: Int) = checkDisplays()
        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service connected")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        
        checkDisplays()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        detachCursor()
        displayManager.unregisterDisplayListener(displayListener)
        disableGyro()
        Log.d(TAG, "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun checkDisplays() {
        val displays = displayManager.displays
        
        // Find external display (non-default)
        val external = displays.find { it.displayId != Display.DEFAULT_DISPLAY }
        
        if (external != null && cursorView == null) {
            Log.d(TAG, "External display found: ${external.displayId}")
            attachCursor(external)
            enableGyro()
            Toast.makeText(this, "Connected to Display ${external.displayId}", Toast.LENGTH_SHORT).show()
        } else if (external == null && cursorView != null) {
            Log.d(TAG, "External display disconnected")
            detachCursor()
            disableGyro()
        }
    }

    private fun attachCursor(display: Display) {
        val context = createDisplayContext(display)
        externalWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        externalDisplayId = display.displayId
        
        val sizePx = dpToPx(CURSOR_SIZE)
        
        cursorParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        // Build cursor view (white circle with shadow)
        cursorView = View(context).apply {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke((sizePx * 0.15f).toInt().coerceAtLeast(2), Color.parseColor("#44000000"))
                setSize(sizePx, sizePx)
            }
            background = drawable
            elevation = dpToPx(4).toFloat()
        }
        
        // Calculate bounds
        val size = android.graphics.Point()
        display.getRealSize(size)
        cursorBoundsWidth = size.x - sizePx
        cursorBoundsHeight = size.y - sizePx
        
        // Center cursor
        cursorX = cursorBoundsWidth / 2f
        cursorY = cursorBoundsHeight / 2f
        cursorParams?.x = cursorX.toInt()
        cursorParams?.y = cursorY.toInt()
        
        try {
            externalWindowManager?.addView(cursorView, cursorParams)
            Log.d(TAG, "Cursor attached to display ${display.displayId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach cursor", e)
        }
    }

    private fun detachCursor() {
        cursorView?.let { view ->
            try {
                externalWindowManager?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove cursor", e)
            }
        }
        cursorView = null
        cursorParams = null
        externalWindowManager = null
        externalDisplayId = Display.DEFAULT_DISPLAY
    }

    // Called from MainActivity to move cursor via touchpad
    fun moveCursorByTouch(dx: Float, dy: Float) {
        if (cursorView == null) return
        
        cursorX = (cursorX + dx * CURSOR_SPEED).coerceIn(0f, cursorBoundsWidth.toFloat())
        cursorY = (cursorY + dy * CURSOR_SPEED).coerceIn(0f, cursorBoundsHeight.toFloat())
        
        applyCursorPosition()
    }

    // Move cursor via gyroscope delta
    private fun moveCursorByGyro(dx: Float, dy: Float) {
        if (cursorView == null) return
        
        cursorX = (cursorX + dx).coerceIn(0f, cursorBoundsWidth.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, cursorBoundsHeight.toFloat())
        
        applyCursorPosition()
    }

    private fun applyCursorPosition() {
        cursorParams?.let { params ->
            params.x = cursorX.toInt()
            params.y = cursorY.toInt()
            
            try {
                externalWindowManager?.updateViewLayout(cursorView, params)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update cursor", e)
            }
        }
    }

    fun pulseCursor() {
        cursorView?.animate()
            ?.scaleX(1.12f)
            ?.scaleY(1.12f)
            ?.setDuration(80)
            ?.withEndAction {
                cursorView?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(80)
                    ?.start()
            }
            ?.start()
    }

    fun centerCursor() {
        cursorX = cursorBoundsWidth / 2f
        cursorY = cursorBoundsHeight / 2f
        applyCursorPosition()
        pulseCursor()
    }

    fun getDisplayId(): Int = externalDisplayId

    // Gyroscope handling
    private fun enableGyro() {
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            lastGyroTimestamp = 0
            Log.d(TAG, "Gyro enabled")
        }
    }

    private fun disableGyro() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Gyro disabled")
    }

    fun setGyroEnabled(enabled: Boolean) {
        gyroEnabled = enabled
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_GYROSCOPE || !gyroEnabled) return
        
        if (lastGyroTimestamp != 0L) {
            val dt = (event.timestamp - lastGyroTimestamp) / 1_000_000_000f
            
            // values[2] = rotation around Z axis (yaw) -> cursor X
            // values[0] = rotation around X axis (pitch) -> cursor Y
            val dx = -event.values[2] * GYRO_SPEED * dt
            val dy = -event.values[0] * GYRO_SPEED * dt
            
            moveCursorByGyro(dx, dy)
        }
        lastGyroTimestamp = event.timestamp
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
