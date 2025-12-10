package me.ambassator.phonepointer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity providing a trackpad interface for controlling the cursor.
 * Based on Taskbar's approach.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetector
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val touchSlop by lazy { android.view.ViewConfiguration.get(this).scaledTouchSlop }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            setPadding(24, 48, 24, 24)
        }

        // Title
        val title = TextView(this).apply {
            text = "PhonePointer"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        // Status
        val status = TextView(this).apply {
            text = "Waiting for external display..."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        }
        layout.addView(status)

        // Shizuku permission check
        ShizukuInput.checkPermission()

        // Navigation buttons row
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun createNavButton(text: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                this.text = text
                setOnClickListener { onClick() }
            }
        }

        navRow.addView(createNavButton("Back") {
            val id = PointerAccessibilityService.instance?.getDisplayId() ?: return@createNavButton
            ShizukuInput.keyEvent(id, android.view.KeyEvent.KEYCODE_BACK)
        })

        navRow.addView(createNavButton("Home") {
            val id = PointerAccessibilityService.instance?.getDisplayId() ?: return@createNavButton
            ShizukuInput.keyEvent(id, android.view.KeyEvent.KEYCODE_HOME)
        })

        navRow.addView(createNavButton("Recents") {
            val id = PointerAccessibilityService.instance?.getDisplayId() ?: return@createNavButton
            ShizukuInput.keyEvent(id, android.view.KeyEvent.KEYCODE_APP_SWITCH)
        })

        navRow.addView(createNavButton("Recenter") {
            PointerAccessibilityService.instance?.centerCursor()
        })

        navRow.addView(createNavButton("Apps") {
            showAppLauncher()
        })

        layout.addView(navRow)

        // Check permissions
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        // Trackpad area
        val trackpad = FrameLayout(this).apply {
            val drawable = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2E"))
                cornerRadius = dpToPx(12f)
                setStroke(dpToPx(1f).toInt(), Color.parseColor("#40FFFFFF"))
            }
            background = drawable
        }

        val trackpadLabel = TextView(this).apply {
            text = "Touchpad\n\nSwipe to move cursor\nTap to click\nSwipe while touching to drag"
            textSize = 16f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
        }
        trackpad.addView(trackpadLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        val trackpadParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            setMargins(0, 24, 0, 0)
        }
        layout.addView(trackpad, trackpadParams)

        setContentView(layout)

        // Gesture detection
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val service = PointerAccessibilityService.instance ?: return true
                val displayId = service.getDisplayId()
                if (displayId <= 0) return true
                
                // Click at cursor position
                ShizukuInput.click(displayId, service.cursorX, service.cursorY)
                service.pulseCursor()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val service = PointerAccessibilityService.instance ?: return
                val displayId = service.getDisplayId()
                if (displayId <= 0) return
                
                // Start drag
                if (ShizukuInput.startDrag(displayId, service.cursorX, service.cursorY)) {
                    isDragging = true
                    dragStartX = service.cursorX
                    dragStartY = service.cursorY
                    Toast.makeText(this@MainActivity, "Drag started", Toast.LENGTH_SHORT).show()
                }
            }
        })

        trackpad.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    v.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    
                    val service = PointerAccessibilityService.instance ?: return@setOnTouchListener true
                    
                    if (isDragging) {
                        // Move drag position
                        service.moveCursorByTouch(dx, dy)
                        ShizukuInput.moveDrag(service.getDisplayId(), service.cursorX, service.cursorY)
                    } else {
                        // Normal cursor movement (trackpad mode - disable gyro temporarily)
                        service.setGyroEnabled(false)
                        service.moveCursorByTouch(dx, dy)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    
                    if (isDragging) {
                        val service = PointerAccessibilityService.instance
                        if (service != null) {
                            ShizukuInput.endDrag(service.getDisplayId(), service.cursorX, service.cursorY)
                        }
                        isDragging = false
                    }
                    
                    // Re-enable gyro
                    PointerAccessibilityService.instance?.setGyroEnabled(true)
                }
            }
            true
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun showAppLauncher() {
        val pm = packageManager
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        
        val apps = pm.queryIntentActivities(mainIntent, 0)
            .sortedBy { it.loadLabel(pm).toString() }
        
        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val appNames = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Launch App on External Display")
            .setItems(appNames) { _, which ->
                val app = apps[which]
                val displayId = PointerAccessibilityService.instance?.getDisplayId() ?: return@setItems
                
                val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                launchIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                launchIntent.setClassName(app.activityInfo.packageName, app.activityInfo.name)
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                
                val options = android.app.ActivityOptions.makeBasic()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    options.launchDisplayId = displayId
                }
                
                try {
                    startActivity(launchIntent, options.toBundle())
                    Toast.makeText(this, "Launched ${app.loadLabel(pm)}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
