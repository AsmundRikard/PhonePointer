package me.ambassator.phonepointer.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import me.ambassator.phonepointer.R;
import me.ambassator.phonepointer.helper.DesktopModeInputController;
import me.ambassator.phonepointer.util.Constants;
import me.ambassator.phonepointer.util.U;
import java.util.function.Consumer;

/**
 * Simple settings screen for all toggles (gyro, dimming, desktop input, logs,
 * trackpad).
 */
public class SettingsActivity extends AppCompatActivity {

        private SharedPreferences prefs;
        private DesktopModeInputController controller;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);

                prefs = U.getSharedPreferences(this);
                controller = DesktopModeInputController.getInstance();

                LinearLayout content = new LinearLayout(this);
                content.setOrientation(LinearLayout.VERTICAL);
                content.setBackgroundColor(Color.parseColor("#1C1C1E"));
                int pad = U.dpToPx(this, 20);
                content.setPadding(pad, pad * 2, pad, pad);

                // Make settings scrollable for long lists.
                androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(this);
                scrollView.addView(content, new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));

                content.addView(makeSpacer());

                addSwitch(content,
                                "Desktop input",
                                "Enable cursor/touchpad injection to the external display",
                                Constants.PREF_DESKTOP_INPUT,
                                true,
                                checked -> {
                                        prefs.edit().putBoolean(Constants.PREF_DESKTOP_INPUT, checked).apply();
                                        if (!checked)
                                                controller.detachTouchpad();
                                });

                addSwitch(content,
                                "Gyro pointer",
                                "Move the cursor with phone gyro while touchpad is open",
                                Constants.PREF_DESKTOP_GYRO,
                                false,
                                checked -> {
                                        prefs.edit().putBoolean(Constants.PREF_DESKTOP_GYRO, checked).apply();
                                        controller.setGyroEnabled(this, checked);
                                });

                addSwitch(content,
                                "Battery-friendly gyro rate",
                                "Use NORMAL sensor delay instead of GAME to save battery",
                                Constants.PREF_DESKTOP_GYRO_SLOW,
                                false,
                                checked -> {
                                        prefs.edit().putBoolean(Constants.PREF_DESKTOP_GYRO_SLOW, checked).apply();
                                        controller.setGyroDelayNormal(checked);
                                });

                addSwitch(content,
                                "Dim phone screen while trackpad is open",
                                "Lowers screen brightness to reduce battery while using the trackpad",
                                Constants.PREF_DIM_SCREEN,
                                false,
                                checked -> prefs.edit().putBoolean(Constants.PREF_DIM_SCREEN, checked).apply());

                addSlider(content,
                                "Swipe sensitivity",
                                "Adjust how fast the cursor moves with finger swipes",
                                Constants.PREF_SWIPE_SENS,
                                100, // UI shows 100%, maps to 0.5x swipe speed
                                progress -> {
                                        prefs.edit().putInt(Constants.PREF_SWIPE_SENS, progress).apply();
                                        controller.setSwipeSensitivity(U.swipeMultiplierFromPercent(progress));
                                });

                addSlider(content,
                                "Gyro sensitivity (X / yaw)",
                                "Left/right tilt speed",
                                Constants.PREF_GYRO_SENS_X,
                                100, // UI shows 100%, maps to ~2700 multiplier
                                progress -> {
                                        prefs.edit().putInt(Constants.PREF_GYRO_SENS_X, progress).apply();
                                        int y = prefs.getInt(Constants.PREF_GYRO_SENS_Y,
                                                        prefs.getInt(Constants.PREF_GYRO_SENS, 100));
                                        controller.setGyroSensitivity(
                                                        U.gyroMultiplierFromPercent(progress),
                                                        U.gyroMultiplierFromPercent(y));
                                });

                addSlider(content,
                                "Gyro sensitivity (Y / pitch)",
                                "Up/down tilt speed",
                                Constants.PREF_GYRO_SENS_Y,
                                100,
                                progress -> {
                                        prefs.edit().putInt(Constants.PREF_GYRO_SENS_Y, progress).apply();
                                        int x = prefs.getInt(Constants.PREF_GYRO_SENS_X,
                                                        prefs.getInt(Constants.PREF_GYRO_SENS, 100));
                                        controller.setGyroSensitivity(
                                                        U.gyroMultiplierFromPercent(x),
                                                        U.gyroMultiplierFromPercent(progress));
                                });

                content.addView(makeSpacer());

                Button trackpadButton = new Button(this);
                trackpadButton.setText("Open trackpad");
                trackpadButton.setOnClickListener(v -> startActivity(new Intent(this, TrackpadActivity.class)));
                content.addView(trackpadButton, defaultButtonParams());

                Button logButton = new Button(this);
                logButton.setText("View logs");
                logButton.setOnClickListener(v -> startActivity(new Intent(this, LogViewerActivity.class)));
                content.addView(logButton, defaultButtonParams());

                Button clearLogButton = new Button(this);
                clearLogButton.setText("Clear logs");
                clearLogButton.setOnClickListener(v -> {
                        me.ambassator.phonepointer.util.DebugLogger.clearLogs();
                        android.widget.Toast.makeText(this, "Logs cleared", android.widget.Toast.LENGTH_SHORT).show();
                });
                content.addView(clearLogButton, defaultButtonParams());

                setContentView(scrollView);
        }

        private void addSwitch(LinearLayout root,
                        String title,
                        String subtitle,
                        String prefKey,
                        boolean defaultValue,
                        Consumer<Boolean> onChanged) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, U.dpToPx(this, 10), 0, U.dpToPx(this, 10));

                SwitchCompat toggle = new SwitchCompat(this);
                toggle.setChecked(prefs.getBoolean(prefKey, defaultValue));
                toggle.setText(title);
                toggle.setTextColor(Color.WHITE);
                toggle.setOnCheckedChangeListener((buttonView, isChecked) -> onChanged.accept(isChecked));

                TextView sub = new TextView(this);
                sub.setText(subtitle);
                sub.setTextColor(Color.parseColor("#BBBBBB"));
                sub.setTextSize(12f);

                row.addView(toggle);
                row.addView(sub);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = U.dpToPx(this, 6);

                root.addView(row, params);
        }

        private TextView makeSpacer() {
                TextView spacer = new TextView(this);
                spacer.setHeight(U.dpToPx(this, 8));
                return spacer;
        }

        private LinearLayout.LayoutParams defaultButtonParams() {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = U.dpToPx(this, 8);
                return params;
        }

        private void addSlider(LinearLayout root,
                        String title,
                        String subtitle,
                        String prefKey,
                        int defaultPercent,
                        Consumer<Integer> onChanged) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, U.dpToPx(this, 10), 0, U.dpToPx(this, 10));

                TextView header = new TextView(this);
                header.setText(title);
                header.setTextColor(Color.WHITE);
                header.setTextSize(16f);

                TextView sub = new TextView(this);
                sub.setText(subtitle);
                sub.setTextColor(Color.parseColor("#BBBBBB"));
                sub.setTextSize(12f);

                TextView value = new TextView(this);
                value.setTextColor(Color.parseColor("#DDDDDD"));
                value.setTextSize(12f);

                SeekBar slider = new SeekBar(this);
                slider.setMax(400); // represent 0..400%
                int stored = prefs.getInt(prefKey, defaultPercent);
                slider.setProgress(stored);
                value.setText(stored + "%");

                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                int clamped = Math.max(10, progress); // avoid too-low values
                                value.setText(clamped + "%");
                                onChanged.accept(clamped);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                });

                row.addView(header);
                row.addView(sub);
                row.addView(value);
                row.addView(slider);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = U.dpToPx(this, 6);

                root.addView(row, params);
        }
}
