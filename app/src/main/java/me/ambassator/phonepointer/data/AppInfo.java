package me.ambassator.phonepointer.data;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String label;
    public final String packageName;
    public final Drawable icon;
    public final ComponentName componentName;
    public int taskId = -1; // Default -1 means not running
    public boolean excluded = false; // Excluded from snap calculations
    public int colorIndex = -1; // Color index for snap preview (-1 = no color)

    public AppInfo(String label, String packageName, Drawable icon, ComponentName componentName) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
        this.componentName = componentName;
    }
}
