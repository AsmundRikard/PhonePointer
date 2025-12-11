package me.ambassator.phonepointer.data;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String label;
    public final String packageName;
    public final Drawable icon;
    public final ComponentName componentName;

    public AppInfo(String label, String packageName, Drawable icon, ComponentName componentName) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
        this.componentName = componentName;
    }
}
