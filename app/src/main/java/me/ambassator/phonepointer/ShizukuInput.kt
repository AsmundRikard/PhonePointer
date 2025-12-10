package me.ambassator.phonepointer

import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.concurrent.thread

/**
 * Input injection using Shizuku + IInputManager.
 * This approach directly injects MotionEvents with display targeting,
 * which is more reliable than shell commands.
 */
object ShizukuInput {

    private const val TAG = "ShizukuInput"
    private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
    
    private var dragDownTime: Long = 0
    private var isDragging = false

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

    fun click(displayId: Int, x: Float, y: Float) {
        thread {
            try {
                if (!Shizuku.pingBinder()) {
                    Log.e(TAG, "Shizuku binder not available")
                    return@thread
                }
                
                val inputBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
                
                val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val iInputManager = stubClass
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, inputBinder)
                
                val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
                val injectMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.java)
                
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0)
                
                setDisplayId(down, displayId)
                setDisplayId(up, displayId)
                
                injectMethod.invoke(iInputManager, down, INJECT_INPUT_EVENT_MODE_ASYNC)
                injectMethod.invoke(iInputManager, up, INJECT_INPUT_EVENT_MODE_ASYNC)
                
                down.recycle()
                up.recycle()
                
                Log.d(TAG, "Click injected at ($x, $y) on display $displayId")
            } catch (e: Exception) {
                Log.e(TAG, "Click injection failed", e)
            }
        }
    }

    fun startDrag(displayId: Int, x: Float, y: Float): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            
            val inputBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val iInputManager = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, inputBinder)
            
            val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
            val injectMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.java)
            
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            setDisplayId(down, displayId)
            injectMethod.invoke(iInputManager, down, INJECT_INPUT_EVENT_MODE_ASYNC)
            down.recycle()
            
            dragDownTime = now
            isDragging = true
            Log.d(TAG, "Drag started at ($x, $y) on display $displayId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start drag failed", e)
            isDragging = false
            false
        }
    }
    
    fun moveDrag(displayId: Int, x: Float, y: Float) {
        if (!isDragging) return
        injectMotion(displayId, MotionEvent.ACTION_MOVE, x, y)
    }
    
    fun endDrag(displayId: Int, x: Float, y: Float) {
        if (!isDragging) return
        injectMotion(displayId, MotionEvent.ACTION_UP, x, y)
        isDragging = false
        Log.d(TAG, "Drag ended at ($x, $y)")
    }
    
    private fun injectMotion(displayId: Int, action: Int, x: Float, y: Float) {
        try {
            if (!Shizuku.pingBinder()) return
            
            val inputBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val iInputManager = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, inputBinder)
            
            val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
            val injectMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.java)
            
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(dragDownTime, now, action, x, y, 0)
            setDisplayId(event, displayId)
            injectMethod.invoke(iInputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC)
            event.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Inject motion failed", e)
        }
    }
    
    private fun setDisplayId(event: MotionEvent?, displayId: Int) {
        if (event == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MotionEvent::class.java
                    .getMethod("setDisplayId", Int::class.java)
                    .invoke(event, displayId)
            } catch (e: Exception) {
                Log.w(TAG, "setDisplayId failed", e)
            }
        }
    }
    
    // Legacy functions using tap/swipe (kept for compatibility)
    fun tap(displayId: Int, x: Float, y: Float) = click(displayId, x, y)
    
    fun swipe(displayId: Int, x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int = 300) {
        thread {
            try {
                if (!Shizuku.pingBinder()) return@thread
                
                val inputBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
                val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val iInputManager = stubClass
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, inputBinder)
                
                val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
                val injectMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.java)
                
                val startTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, x1, y1, 0)
                setDisplayId(down, displayId)
                injectMethod.invoke(iInputManager, down, INJECT_INPUT_EVENT_MODE_ASYNC)
                down.recycle()
                
                // Interpolate move events
                val steps = 10
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val x = x1 + (x2 - x1) * t
                    val y = y1 + (y2 - y1) * t
                    val time = startTime + (durationMs * t).toLong()
                    
                    val move = MotionEvent.obtain(startTime, time, MotionEvent.ACTION_MOVE, x, y, 0)
                    setDisplayId(move, displayId)
                    injectMethod.invoke(iInputManager, move, INJECT_INPUT_EVENT_MODE_ASYNC)
                    move.recycle()
                    
                    Thread.sleep((durationMs / steps).toLong())
                }
                
                val endTime = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, x2, y2, 0)
                setDisplayId(up, displayId)
                injectMethod.invoke(iInputManager, up, INJECT_INPUT_EVENT_MODE_ASYNC)
                up.recycle()
                
                Log.d(TAG, "Swipe from ($x1,$y1) to ($x2,$y2) on display $displayId")
            } catch (e: Exception) {
                Log.e(TAG, "Swipe failed", e)
            }
        }
    }
    
    fun keyEvent(displayId: Int, keyCode: Int) {
        // Key events use a different approach - shell command for simplicity
        thread {
            try {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return@thread
                val cmd = "input -d $displayId keyevent $keyCode"
                val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                process.waitFor()
                Log.d(TAG, "Key event $keyCode sent to display $displayId")
            } catch (e: Exception) {
                Log.e(TAG, "Key event failed", e)
            }
        }
    }
    
    fun longPress(displayId: Int, x: Float, y: Float, durationMs: Int = 1000) {
        // Long press is a swipe from same position to same position
        swipe(displayId, x, y, x, y, durationMs)
    }
}
