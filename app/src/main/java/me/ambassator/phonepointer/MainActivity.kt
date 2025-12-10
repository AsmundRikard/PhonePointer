package me.ambassator.phonepointer

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var isCalibrated = false
    private var initialYaw = 0f
    private var initialPitch = 0f

    // Trackpad gesture detector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple UI Layout
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = android.view.Gravity.CENTER
        
        val statusText = TextView(this)
        statusText.text = "PhonePointer Active\n1. Enable Accessibility Service\n2. Connect External Display"
        statusText.textSize = 18f
        statusText.textAlignment = View.TEXT_ALIGNMENT_CENTER
        layout.addView(statusText)
        
        val settingsButton = Button(this)
        settingsButton.text = "Open Accessibility Settings"
        settingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        layout.addView(settingsButton)
        
        // Recenter Button
        val recenterButton = Button(this)
        recenterButton.text = "Recenter Cursor"
        recenterButton.setOnClickListener {
            isCalibrated = false // Will reset on next sensor event
        }
        layout.addView(recenterButton)
        
        // Shizuku Permission
        ShizukuInput.checkPermission()

        // Navigation Controls
        val navLayout = LinearLayout(this)
        navLayout.orientation = LinearLayout.HORIZONTAL
        navLayout.gravity = android.view.Gravity.CENTER
        
        // Helper to get target Display ID
        fun getTargetDisplayId(): Int {
             return PointerAccessibilityService.instance?.presentation?.display?.displayId ?: -1
        }
        
        val btnBack = Button(this)
        btnBack.text = "Back"
        btnBack.setOnClickListener { 
            val id = getTargetDisplayId()
            if (id != -1) ShizukuInput.keyEvent(id, android.view.KeyEvent.KEYCODE_BACK)
        }
        
        val btnHome = Button(this)
        btnHome.text = "Home"
        btnHome.setOnClickListener { 
            val id = getTargetDisplayId()
            if (id != -1) ShizukuInput.keyEvent(id, android.view.KeyEvent.KEYCODE_HOME)
        }
        
        val btnRecents = Button(this)
        btnRecents.text = "Recents"
        btnRecents.setOnClickListener { 
            val id = getTargetDisplayId()
            if (id != -1) ShizukuInput.keyEvent(id, android.view.KeyEvent.KEYCODE_APP_SWITCH)
        }
        
        val btnApps = Button(this)
        btnApps.text = "Apps"
        btnApps.setOnClickListener {
            PointerAccessibilityService.instance?.presentation?.toggleAppDrawer()
        }
        
        navLayout.addView(btnApps)
        navLayout.addView(btnBack)
        navLayout.addView(btnHome)
        navLayout.addView(btnRecents)
        layout.addView(navLayout)
        
        // Check Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        // Trackpad Area
        val trackpad = View(this)
        trackpad.setBackgroundColor(android.graphics.Color.DKGRAY)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        params.setMargins(16, 16, 16, 16)
        trackpad.layoutParams = params
        layout.addView(trackpad)
        
        setContentView(layout)
        
        // Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Re-using the variable name 'rotationVectorSensor' for simplicity, though now it holds Gyroscope
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        // Gestures (Tap to click, Scroll to scroll?)
        // The user said: "your main screen will turn into a trackpad so when you click, it clicks where the virtual cursor is, and when you swipe up and down it swipes on the screen"
        // Wait, "when you swipe up and down it swipes on the screen". This sounds like scroll.
        // And "when you click, it clicks".
        
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val service = PointerAccessibilityService.instance ?: return true
                val p = service.presentation ?: return true
                val displayId = p.display.displayId
                
                // If App Drawer is open, try to launch app at cursor
                if (p.isAppDrawerVisible) {
                    p.launchAppAtCursor()
                    return true
                }
                
                // Otherwise, inject tap at cursor position
                val metrics = android.util.DisplayMetrics()
                p.display.getMetrics(metrics)
                val targetX = p.cursorX * metrics.widthPixels
                val targetY = p.cursorY * metrics.heightPixels
                
                ShizukuInput.tap(displayId, targetX, targetY)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                 val service = PointerAccessibilityService.instance ?: return true
                 val p = service.presentation ?: return true
                 val displayId = p.display.displayId
                 
                 val metrics = android.util.DisplayMetrics()
                 p.display.getMetrics(metrics)
                 val targetX = p.cursorX * metrics.widthPixels
                 val targetY = p.cursorY * metrics.heightPixels
                 
                 // Swipe to scroll.
                 val scale = 2.0f
                 val endY = targetY - (distanceY * scale)
                 val endX = targetX - (distanceX * scale)
                 
                 ShizukuInput.swipe(displayId, targetX, targetY, endX, endY, 100)
                return true
            }
            
            override fun onLongPress(e: MotionEvent) {
                val service = PointerAccessibilityService.instance ?: return
                val p = service.presentation ?: return
                val displayId = p.display.displayId
                
                if (p.isAppDrawerVisible) return
                
                val metrics = android.util.DisplayMetrics()
                p.display.getMetrics(metrics)
                val targetX = p.cursorX * metrics.widthPixels
                val targetY = p.cursorY * metrics.heightPixels
                
                // Long press for context menu/resize
                ShizukuInput.longPress(displayId, targetX, targetY, 1000)
            }
        })
        
        trackpad.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            // Return true ensures we keep receiving events
            true
        }
    }

    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private var lastTimestamp: Long = 0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            if (lastTimestamp == 0L) {
                lastTimestamp = event.timestamp
                return
            }
            
            // dt in seconds. event.timestamp is nanoseconds
            val dt = (event.timestamp - lastTimestamp) * NS2S
            lastTimestamp = event.timestamp
            
            // Gyroscope values are in rad/s
            // values[0]: x-axis (Pitch)
            // values[1]: y-axis (Roll)
            // values[2]: z-axis (Yaw)
            
            // Mapping for "Pointing at screen" (Portrait phone):
            // Rotate around Z (Yaw) -> Move X
            // Rotate around X (Pitch) -> Move Y
            
            // Sensitivity
            val sensitivity = 2.0f
            
            val wx = event.values[0]
            val wz = event.values[2]
            
            // Integration
            // dX screen = -wz (Yaw). If we rotate Left (Positive Z), cursor should go Left (Negative X).
            // dY screen = -wx (Pitch). If we rotate Up (Negative X??), cursor should go Up (Negative Y).
            // Let's test signs. Standard Right-Hand Rule:
            // Thumb X (Right), fingers curl Up. Positive rotation is Tip Down? No.
            // Android X axis is right. Positive rotation is tilting top BACK? No. 
            // Let's try:
            // dX = -wz * sensitivity * dt
            // dY = -wx * sensitivity * dt
            
            // User said inverted so I will flip signs relative to "Logic".
            
            // Let's try:
            val dx = -wz * sensitivity * dt
            val dy = -wx * sensitivity * dt 
            
            PointerAccessibilityService.instance?.onSensorMove(dx.toFloat(), dy.toFloat())
        }
    }
    
    companion object {
        private val NS2S = 1.0f / 1000000000.0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
