package me.ambassator.phonepointer.helper;

import android.content.ComponentName;
import android.graphics.Rect;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.ambassator.phonepointer.util.DebugLogger;
import rikka.shizuku.Shizuku;

/**
 * Shizuku wrapper for Task management using shell commands.
 * Based on DroidOS approach - uses dumpsys and am commands for reliable task
 * handling.
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
        public String packageName;
        public ComponentName baseActivity;
        public ComponentName topActivity;

        public RunningTask(int id, String packageName) {
            this.id = id;
            this.packageName = packageName;
            this.baseActivity = null;
            this.topActivity = null;
        }

        public RunningTask(int id, ComponentName baseActivity, ComponentName topActivity) {
            this.id = id;
            this.baseActivity = baseActivity;
            this.topActivity = topActivity;
            this.packageName = baseActivity != null ? baseActivity.getPackageName() : null;
        }
    }

    /**
     * Get visible packages on a specific display using shell command.
     * Based on DroidOS ShellUserService.getVisiblePackages()
     */
    public static List<String> getVisiblePackages(int displayId) {
        List<String> packages = new ArrayList<>();
        DebugLogger.d(TAG, "getVisiblePackages called for displayId=" + displayId);

        // Packages to exclude (system UI, launchers, our app, etc.)
        Set<String> excludedPackages = new HashSet<>();
        excludedPackages.add("com.android.systemui");
        excludedPackages.add("com.google.android.apps.nexuslauncher");
        excludedPackages.add("com.google.android.googlequicksearchbox");
        excludedPackages.add("com.google.android.apps.walletnfcrel");
        excludedPackages.add("me.ambassator.phonepointer");
        excludedPackages.add("com.android.launcher3");
        excludedPackages.add("com.android.launcher");

        if (!Shizuku.pingBinder()) {
            DebugLogger.d(TAG, "Shizuku not available for getVisiblePackages");
            return packages;
        }

        try {
            Process process = Shizuku.newProcess(
                    new String[] { "sh", "-c", "dumpsys activity activities" },
                    null, null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            int currentScanningDisplayId = -1;
            // Look for "Display #X" pattern - may have variations
            Pattern displayPattern = Pattern.compile("Display\\s*#?(\\d+)");
            Pattern recordPattern = Pattern.compile("u\\d+\\s+([a-zA-Z0-9_.]+)/");

            while ((line = reader.readLine()) != null) {
                String l = line.trim();

                // Track which display we're scanning - check for various display line formats
                if (l.contains("Display") && l.contains("#")) {
                    Matcher displayMatch = displayPattern.matcher(l);
                    if (displayMatch.find()) {
                        currentScanningDisplayId = Integer.parseInt(displayMatch.group(1));
                        DebugLogger.d(TAG, "Found display section: " + currentScanningDisplayId);
                    }
                    continue;
                }

                // Parse Task lines on target display - they have visible=true/false and
                // A=userid:package
                // Example: Task{... A=10447:me.ambassator.betterassistant ... visible=true ...}
                if (currentScanningDisplayId == displayId && l.contains("Task{") && l.contains("visible=")) {
                    // Extract package from A=userid:packagename pattern
                    java.util.regex.Matcher pkgMatcher = Pattern.compile("A=\\d+:([a-zA-Z0-9_.]+)").matcher(l);
                    if (pkgMatcher.find()) {
                        String pkg = pkgMatcher.group(1);
                        boolean isVisible = l.contains("visible=true");

                        if (isVisible && pkg != null && !packages.contains(pkg) && !excludedPackages.contains(pkg)) {
                            packages.add(pkg);
                            DebugLogger.d(TAG, "Found visible Task on display " + displayId + ": " + pkg);
                        } else if (!isVisible) {
                            DebugLogger.d(TAG, "Skipped non-visible Task: " + pkg + " visible=false");
                        }
                    }
                }
            }

            reader.close();
            process.waitFor();
            DebugLogger.d(TAG, "getVisiblePackages display=" + displayId + " found " + packages.size() + " packages");

        } catch (Exception e) {
            DebugLogger.e(TAG, "getVisiblePackages failed", e);
        }

        return packages;
    }

    /**
     * Get all running packages (across all displays) using shell command.
     * Based on DroidOS ShellUserService.getAllRunningPackages()
     */
    public static List<String> getAllRunningPackages() {
        Set<String> packages = new HashSet<>();
        DebugLogger.d(TAG, "getAllRunningPackages called, pingBinder=" + Shizuku.pingBinder());

        if (!Shizuku.pingBinder()) {
            DebugLogger.d(TAG, "Shizuku not available for getAllRunningPackages");
            return new ArrayList<>(packages);
        }

        try {
            Process process = Shizuku.newProcess(
                    new String[] { "sh", "-c", "dumpsys activity activities" },
                    null, null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            int lineCount = 0;
            Pattern recordPattern = Pattern.compile("ActivityRecord\\{[0-9a-f]+ u\\d+ ([a-zA-Z0-9_.]+)/");

            while ((line = reader.readLine()) != null) {
                lineCount++;
                String l = line.trim();
                if (l.contains("ActivityRecord{")) {
                    DebugLogger.d(TAG, "Found ActivityRecord: " + l.substring(0, Math.min(100, l.length())));
                    Matcher matcher = recordPattern.matcher(l);
                    if (matcher.find()) {
                        String pkg = matcher.group(1);
                        if (pkg != null) {
                            packages.add(pkg);
                            DebugLogger.d(TAG, "Extracted package: " + pkg);
                        }
                    }
                }
            }

            reader.close();
            int exitCode = process.waitFor();
            DebugLogger.d(TAG, "getAllRunningPackages: read " + lineCount + " lines, exit=" + exitCode + ", found "
                    + packages.size());

        } catch (Exception e) {
            DebugLogger.e(TAG, "getAllRunningPackages failed", e);
        }

        return new ArrayList<>(packages);
    }

    /**
     * Get task ID for a package using dumpsys activity activities (more reliable
     * than top).
     */
    public static int getTaskId(String packageName) {
        DebugLogger.d(TAG, "getTaskId called for: " + packageName);
        if (!Shizuku.pingBinder()) {
            return -1;
        }

        try {
            // Use dumpsys activity activities instead of top, and look for Task lines
            Process process = Shizuku.newProcess(
                    new String[] { "sh", "-c", "dumpsys activity activities" },
                    null, null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            int lastTaskId = -1;
            Pattern taskPattern = Pattern.compile("Task\\{[^}]+ #(\\d+)");
            Pattern taskPattern2 = Pattern.compile("taskId=(\\d+)");

            while ((line = reader.readLine()) != null) {
                String l = line.trim();

                // Look for Task lines
                if (l.contains("Task{") || l.contains("taskId=")) {
                    Matcher match1 = taskPattern.matcher(l);
                    Matcher match2 = taskPattern2.matcher(l);
                    if (match1.find()) {
                        lastTaskId = Integer.parseInt(match1.group(1));
                    } else if (match2.find()) {
                        lastTaskId = Integer.parseInt(match2.group(1));
                    }
                }

                // When we hit an ActivityRecord with our package, return the last taskId
                if (l.contains("ActivityRecord{") && l.contains(packageName) && lastTaskId != -1) {
                    reader.close();
                    process.waitFor();
                    DebugLogger.d(TAG, "getTaskId " + packageName + " = " + lastTaskId);
                    return lastTaskId;
                }
            }

            reader.close();
            process.waitFor();
            DebugLogger.d(TAG, "getTaskId: no task found for " + packageName);

        } catch (Exception e) {
            DebugLogger.e(TAG, "getTaskId failed for " + packageName, e);
        }

        return -1;
    }

    /**
     * Get recent/running tasks with their IDs.
     * Uses shell commands for reliable detection on all Android versions.
     */
    public static List<RunningTask> getRecentTasks(int max) {
        List<RunningTask> tasks = new ArrayList<>();
        if (!Shizuku.pingBinder()) {
            DebugLogger.d(TAG, "Shizuku not available for getRecentTasks");
            return tasks;
        }

        try {
            // Get all running packages first
            List<String> packages = getAllRunningPackages();

            // For each package, get its task ID
            int count = 0;
            for (String pkg : packages) {
                if (count >= max)
                    break;

                int taskId = getTaskId(pkg);
                if (taskId != -1) {
                    tasks.add(new RunningTask(taskId, pkg));
                    count++;
                    DebugLogger.d(TAG, "getRecentTasks: id=" + taskId + " pkg=" + pkg);
                }
            }

        } catch (Exception e) {
            DebugLogger.e(TAG, "getRecentTasks failed", e);
        }

        DebugLogger.d(TAG, "getRecentTasks returning " + tasks.size() + " tasks");
        return tasks;
    }

    /**
     * Resize a task using shell commands.
     * Based on DroidOS ShellUserService.repositionTask()
     */
    public static void resizeTask(int taskId, Rect bounds) {
        if (!Shizuku.pingBinder()) {
            DebugLogger.d(TAG, "Shizuku not available for resizeTask");
            return;
        }

        try {
            // First, set the task to freeform windowing mode
            String setModeCmd = "am task set-windowing-mode " + taskId + " " + WINDOWING_MODE_FREEFORM;
            Process p1 = Shizuku.newProcess(new String[] { "sh", "-c", setModeCmd }, null, null);
            p1.waitFor();
            DebugLogger.d(TAG, "Set windowing mode: " + setModeCmd);

            // Then resize the task
            String resizeCmd = "am task resize " + taskId + " " +
                    bounds.left + " " + bounds.top + " " + bounds.right + " " + bounds.bottom;
            Process p2 = Shizuku.newProcess(new String[] { "sh", "-c", resizeCmd }, null, null);
            p2.waitFor();
            DebugLogger.d(TAG, "Resized task: " + resizeCmd);

        } catch (Exception e) {
            DebugLogger.e(TAG, "resizeTask failed for taskId=" + taskId, e);
        }
    }

    /**
     * Resize a task by package name.
     */
    public static void resizeTaskByPackage(String packageName, Rect bounds) {
        int taskId = getTaskId(packageName);
        if (taskId != -1) {
            resizeTask(taskId, bounds);
        } else {
            DebugLogger.d(TAG, "Could not find taskId for " + packageName);
        }
    }

    /**
     * Set task windowing mode using shell command.
     */
    public static void setTaskWindowingMode(int taskId, int mode) {
        if (!Shizuku.pingBinder()) {
            return;
        }

        try {
            String cmd = "am task set-windowing-mode " + taskId + " " + mode;
            Process p = Shizuku.newProcess(new String[] { "sh", "-c", cmd }, null, null);
            p.waitFor();
            DebugLogger.d(TAG, "setTaskWindowingMode: " + cmd);
        } catch (Exception e) {
            DebugLogger.e(TAG, "setTaskWindowingMode failed", e);
        }
    }

    /**
     * Move task to front using shell command.
     */
    public static void moveTaskToFront(int taskId) {
        if (!Shizuku.pingBinder()) {
            return;
        }

        try {
            // am start --activity-task-on-home will bring it to front
            // or use am task lock <taskId> and unlock
            String cmd = "am task focus " + taskId;
            Process p = Shizuku.newProcess(new String[] { "sh", "-c", cmd }, null, null);
            p.waitFor();
            DebugLogger.d(TAG, "moveTaskToFront: " + cmd);
        } catch (Exception e) {
            DebugLogger.e(TAG, "moveTaskToFront failed", e);
        }
    }

    /**
     * Force stop an app using shell command.
     */
    public static void forceStopApp(String packageName) {
        if (!Shizuku.pingBinder()) {
            return;
        }

        try {
            String cmd = "am force-stop " + packageName;
            Process p = Shizuku.newProcess(new String[] { "sh", "-c", cmd }, null, null);
            p.waitFor();
            DebugLogger.d(TAG, "forceStopApp: " + cmd);
        } catch (Exception e) {
            DebugLogger.e(TAG, "forceStopApp failed", e);
        }
    }
}
