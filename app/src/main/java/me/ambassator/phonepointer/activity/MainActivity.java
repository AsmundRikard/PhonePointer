package me.ambassator.phonepointer.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.HorizontalScrollView;

import androidx.appcompat.app.AppCompatActivity;

import me.ambassator.phonepointer.R;
import me.ambassator.phonepointer.helper.DesktopModeInputController;
import me.ambassator.phonepointer.util.Constants;
import me.ambassator.phonepointer.util.DebugLogger;
import me.ambassator.phonepointer.util.U;
import rikka.shizuku.Shizuku;

/**
 * Phone UI that mirrors Taskbar's desktop input registration (touchpad + cursor
 * attach).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PhonePointer";
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 100;

    private final DesktopModeInputController inputController = DesktopModeInputController.getInstance();
    private DisplayManager displayManager;
    private WindowManager externalWindowManager;

    private FrameLayout touchpadContainer;
    private TextView statusText;
    private int externalDisplayId = Display.DEFAULT_DISPLAY;
    private int lastExternalDisplayId = Display.DEFAULT_DISPLAY;
    private boolean externalReady = false;
    private boolean shizukuPermissionGranted = false;
    private boolean dimScreen = false;
    private boolean gyroSlow = false;
    private int swipeSens = 100;
    private int gyroSensX = 100;
    private int gyroSensY = 100;

    // Shizuku listeners
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Shizuku connected", Toast.LENGTH_SHORT).show();
            checkShizukuPermission();
        });
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        runOnUiThread(() -> {
            shizukuPermissionGranted = false;
            Toast.makeText(MainActivity.this, "Shizuku disconnected", Toast.LENGTH_SHORT).show();
        });
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            shizukuPermissionGranted = (grantResult == PackageManager.PERMISSION_GRANTED);
            runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    shizukuPermissionGranted ? "Shizuku permission granted!" : "Shizuku permission denied",
                    Toast.LENGTH_SHORT).show());
        }
    };

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            checkDisplays();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            checkDisplays();
        }

        @Override
        public void onDisplayChanged(int displayId) {
            checkDisplays();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        DebugLogger.init(getApplicationContext());

        // Register Shizuku listeners FIRST
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);

        // Check overlay permission needed for cursor overlay
        if (!U.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        // Check Shizuku permission (if already connected)
        checkShizukuPermission();

        buildUi();

        // Register display listener
        if (displayManager != null)
            displayManager.registerDisplayListener(displayListener, null);

        // Initial display check
        checkDisplays();
    }

    @Override
    protected void onResume() {
        super.onResume();
        attachTouchpad();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inputController.setGyroEnabled(this, false);
    }

    private void buildUi() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1C1E"));
        layout.setPadding(24, 48, 24, 24);

        statusText = new TextView(this);
        statusText.setText("Waiting for external display...");
        statusText.setTextSize(14f);
        statusText.setTextColor(Color.GRAY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 16, 0, 24);
        layout.addView(statusText);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER);

        navRow.addView(createNavButton("Back", () -> sendKeyEvent(KeyEvent.KEYCODE_BACK)));
        navRow.addView(createNavButton("Home", () -> sendKeyEvent(KeyEvent.KEYCODE_HOME)));
        navRow.addView(createNavButton("Recents", () -> sendKeyEvent(KeyEvent.KEYCODE_APP_SWITCH)));
        navRow.addView(createNavButton("Apps",
                () -> me.ambassator.phonepointer.helper.AppsOverlayController.getInstance().show(this)));
        navRow.addView(createNavButton("Keyboard",
                () -> me.ambassator.phonepointer.helper.KeyboardOverlayController.getInstance().show(this,
                        externalDisplayId)));
        navRow.addView(createNavButton("Settings", this::openSettings));

        navScroll.addView(navRow);
        layout.addView(navScroll);

        touchpadContainer = new FrameLayout(this);
        LinearLayout.LayoutParams trackpadParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        trackpadParams.setMargins(0, 24, 0, 0);
        layout.addView(touchpadContainer, trackpadParams);

        setContentView(layout);
    }

    private void attachTouchpad() {
        if (touchpadContainer == null)
            return;

        if (!externalReady || externalDisplayId == Display.DEFAULT_DISPLAY) {
            inputController.detachTouchpad();
            statusText.setText("No external display connected");
            statusText.setTextColor(Color.RED);
            return;
        }

        SharedPreferences pref = U.getSharedPreferences(this);
        dimScreen = pref.getBoolean(Constants.PREF_DIM_SCREEN, false);
        gyroSlow = pref.getBoolean(Constants.PREF_DESKTOP_GYRO_SLOW, false);
        swipeSens = pref.getInt(Constants.PREF_SWIPE_SENS, 100); // UI default 100% maps to 0.5x
        gyroSensX = pref.getInt(Constants.PREF_GYRO_SENS_X,
                pref.getInt(Constants.PREF_GYRO_SENS, 100));
        gyroSensY = pref.getInt(Constants.PREF_GYRO_SENS_Y,
                pref.getInt(Constants.PREF_GYRO_SENS, 100));
        inputController.setGyroDelayNormal(gyroSlow);
        inputController.setSwipeSensitivity(U.swipeMultiplierFromPercent(swipeSens));
        inputController.setGyroSensitivity(
                U.gyroMultiplierFromPercent(gyroSensX),
                U.gyroMultiplierFromPercent(gyroSensY));
        applyScreenDim();

        if (!pref.getBoolean(Constants.PREF_DESKTOP_INPUT, true)) {
            inputController.detachTouchpad();
            return;
        }

        int targetDisplay = externalDisplayId != Display.DEFAULT_DISPLAY
                ? externalDisplayId
                : lastExternalDisplayId;
        Log.d(TAG, "attachTouchpad targetDisplay=" + targetDisplay);
        inputController.setTargetDisplayId(targetDisplay);
        // Use fullscreen touchpad for maximum surface area on the phone.
        inputController.attachTouchpadFullscreen(this, touchpadContainer);
        inputController.setGyroEnabled(this, pref.getBoolean(Constants.PREF_DESKTOP_GYRO, false));
    }

    private void checkShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Waiting for Shizuku...", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuPermissionGranted = true;
                Toast.makeText(this, "Shizuku ready!", Toast.LENGTH_SHORT).show();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this, "Please grant Shizuku permission in Shizuku app", Toast.LENGTH_LONG).show();
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Shizuku error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkDisplays() {
        if (displayManager == null)
            return;

        // Log all displays
        for (Display d : displayManager.getDisplays()) {
            DebugLogger.d("Displays",
                    "Display ID: " + d.getDisplayId() + " Name: " + d.getName() + " State: " + d.getState());
        }

        Display external = U.getExternalDisplay(this);

        if (external != null) {
            if (externalDisplayId != external.getDisplayId()) {
                externalDisplayId = external.getDisplayId();
                lastExternalDisplayId = externalDisplayId;
                externalReady = true;
                DebugLogger.d(TAG, "External display detected: " + externalDisplayId);
                attachCursorToDisplay(external);
                statusText.setText("Connected to Display " + externalDisplayId);
                statusText.setTextColor(Color.GREEN);
                attachTouchpad();
            }
        } else {
            externalReady = false;
            detachCursor();
            statusText.setText("External display disconnected");
            statusText.setTextColor(Color.GRAY);
            // do not fall back to phone display; keep lastExternalDisplayId for when it
            // returns
            attachTouchpad();
        }
    }

    private void attachCursorToDisplay(Display display) {
        Context displayContext = createDisplayContext(display);
        externalWindowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);

        inputController.setTargetDisplayId(display.getDisplayId());
        inputController.attachCursor(displayContext, externalWindowManager);
        inputController.updateCursorBounds(displayContext);

        Toast.makeText(this, "Cursor attached to display " + display.getDisplayId(), Toast.LENGTH_SHORT).show();
    }

    private void detachCursor() {
        inputController.detachCursor();
    }

    private Button createNavButton(String text, Runnable onClick) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(v -> onClick.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(4, 0, 4, 0);
        button.setLayoutParams(params);

        return button;
    }

    private void sendKeyEvent(int keyCode) {
        if (externalDisplayId == Display.DEFAULT_DISPLAY) {
            Toast.makeText(this, "No external display", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use shell command for key events
        new Thread(() -> {
            try {
                if (!shizukuPermissionGranted)
                    return;
                String cmd = "input -d " + externalDisplayId + " keyevent " + keyCode;
                Process process = Shizuku.newProcess(new String[] { "sh", "-c", cmd }, null, null);
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void applyScreenDim() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (dimScreen) {
            lp.screenBrightness = 0.05f; // very dim but still interactive
        } else {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }
        getWindow().setAttributes(lp);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister Shizuku listeners
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);

        if (displayManager != null)
            displayManager.unregisterDisplayListener(displayListener);
        inputController.detachTouchpad();
        inputController.detachCursor();
    }
}
