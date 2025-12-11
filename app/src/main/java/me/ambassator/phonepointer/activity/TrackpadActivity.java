package me.ambassator.phonepointer.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import me.ambassator.phonepointer.R;
import me.ambassator.phonepointer.helper.DesktopModeInputController;
import me.ambassator.phonepointer.util.Constants;
import me.ambassator.phonepointer.util.U;

/**
 * Fullscreen touchpad UI on the phone (matches Taskbar's TrackpadActivity behavior).
 */
public class TrackpadActivity extends AppCompatActivity {

    private final DesktopModeInputController controller = DesktopModeInputController.getInstance();
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.tb_touchpad_label);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(getResources().getColor(android.R.color.black));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences pref = U.getSharedPreferences(this);
        if (!pref.getBoolean(Constants.PREF_DESKTOP_INPUT, true)) {
            finish();
            return;
        }

        // Set target to external display if available
        int externalDisplayId = U.getExternalDisplayID(this);
        if (externalDisplayId != android.view.Display.DEFAULT_DISPLAY) {
            controller.setTargetDisplayId(externalDisplayId);
        }

        controller.attachTouchpadFullscreen(this, root);
        controller.setGyroEnabled(this, pref.getBoolean(Constants.PREF_DESKTOP_GYRO, false));
    }

    @Override
    protected void onPause() {
        super.onPause();
        controller.setGyroEnabled(this, false);
        controller.detachTouchpad();
    }
}
