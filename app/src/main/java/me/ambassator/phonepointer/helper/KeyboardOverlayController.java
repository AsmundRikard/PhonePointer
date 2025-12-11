package me.ambassator.phonepointer.helper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import me.ambassator.phonepointer.util.DebugLogger;
import me.ambassator.phonepointer.util.U;
import rikka.shizuku.Shizuku;

/**
 * Shows an overlay with EditText on the phone screen.
 * Uses system keyboard (Gboard etc) and relays typed text to external display.
 */
public class KeyboardOverlayController {

    private static final String TAG = "KeyboardOverlay";
    private static KeyboardOverlayController instance;

    private View overlayView;
    private WindowManager windowManager;
    private EditText editText;
    private int targetDisplayId = -1;
    private String lastText = "";

    private KeyboardOverlayController() {
    }

    public static KeyboardOverlayController getInstance() {
        if (instance == null) {
            instance = new KeyboardOverlayController();
        }
        return instance;
    }

    public void show(Context context, int displayId) {
        if (overlayView != null)
            return;

        targetDisplayId = displayId;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null)
            return;

        // Create overlay layout
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#E0000000"));
        int pad = U.dpToPx(context, 16);
        container.setPadding(pad, pad, pad, pad);
        container.setGravity(Gravity.CENTER);

        // Title
        TextView title = new TextView(context);
        title.setText("Type here to send to external display");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        title.setPadding(0, 0, 0, U.dpToPx(context, 8));
        container.addView(title);

        // EditText for keyboard input
        editText = new EditText(context);
        editText.setHint("Start typing...");
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
        editText.setBackgroundColor(Color.parseColor("#333333"));
        editText.setPadding(U.dpToPx(context, 12), U.dpToPx(context, 8), U.dpToPx(context, 12), U.dpToPx(context, 8));
        editText.setImeOptions(EditorInfo.IME_ACTION_SEND);
        editText.setSingleLine(false);
        editText.setMaxLines(4);

        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        editParams.bottomMargin = U.dpToPx(context, 8);
        container.addView(editText, editParams);

        // Close button
        TextView closeBtn = new TextView(context);
        closeBtn.setText("Close");
        closeBtn.setTextColor(Color.parseColor("#64B5F6"));
        closeBtn.setTextSize(16f);
        closeBtn.setPadding(U.dpToPx(context, 16), U.dpToPx(context, 8), U.dpToPx(context, 16), U.dpToPx(context, 8));
        closeBtn.setOnClickListener(v -> hide());
        container.addView(closeBtn);

        // Watch for text changes
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Detect what was typed
                String current = s.toString();
                if (current.length() > lastText.length()) {
                    // New text added
                    String added = current.substring(lastText.length());
                    injectText(added);
                } else if (current.length() < lastText.length()) {
                    // Text deleted - send backspace
                    int deleted = lastText.length() - current.length();
                    for (int i = 0; i < deleted; i++) {
                        injectKeyEvent(KeyEvent.KEYCODE_DEL);
                    }
                }
                lastText = current;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Handle Enter key
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                injectKeyEvent(KeyEvent.KEYCODE_ENTER);
                return true;
            }
            return false;
        });

        // Wrap in FrameLayout for positioning
        FrameLayout wrapper = new FrameLayout(context);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        containerParams.gravity = Gravity.BOTTOM;
        wrapper.addView(container, containerParams);
        wrapper.setOnClickListener(v -> hide()); // Click outside to close

        overlayView = wrapper;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                U.getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        try {
            windowManager.addView(overlayView, params);

            // Focus EditText and show keyboard
            editText.requestFocus();
            editText.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);

            DebugLogger.d(TAG, "Keyboard overlay shown for display " + targetDisplayId);
        } catch (Exception e) {
            DebugLogger.e(TAG, "Failed to show keyboard overlay", e);
            overlayView = null;
        }
    }

    private void injectText(String text) {
        if (targetDisplayId == -1 || text.isEmpty())
            return;

        new Thread(() -> {
            try {
                // Escape special characters for shell
                String escaped = text.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("$", "\\$")
                        .replace("`", "\\`")
                        .replace(" ", "%s"); // %s is space for input command

                String cmd = "input -d " + targetDisplayId + " text \"" + escaped + "\"";
                DebugLogger.d(TAG, "Injecting text: " + cmd);
                Process p = Shizuku.newProcess(new String[] { "sh", "-c", cmd }, null, null);
                p.waitFor();
            } catch (Exception e) {
                DebugLogger.e(TAG, "Failed to inject text", e);
            }
        }).start();
    }

    private void injectKeyEvent(int keyCode) {
        if (targetDisplayId == -1)
            return;

        new Thread(() -> {
            try {
                String cmd = "input -d " + targetDisplayId + " keyevent " + keyCode;
                DebugLogger.d(TAG, "Injecting keyevent: " + cmd);
                Process p = Shizuku.newProcess(new String[] { "sh", "-c", cmd }, null, null);
                p.waitFor();
            } catch (Exception e) {
                DebugLogger.e(TAG, "Failed to inject keyevent", e);
            }
        }).start();
    }

    public void hide() {
        if (overlayView != null && windowManager != null) {
            try {
                // Hide keyboard first
                InputMethodManager imm = (InputMethodManager) overlayView.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && editText != null) {
                    imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                }

                windowManager.removeView(overlayView);
            } catch (Exception e) {
                DebugLogger.e(TAG, "Failed to hide overlay", e);
            }
            overlayView = null;
            editText = null;
            lastText = "";
        }
    }

    public boolean isShowing() {
        return overlayView != null;
    }
}
