package me.ambassator.phonepointer.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/**
 * Utility helpers modeled after Taskbar's U.java for display/overlay helpers.
 */
public class U {

    /**
     * Gets the external display ID, or default display if none found.
     */
    public static int getExternalDisplayID(Context context) {
        Display display = getExternalDisplay(context);
        if (display == null)
            return Display.DEFAULT_DISPLAY;
        int id = display.getDisplayId();
        DebugLogger.d("DisplayTarget", "External display ID: " + id + " DEFAULT_DISPLAY: " + Display.DEFAULT_DISPLAY);
        return id;
    }

    /**
     * Gets the external display: returns the last non-default display if present,
     * otherwise null.
     */
    public static Display getExternalDisplay(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null)
            return null;

        Display[] displays = dm.getDisplays();
        if (displays.length == 0)
            return null;

        // Find the last non-default display
        for (int i = displays.length - 1; i >= 0; i--) {
            if (displays[i].getDisplayId() != Display.DEFAULT_DISPLAY)
                return displays[i];
        }

        return null;
    }

    /**
     * Checks if an external display is connected.
     */
    public static boolean hasExternalDisplay(Context context) {
        Display display = getExternalDisplay(context);
        return display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY;
    }

    /**
     * Gets the appropriate overlay type based on API level.
     */
    public static int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static DisplayInfo getDisplayInfo(Context context) {
        Display display = null;

        // On API 30+, if context is a DisplayContext (e.g., from createDisplayContext),
        // use context.getDisplay() to get the correct display.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                display = context.getDisplay();
            } catch (UnsupportedOperationException e) {
                // Some contexts don't support getDisplay()
            }
        }

        // Fallback to searching for external display
        if (display == null) {
            display = getExternalDisplay(context);
        }

        if (display == null)
            return new DisplayInfo(0, 0, 0, 0, false);

        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        int density = metrics.densityDpi;
        DebugLogger.d("U", "getDisplayInfo: display=" + display.getDisplayId() + " w=" + metrics.widthPixels + " h="
                + metrics.heightPixels);
        return new DisplayInfo(metrics.widthPixels, metrics.heightPixels, density, density, false);
    }

    /**
     * Converts dp to pixels.
     */
    public static int dpToPx(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Converts pixels to dp.
     */
    public static float pxToDp(Context context, int px) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    /**
     * Map swipe sensitivity percent (0-200) to an actual multiplier.
     * 100% -> 0.5x (lower default), 200% -> 1.0x (old default).
     */
    public static float swipeMultiplierFromPercent(int percent) {
        int p = Math.max(25, Math.min(400, percent));
        return p / 200f;
    }

    /**
     * Map gyro sensitivity percent (0-200) to a faster multiplier.
     * 100% -> ~2700 (about 3x old baseline), 50% -> ~1350, 200% -> ~5400.
     */
    public static float gyroMultiplierFromPercent(int percent) {
        int p = Math.max(25, Math.min(400, percent));
        return 27f * p;
    }
}
