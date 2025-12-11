package me.ambassator.phonepointer.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

import me.ambassator.phonepointer.helper.KeyboardOverlayController;
import me.ambassator.phonepointer.util.DebugLogger;
import me.ambassator.phonepointer.util.U;

/**
 * AccessibilityService that detects when a text field is focused.
 * When detected on external display, triggers the keyboard overlay on phone.
 */
public class KeyboardRelayService extends AccessibilityService {

    private static final String TAG = "KeyboardRelayService";
    private static KeyboardRelayService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        DebugLogger.d(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        DebugLogger.d(TAG, "Service destroyed");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        setServiceInfo(info);
        DebugLogger.d(TAG, "Service connected and configured");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null)
            return;

        // Check if this is a text input field
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            CharSequence className = event.getClassName();
            if (className != null && isTextInputClass(className.toString())) {
                DebugLogger.d(TAG, "Text field focused: " + className);

                // Check if we have an external display
                int externalId = U.getExternalDisplayID(this);
                if (externalId != Display.DEFAULT_DISPLAY) {
                    // Show keyboard overlay for external display
                    // Note: We can't easily determine WHICH display the focus event came from,
                    // so we assume if external display exists, the focus is there.
                    KeyboardOverlayController.getInstance().show(this, externalId);
                }
            }
        }
    }

    private boolean isTextInputClass(String className) {
        return className.contains("EditText")
                || className.contains("TextView")
                || className.contains("AutoCompleteTextView")
                || className.contains("SearchView")
                || className.contains("TextInputEditText");
    }

    @Override
    public void onInterrupt() {
        DebugLogger.d(TAG, "Service interrupted");
    }

    public static boolean isRunning() {
        return instance != null;
    }
}
