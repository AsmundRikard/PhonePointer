package me.ambassator.phonepointer.helper;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

import me.ambassator.phonepointer.util.DebugLogger;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Shizuku wrapper for Task management (IActivityManager/IActivityTaskManager).
 */
public class SystemTaskHelper {

    private static final String TAG = "SystemTaskHelper";

    // Windowing modes (from android.app.WindowConfiguration)
    public static final int WINDOWING_MODE_UNDEFINED = 0;
    public static final int WINDOWING_MODE_FULLSCREEN = 1;
    public static final int WINDOWING_MODE_PINNED = 2;
    public static final int WINDOWING_MODE_SPLIT_SCREEN_PRIMARY = 3;
    public static final int WINDOWING_MODE_SPLIT_SCREEN_SECONDARY = 4;
    public static final int WINDOWING_MODE_FREEFORM = 5;
    public static final int WINDOWING_MODE_MULTI_WINDOW = 6;

    public static class RunningTask {
        public int id;
        public ComponentName baseActivity;
        public ComponentName topActivity;

        public RunningTask(int id, ComponentName baseActivity, ComponentName topActivity) {
            this.id = id;
            this.baseActivity = baseActivity;
            this.topActivity = topActivity;
        }
    }

    public static List<RunningTask> getRecentTasks(int max) {
        if (!Shizuku.pingBinder())
            return new ArrayList<>();

        List<RunningTask> tasks = new ArrayList<>();
        try {
            // Get IActivityManager (before Android 10/Q) or IActivityTaskManager (Q+)?
            // "activity" service is IActivityManager
            IBinder amBinder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"));

            // We use reflection to call getRecentTasks on IActivityManager
            Object taskList = null;

            // Reflection setup
            Class<?> iamClass = Class.forName("android.app.IActivityManager");
            Class<?> iamStub = Class.forName("android.app.IActivityManager$Stub");
            Object iam = iamStub.getMethod("asInterface", IBinder.class).invoke(null, amBinder);

            // getRecentTasks(int maxNum, int flags, int userId)
            // RECENT_IGNORE_UNAVAILABLE = 2
            taskList = iamClass.getMethod("getRecentTasks", int.class, int.class, int.class)
                    .invoke(iam, max, 2, 0);

            // Parse list (List<ActivityManager.RecentTaskInfo>) or ParcellableListSlice
            if (taskList instanceof List) {
                List<?> list = (List<?>) taskList;
                for (Object item : list) {
                    // Get fields manually
                    // RecentTaskInfo extends TaskInfo
                    int id = (int) item.getClass().getField("id").get(item); // or taskId in newer
                    if (id == -1) {
                        // Fallback for newer android versions where id might be taskId
                        try {
                            id = (int) item.getClass().getField("taskId").get(item);
                        } catch (Exception ignored) {
                        }
                    }

                    ComponentName baseActivity = (ComponentName) item.getClass().getField("baseActivity").get(item);
                    ComponentName topActivity = (ComponentName) item.getClass().getField("topActivity").get(item);

                    if (id != -1 && baseActivity != null) {
                        tasks.add(new RunningTask(id, baseActivity, topActivity));
                    }
                }
                // If getting RecentTaskInfo failed or returned ParcellableListSlice on newer
                // Androids
            } else if (taskList != null && taskList.getClass().getName().contains("ParcedListSlice")) {
                List<?> list = (List<?>) taskList.getClass().getMethod("getList").invoke(taskList);
                for (Object item : list) {
                    int id = -1;
                    try {
                        id = item.getClass().getField("taskId").getInt(item);
                    } catch (Exception e) {
                        id = item.getClass().getField("id").getInt(item);
                    }

                    ComponentName baseActivity = (ComponentName) item.getClass().getField("baseActivity").get(item);
                    ComponentName topActivity = (ComponentName) item.getClass().getField("topActivity").get(item);
                    if (id != -1)
                        tasks.add(new RunningTask(id, baseActivity, topActivity));
                }
            }

        } catch (Exception e) {
            DebugLogger.e(TAG, "getRecentTasks failed", e);
        }
        return tasks;
    }

    public static void resizeTask(int taskId, Rect bounds) {
        if (!Shizuku.pingBinder())
            return;
        try {
            // Android 10+ uses IActivityTaskManager for resizing
            // "activity_task" service
            String serviceName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? "activity_task" : "activity";
            IBinder binder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(serviceName));

            String interfaceName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? "android.app.IActivityTaskManager"
                    : "android.app.IActivityManager";

            Class<?> stubClass = Class.forName(interfaceName + "$Stub");
            Object interfaceObj = stubClass.getMethod("asInterface", IBinder.class).invoke(null, binder);

            Class<?> interfaceClass = Class.forName(interfaceName);

            // resizeTask(int taskId, Rect bounds, int resizeMode)
            // resizeMode: RESIZE_MODE_SYSTEM_SCREEN_ROTATION = 1 (or 0 for user?)
            // Let's try just bounds or bounds + mode

            try {
                interfaceClass.getMethod("resizeTask", int.class, Rect.class, int.class)
                        .invoke(interfaceObj, taskId, bounds, 0);
            } catch (NoSuchMethodException e) {
                // Fallback: older signature might be just resizeTask(taskId, bounds)
                interfaceClass.getMethod("resizeTask", int.class, Rect.class)
                        .invoke(interfaceObj, taskId, bounds);
            }

            DebugLogger.d(TAG, "Available to resize task " + taskId + " to " + bounds);
        } catch (Exception e) {
            DebugLogger.e(TAG, "resizeTask failed", e);
        }
    }

    public static void setTaskWindowingMode(int taskId, int mode) {
        if (!Shizuku.pingBinder())
            return;
        try {
            String serviceName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? "activity_task" : "activity";
            IBinder binder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(serviceName));

            String interfaceName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? "android.app.IActivityTaskManager"
                    : "android.app.IActivityManager";

            Class<?> stubClass = Class.forName(interfaceName + "$Stub");
            Object interfaceObj = stubClass.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> interfaceClass = Class.forName(interfaceName);

            // setTaskWindowingMode(int taskId, int windowingMode, boolean toTop)
            interfaceClass.getMethod("setTaskWindowingMode", int.class, int.class, boolean.class)
                    .invoke(interfaceObj, taskId, mode, true);

        } catch (Exception e) {
            // Not critical, might fail on some versions
            DebugLogger.e(TAG, "setTaskWindowingMode failed", e);
        }
    }

    public static void moveTaskToFront(int taskId) {
        if (!Shizuku.pingBinder())
            return;
        try {
            // "activity" service usually handles moveToFront, or IActivityTaskManager
            String serviceName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? "activity_task" : "activity";
            IBinder binder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(serviceName));
            String interfaceName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? "android.app.IActivityTaskManager"
                    : "android.app.IActivityManager";

            Class<?> stubClass = Class.forName(interfaceName + "$Stub");
            Object interfaceObj = stubClass.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> interfaceClass = Class.forName(interfaceName);

            // moveToFrontActivity(IBinder token, int flags) ?? No, that's activity.
            // moveTaskToFront(int task, int flags, Bundle options) wrapper?
            // Usually it's startActivityFromRecents(int taskId, Bundle options) in ATM.

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                interfaceClass.getMethod("startActivityFromRecents", int.class, android.os.Bundle.class)
                        .invoke(interfaceObj, taskId, null);
            } else {
                interfaceClass.getMethod("moveTaskToFront", int.class, int.class, android.os.Bundle.class)
                        .invoke(interfaceObj, taskId, 0, null);
            }

        } catch (Exception e) {
            DebugLogger.e(TAG, "moveTaskToFront failed", e);
        }
    }
}
