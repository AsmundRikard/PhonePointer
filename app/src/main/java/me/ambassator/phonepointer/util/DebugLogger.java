package me.ambassator.phonepointer.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Minimal file logger that works without adb. Writes to app internal storage
 * (phonepointer.log).
 */
public final class DebugLogger {

    private static final String FILE_NAME = "phonepointer.log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static Context appContext;

    private DebugLogger() {
    }

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        write("D", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, msg, t);
        write("E", tag, msg, t);
    }

    public static String getLogPath() {
        if (appContext == null)
            return "unknown";
        return new File(appContext.getFilesDir(), FILE_NAME).getAbsolutePath();
    }

    public static void clearLogs() {
        if (appContext == null)
            return;
        File file = new File(appContext.getFilesDir(), FILE_NAME);
        if (file.exists()) {
            file.delete();
        }
    }

    private static synchronized void write(String level, String tag, String msg, Throwable t) {
        if (appContext == null)
            return;
        File file = new File(appContext.getFilesDir(), FILE_NAME);
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(DATE_FORMAT.format(new Date()));
            fw.write(" ");
            fw.write(level);
            fw.write("/");
            fw.write(tag);
            fw.write(": ");
            fw.write(msg == null ? "" : msg);
            if (t != null) {
                fw.write(" :: ");
                fw.write(Log.getStackTraceString(t));
            }
            fw.write("\n");
        } catch (IOException ignored) {
        }
    }
}
