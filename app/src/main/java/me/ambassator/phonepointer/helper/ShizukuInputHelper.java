package me.ambassator.phonepointer.helper;

import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.InputDevice;
import android.view.MotionEvent;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;
import me.ambassator.phonepointer.util.DebugLogger;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * Port of Taskbar's Shizuku-based input injector.
 */
public class ShizukuInputHelper {

    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    private static final String TAG = "ShizukuInputHelper";
    private static long dragDownTime;
    private static boolean dragging;
    private static boolean triedDisplayField;

    private ShizukuInputHelper() {}

    static {
        try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/view/MotionEvent;");
            DebugLogger.d(TAG, "HiddenApiBypass enabled for MotionEvent");
        } catch (Throwable t) {
            DebugLogger.e(TAG, "HiddenApiBypass failed", t);
        }
    }

    public static boolean click(int displayId, float x, float y) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        if(!Shizuku.pingBinder()) return false;

        try {
            IBinder inputBinder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"));

            Class<?> stubClass = Class.forName("android.hardware.input.IInputManager$Stub");
            Object iInputManager = stubClass
                    .getMethod("asInterface", IBinder.class)
                    .invoke(null, inputBinder);

            Class<?> iInputManagerClass = Class.forName("android.hardware.input.IInputManager");
            java.lang.reflect.Method injectMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent.class, int.class);

            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0);

            down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            setDisplayIdIfPossible(down, displayId);
            setDisplayIdIfPossible(up, displayId);

            injectMethod.invoke(iInputManager, down, INJECT_INPUT_EVENT_MODE_ASYNC);
            injectMethod.invoke(iInputManager, up, INJECT_INPUT_EVENT_MODE_ASYNC);

            down.recycle();
            up.recycle();
            DebugLogger.d(TAG, "Click injected at (" + x + ", " + y + ") on display " + displayId);
            return true;
        } catch (Throwable t) {
            DebugLogger.e(TAG, "Click inject failed", t);
            return false;
        }
    }

    public static boolean startDrag(int displayId, float x, float y) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        if(!Shizuku.pingBinder()) return false;
        try {
            IBinder inputBinder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"));
            Class<?> stubClass = Class.forName("android.hardware.input.IInputManager$Stub");
            Object iInputManager = stubClass
                    .getMethod("asInterface", IBinder.class)
                    .invoke(null, inputBinder);
            Class<?> iInputManagerClass = Class.forName("android.hardware.input.IInputManager");
            java.lang.reflect.Method injectMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent.class, int.class);

            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            setDisplayIdIfPossible(down, displayId);
            injectMethod.invoke(iInputManager, down, INJECT_INPUT_EVENT_MODE_ASYNC);
            down.recycle();

            dragDownTime = now;
            dragging = true;
            DebugLogger.d(TAG, "Drag start at (" + x + "," + y + ") display " + displayId);
            return true;
        } catch (Throwable t) {
            dragging = false;
            DebugLogger.e(TAG, "Drag start failed", t);
            return false;
        }
    }

    public static void moveDrag(int displayId, float x, float y) {
        if(!dragging) return;
        injectMotion(displayId, MotionEvent.ACTION_MOVE, x, y);
    }

    public static void endDrag(int displayId, float x, float y) {
        if(!dragging) return;
        injectMotion(displayId, MotionEvent.ACTION_UP, x, y);
        dragging = false;
        DebugLogger.d(TAG, "Drag end at (" + x + "," + y + ") display " + displayId);
    }

    private static void injectMotion(int displayId, int action, float x, float y) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if(!Shizuku.pingBinder()) return;
        try {
            IBinder inputBinder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"));
            Class<?> stubClass = Class.forName("android.hardware.input.IInputManager$Stub");
            Object iInputManager = stubClass
                    .getMethod("asInterface", IBinder.class)
                    .invoke(null, inputBinder);
            Class<?> iInputManagerClass = Class.forName("android.hardware.input.IInputManager");
            java.lang.reflect.Method injectMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent.class, int.class);

            long now = SystemClock.uptimeMillis();
            MotionEvent ev = MotionEvent.obtain(dragDownTime, now, action, x, y, 0);
            ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            setDisplayIdIfPossible(ev, displayId);
            injectMethod.invoke(iInputManager, ev, INJECT_INPUT_EVENT_MODE_ASYNC);
            ev.recycle();
        } catch (Throwable t) {
            DebugLogger.e(TAG, "Inject motion failed", t);
        }
    }

    private static void setDisplayIdIfPossible(MotionEvent event, int displayId) {
        if(event == null) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MotionEvent.class.getMethod("setDisplayId", int.class).invoke(event, displayId);
                DebugLogger.d(TAG, "setDisplayId -> " + displayId);
                return;
            } catch (Throwable t) {
                DebugLogger.e(TAG, "Failed to set displayId", t);
            }
        }

        // Fallback: try setting mDisplayId field directly on older platforms / hidden API
        if (!triedDisplayField) {
            triedDisplayField = true;
        }
        try {
            java.lang.reflect.Field f = MotionEvent.class.getDeclaredField("mDisplayId");
            f.setAccessible(true);
            f.setInt(event, displayId);
            DebugLogger.d(TAG, "set mDisplayId field -> " + displayId);
        } catch (Throwable t) {
            DebugLogger.e(TAG, "Failed to set mDisplayId field", t);
        }
    }
}
