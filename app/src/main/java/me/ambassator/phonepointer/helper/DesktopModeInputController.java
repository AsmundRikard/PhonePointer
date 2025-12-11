package me.ambassator.phonepointer.helper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import me.ambassator.phonepointer.R;
import me.ambassator.phonepointer.util.DisplayInfo;
import me.ambassator.phonepointer.util.U;

/**
 * Port of Taskbar's DesktopModeInputController for cursor overlay, touchpad input, and gyro control.
 */
public class DesktopModeInputController {

    // Defaults are intentionally modest; can be overridden via setters.
    private static final float CURSOR_PULSE_SCALE = 1.12f;
    private static final float DEFAULT_SWIPE_MULTIPLIER = 1.0f;
    private static final float DEFAULT_GYRO_MULTIPLIER = 2700f;

    private static DesktopModeInputController theInstance;

    private WeakReference<View> cursorView = new WeakReference<>(null);
    private WeakReference<WindowManager> cursorWindowManager = new WeakReference<>(null);
    private WindowManager.LayoutParams cursorLayoutParams;
    private int cursorBoundsWidth;
    private int cursorBoundsHeight;
    private int cursorDisplayId = Display.DEFAULT_DISPLAY;
    private boolean hasCursorLocation;
    private float cursorX;
    private float cursorY;

    private WeakReference<ViewGroup> touchpadContainer = new WeakReference<>(null);
    private View touchpadView;
    private float lastTouchX;
    private float lastTouchY;
    private boolean touchpadFullscreen;
    private TextView debugView;
    private boolean isDragging;
    private float downX;
    private float downY;
    private float swipeX;
    private float swipeY;
    private int touchSlop;
    private boolean gyroEnabled;
    private boolean suppressGyro;
    private SensorManager sensorManager;
    private SensorEventListener gyroListener;
    private long lastGyroTimestamp;
    private int gyroDelay = SensorManager.SENSOR_DELAY_GAME;
    private float swipeMultiplier = DEFAULT_SWIPE_MULTIPLIER;
    private float gyroMultiplierX = DEFAULT_GYRO_MULTIPLIER;
    private float gyroMultiplierY = DEFAULT_GYRO_MULTIPLIER;

    private DesktopModeInputController() {
    }

    public static DesktopModeInputController getInstance() {
        if (theInstance == null)
            theInstance = new DesktopModeInputController();

        return theInstance;
    }

    /**
     * Sets the target display ID for input injection.
     * Call this to direct clicks to a specific display (e.g., external display).
     */
    public synchronized void setTargetDisplayId(int displayId) {
        cursorDisplayId = displayId;
    }

    /**
     * Gets the current target display ID.
     */
    public int getTargetDisplayId() {
        return cursorDisplayId;
    }

    public synchronized void attachCursor(Context context, WindowManager windowManager) {
        if (windowManager == null || !U.canDrawOverlays(context))
            return;

        View existing = cursorView.get();
        if (existing != null) {
            WindowManager existingWm = cursorWindowManager.get();
            if (existingWm == windowManager) {
                updateCursorBounds(context);
                return;
            } else
                detachCursor();
        }

        int size = context.getResources().getDimensionPixelSize(R.dimen.tb_desktop_cursor_size);

        cursorLayoutParams = new WindowManager.LayoutParams(
                size,
                size,
                U.getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        cursorLayoutParams.gravity = Gravity.TOP | Gravity.START;

        View cursor = buildCursorView(context, size);

        updateCursorBounds(context, size);
        if (!hasCursorLocation)
            centerCursor();

        cursorLayoutParams.x = Math.round(cursorX);
        cursorLayoutParams.y = Math.round(cursorY);
        cursorDisplayId = windowManager.getDefaultDisplay().getDisplayId();

        try {
            windowManager.addView(cursor, cursorLayoutParams);
            cursorView = new WeakReference<>(cursor);
            cursorWindowManager = new WeakReference<>(windowManager);
        } catch (IllegalStateException | IllegalArgumentException ignored) {
        }
    }

    public synchronized void detachCursor() {
        View view = cursorView.get();
        WindowManager wm = cursorWindowManager.get();
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (IllegalArgumentException ignored) {
            }
        }

        cursorView = new WeakReference<>(null);
        cursorWindowManager = new WeakReference<>(null);
        cursorLayoutParams = null;
        cursorBoundsWidth = 0;
        cursorBoundsHeight = 0;
        cursorDisplayId = Display.DEFAULT_DISPLAY;
        hasCursorLocation = false;
    }

    public synchronized void attachTouchpad(Context context, FrameLayout container) {
        attachTouchpad(context, container, false);
    }

    public synchronized void attachTouchpadFullscreen(Context context, FrameLayout container) {
        attachTouchpad(context, container, true);
    }

    private void attachTouchpad(Context context, FrameLayout container, boolean fullscreen) {
        if (container == null) {
            detachTouchpad();
            return;
        }

        // Set target display to external display if available
        int externalDisplayId = U.getExternalDisplayID(context);
        if (externalDisplayId != Display.DEFAULT_DISPLAY) {
            cursorDisplayId = externalDisplayId;
        }

        ViewGroup currentContainer = touchpadContainer.get();
        if (touchpadView != null && currentContainer == container && touchpadFullscreen == fullscreen)
            return;

        detachTouchpad();

        FrameLayout pad = new FrameLayout(context);
        pad.setClickable(true);
        pad.setFocusable(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        int padWidth = fullscreen
                ? FrameLayout.LayoutParams.MATCH_PARENT
                : context.getResources().getDimensionPixelSize(R.dimen.tb_touchpad_width);
        int padHeight = fullscreen
                ? FrameLayout.LayoutParams.MATCH_PARENT
                : context.getResources().getDimensionPixelSize(R.dimen.tb_touchpad_height);
        int padMargin = fullscreen ? 0 : context.getResources().getDimensionPixelSize(R.dimen.tb_touchpad_margin);
        int padPadding = context.getResources().getDimensionPixelSize(R.dimen.tb_touchpad_padding);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(padWidth, padHeight);
        params.gravity = fullscreen ? Gravity.CENTER : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.setMargins(padMargin, padMargin, padMargin, padMargin);
        pad.setLayoutParams(params);
        pad.setPadding(padPadding, padPadding, padPadding, padPadding);

        pad.setBackground(createTouchpadBackground(context));

        TextView label = new TextView(context);
        label.setText(fullscreen
                ? context.getString(R.string.tb_touchpad_label_fullscreen)
                : context.getString(R.string.tb_touchpad_label));
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, fullscreen ? 18 : 14);
        label.setAlpha(0.85f);

        debugView = new TextView(context);
        debugView.setTextColor(Color.WHITE);
        debugView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        debugView.setAlpha(0.8f);

        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = fullscreen ? Gravity.TOP | Gravity.CENTER_HORIZONTAL
                : Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        labelParams.bottomMargin = dpToPx(context, 4);
        labelParams.topMargin = fullscreen ? dpToPx(context, 16) : 0;
        pad.addView(label, labelParams);

        FrameLayout.LayoutParams debugParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        debugParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        debugParams.bottomMargin = dpToPx(context, 8);
        pad.addView(debugView, debugParams);

        pad.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    downX = cursorX;
                    downY = cursorY;
                    swipeX = cursorX;
                    swipeY = cursorY;
                    isDragging = false;
                    v.setPressed(true);
                    updateDebug("touch down");
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    if (!gyroEnabled) {
                        moveCursorBy(context, dx, dy);
                        updateDebug("touch move dx=" + Math.round(dx) + " dy=" + Math.round(dy));
                    } else {
                        if (!isDragging && shouldStartDrag(dx, dy)) {
                            isDragging = ShizukuInputHelper.startDrag(cursorDisplayId, swipeX, swipeY);
                            suppressGyro = true;
                        }
                        if (isDragging) {
                            swipeX = swipeX + (dx * swipeMultiplier);
                            swipeY = swipeY + (dy * swipeMultiplier);
                            float sendX = clamp(swipeX, 0, cursorBoundsWidth);
                            float sendY = clamp(swipeY, 0, cursorBoundsHeight);
                            ShizukuInputHelper.moveDrag(cursorDisplayId, sendX, sendY);
                            // Keep on-screen cursor indicator in sync with swipe location for accuracy.
                            cursorX = sendX;
                            cursorY = sendY;
                            applyCursorPosition();
                            updateDebug("swipe drag x=" + Math.round(sendX) + " y=" + Math.round(sendY));
                        }
                    }

                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (isDragging) {
                        float endX = gyroEnabled ? clamp(swipeX, 0, cursorBoundsWidth) : cursorX;
                        float endY = gyroEnabled ? clamp(swipeY, 0, cursorBoundsHeight) : cursorY;
                        ShizukuInputHelper.endDrag(cursorDisplayId, endX, endY);
                        // Restore visual cursor to where the drag began without sending extra input.
                        cursorX = downX;
                        cursorY = downY;
                        applyCursorPosition();
                    } else
                        performClick(context);
                    isDragging = false;
                    suppressGyro = false;
                    updateDebug("touch up");
                    pulseCursor();
                    return true;
                default:
                    return false;
            }
        });

        container.addView(pad);
        pad.bringToFront();

        touchpadContainer = new WeakReference<>(container);
        touchpadView = pad;
        touchpadFullscreen = fullscreen;
    }

    public synchronized void detachTouchpad() {
        ViewGroup container = touchpadContainer.get();
        if (container != null && touchpadView != null) {
            container.removeView(touchpadView);
        }

        touchpadContainer = new WeakReference<>(null);
        touchpadView = null;
        touchpadFullscreen = false;
    }

    public synchronized void updateCursorBounds(Context context) {
        int size = cursorLayoutParams != null
                ? cursorLayoutParams.width
                : context.getResources().getDimensionPixelSize(R.dimen.tb_desktop_cursor_size);
        updateCursorBounds(context, size);
    }

    private void updateCursorBounds(Context context, int cursorSize) {
        int width = 0;
        int height = 0;

        WindowManager wm = cursorWindowManager.get();
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            if (display != null) {
                Point real = new Point();
                display.getRealSize(real);
                width = real.x;
                height = real.y;
            }
        }

        if (width == 0 || height == 0) {
            DisplayInfo info = U.getDisplayInfo(context);
            width = info.width;
            height = info.height;
        }

        cursorBoundsWidth = Math.max(0, width - cursorSize);
        cursorBoundsHeight = Math.max(0, height - cursorSize);
        cursorX = clamp(cursorX, 0, cursorBoundsWidth);
        cursorY = clamp(cursorY, 0, cursorBoundsHeight);
        hasCursorLocation = hasCursorLocation || (cursorX != 0 || cursorY != 0);
    }

    private void centerCursor() {
        cursorX = cursorBoundsWidth / 2f;
        cursorY = cursorBoundsHeight / 2f;
        hasCursorLocation = true;
    }

    private void moveCursorBy(Context context, float dx, float dy) {
        if (cursorLayoutParams == null) {
            int size = context.getResources().getDimensionPixelSize(R.dimen.tb_desktop_cursor_size);
            updateCursorBounds(context, size);
        } else if (cursorBoundsWidth == 0 || cursorBoundsHeight == 0)
            updateCursorBounds(context, cursorLayoutParams.width);

        cursorX = clamp(cursorX + (dx * swipeMultiplier), 0, cursorBoundsWidth);
        cursorY = clamp(cursorY + (dy * swipeMultiplier), 0, cursorBoundsHeight);

        applyCursorPosition();
        updateDebug("cursor " + Math.round(cursorX) + "," + Math.round(cursorY));
    }

    private boolean shouldStartDrag() {
        float dx = cursorX - downX;
        float dy = cursorY - downY;
        return Math.hypot(dx, dy) > touchSlop;
    }

    private boolean shouldStartDrag(float dx, float dy) {
        return Math.hypot(dx, dy) > touchSlop;
    }

    private void performClick(Context context) {
        ShizukuInputHelper.click(cursorDisplayId, cursorX, cursorY);
    }

    private void applyCursorPosition() {
        WindowManager wm = cursorWindowManager.get();
        View view = cursorView.get();
        if (wm != null && view != null && cursorLayoutParams != null) {
            cursorLayoutParams.x = Math.round(cursorX);
            cursorLayoutParams.y = Math.round(cursorY);

            try {
                wm.updateViewLayout(view, cursorLayoutParams);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void pulseCursor() {
        View view = cursorView.get();
        if (view != null) {
            view.animate()
                    .scaleX(CURSOR_PULSE_SCALE)
                    .scaleY(CURSOR_PULSE_SCALE)
                    .setDuration(80)
                    .withEndAction(() -> view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(80)
                            .start())
                    .start();
        }
    }

    private View buildCursorView(Context context, int size) {
        View view = new View(context);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.WHITE);
        drawable.setStroke(Math.max(2, (int) (size * 0.15f)), Color.parseColor("#44000000"));
        drawable.setSize(size, size);
        view.setBackground(drawable);
        view.setElevation(dpToPx(context, 4));
        return view;
    }

    private GradientDrawable createTouchpadBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#CC1C1C1E"));
        drawable.setCornerRadius(dpToPx(context, 12));
        drawable.setStroke(dpToPx(context, 1), Color.parseColor("#40FFFFFF"));
        return drawable;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void updateDebug(String message) {
        if (debugView == null)
            return;
        debugView.post(() -> debugView.setText(message));
    }

    public synchronized void setGyroEnabled(Context context, boolean enabled) {
        if (enabled == gyroEnabled)
            return;
        gyroEnabled = enabled;
        if (enabled)
            enableGyro(context);
        else
            disableGyro();
    }

    /**
     * Switch between fast (GAME) and normal gyro delay to trade responsiveness for battery.
     */
    public synchronized void setGyroDelayNormal(boolean useNormal) {
        int newDelay = useNormal ? SensorManager.SENSOR_DELAY_NORMAL : SensorManager.SENSOR_DELAY_GAME;
        if (newDelay == gyroDelay)
            return;
        gyroDelay = newDelay;
        // If already enabled, re-register listener with new delay.
        if (gyroEnabled && sensorManager != null && gyroListener != null) {
            sensorManager.unregisterListener(gyroListener);
            sensorManager.registerListener(gyroListener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), gyroDelay);
        }
    }

    private void enableGyro(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null)
            return;

        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (sensor == null)
            return;

        lastGyroTimestamp = 0;
        gyroListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (suppressGyro) {
                    lastGyroTimestamp = event.timestamp;
                    return;
                }

                if (lastGyroTimestamp != 0) {
                    float dt = (event.timestamp - lastGyroTimestamp) / 1_000_000_000f;
                    // event.values: [rot x, rot y, rot z]
                    // Rotate phone left/right (yaw around Z) moves cursor left/right.
                    float dx = -event.values[2] * gyroMultiplierX * dt;
                    // Tilt phone forward/back (pitch around X) moves cursor up/down (invert to
                    // match natural feel).
                    float dy = -event.values[0] * gyroMultiplierY * dt;
                    moveCursorBy(context, dx, dy);
                    updateDebug("gyro dx=" + String.format("%.2f", dx) + " dy=" + String.format("%.2f", dy));
                }
                lastGyroTimestamp = event.timestamp;
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(gyroListener, sensor, gyroDelay);
    }

    private void disableGyro() {
        if (sensorManager != null && gyroListener != null)
            sensorManager.unregisterListener(gyroListener);
        gyroListener = null;
        lastGyroTimestamp = 0;
    }

    public float getCursorX() {
        return cursorX;
    }

    public float getCursorY() {
        return cursorY;
    }

    public boolean isGyroEnabled() {
        return gyroEnabled;
    }

    /**
     * Sets swipe/touchpad sensitivity multiplier (1.0 = default, lower = slower, higher = faster).
     */
    public void setSwipeSensitivity(float multiplier) {
        swipeMultiplier = Math.max(0.1f, multiplier);
    }

    /**
     * Sets gyro-based cursor speed multiplier for both axes.
     */
    public void setGyroSensitivity(float multiplier) {
        setGyroSensitivity(multiplier, multiplier);
    }

    /**
     * Sets gyro-based cursor speed multiplier per axis.
     */
    public void setGyroSensitivity(float multiplierX, float multiplierY) {
        gyroMultiplierX = Math.max(50f, multiplierX);
        gyroMultiplierY = Math.max(50f, multiplierY);
    }
}
