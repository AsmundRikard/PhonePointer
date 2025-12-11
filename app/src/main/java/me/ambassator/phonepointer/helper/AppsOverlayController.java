package me.ambassator.phonepointer.helper;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.ambassator.phonepointer.R;
import me.ambassator.phonepointer.data.AppAdapter;
import me.ambassator.phonepointer.data.AppInfo;
import me.ambassator.phonepointer.data.AppRepository;
import me.ambassator.phonepointer.util.U;

public class AppsOverlayController {

    private static AppsOverlayController instance;
    private View overlayView;
    private WindowManager windowManager;
    private final AppRepository appRepository = new AppRepository();

    private AppsOverlayController() {
    }

    public static AppsOverlayController getInstance() {
        if (instance == null) {
            instance = new AppsOverlayController();
        }
        return instance;
    }

    public void show(Context context) {
        if (overlayView != null)
            return; // Already showing

        Display externalDisplay = U.getExternalDisplay(context);
        Context displayContext = context;
        int targetDisplayId = Display.DEFAULT_DISPLAY;

        if (externalDisplay != null) {
            displayContext = context.createDisplayContext(externalDisplay);
            targetDisplayId = externalDisplay.getDisplayId();
        }

        // Wrap with theme to ensure styles work
        Context themedContext = new ContextThemeWrapper(displayContext, R.style.Theme_PhonePointer);

        windowManager = (WindowManager) themedContext.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null)
            return;

        LayoutInflater inflater = LayoutInflater.from(themedContext);
        overlayView = inflater.inflate(R.layout.overlay_apps_menu, null);

        RecyclerView recyclerView = overlayView.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(themedContext, 6)); // Grid of 6 cols

        List<AppInfo> apps = appRepository.getInstalledApps(themedContext);
        int finalTargetDisplayId = targetDisplayId;
        AppAdapter adapter = new AppAdapter(apps, app -> {
            // Launch App
            try {
                // Update Recents
                appRepository.updateRecentApp(context, app.packageName);

                // App Launch Logic
                android.content.Intent launchIntent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                launchIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                launchIntent.setComponent(app.componentName);
                launchIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

                android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    options.setLaunchDisplayId(finalTargetDisplayId);
                }

                context.startActivity(launchIntent, options.toBundle());

                hide();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        recyclerView.setAdapter(adapter);

        // Close on outside click is hard with simple view, so let's make the root
        // clickable to close
        overlayView.setOnClickListener(v -> hide());
        // Prevent clicks on card from closing
        overlayView.findViewById(R.id.recycler_view).getParent().requestDisallowInterceptTouchEvent(true);
        // Better: Set card to clickable=true so it consumes event? The layout has card
        // in center.
        View card = overlayView.findViewById(R.id.recycler_view).getParent().getParent() instanceof View
                ? (View) overlayView.findViewById(R.id.recycler_view).getParent()
                : null;
        if (card != null)
            card.setOnClickListener(v -> {
                /* consume */});

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                U.getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // Allow interaction but maybe not steal focus?
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        // We NEED focusable to handle Recycler scroll?
        // Actually FLAG_NOT_FOCUSABLE means no key input. Touch works.
        // If we want back button to close, we need focus.
        // Let's remove NOT_FOCUSABLE.
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        try {
            windowManager.addView(overlayView, params);

            // Ensure cursor is strictly on top by re-attaching it (which moves its window
            // to top of stack)
            me.ambassator.phonepointer.helper.DesktopModeInputController.getInstance().detachCursor();
            me.ambassator.phonepointer.helper.DesktopModeInputController.getInstance().attachCursor(themedContext,
                    windowManager);
        } catch (Exception e) {
            e.printStackTrace();
            overlayView = null;
        }
    }

    public void hide() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            overlayView = null;
            windowManager = null;
        }
    }

    public boolean isShowing() {
        return overlayView != null;
    }
}
