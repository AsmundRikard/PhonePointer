package me.ambassator.phonepointer.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.ambassator.phonepointer.util.U;

public class AppRepository {

    private static final String PREF_RECENT_APPS = "recent_apps_usage";

    public List<AppInfo> getInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        List<AppInfo> appList = new ArrayList<>();

        for (ResolveInfo ri : apps) {
            String label = ri.loadLabel(pm).toString();
            String packageName = ri.activityInfo.packageName;
            Drawable icon = ri.loadIcon(pm);
            ComponentName componentName = new ComponentName(packageName, ri.activityInfo.name);
            appList.add(new AppInfo(label, packageName, icon, componentName));
        }

        // Sort by recents, then alphabetically
        Map<String, Long> recents = getRecentAppUsage(context);
        appList.sort((a, b) -> {
            long usageA = recents.getOrDefault(a.packageName, 0L);
            long usageB = recents.getOrDefault(b.packageName, 0L);
            if (usageA != usageB) {
                return Long.compare(usageB, usageA); // Descending usage
            }
            return a.label.compareToIgnoreCase(b.label);
        });

        return appList;
    }

    private Map<String, Long> getRecentAppUsage(Context context) {
        SharedPreferences prefs = U.getSharedPreferences(context);
        String data = prefs.getString(PREF_RECENT_APPS, "");
        Map<String, Long> map = new HashMap<>();
        if (data.isEmpty())
            return map;

        String[] entries = data.split(";");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    map.put(parts[0], Long.parseLong(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }

    public void updateRecentApp(Context context, String packageName) {
        Map<String, Long> recents = getRecentAppUsage(context);
        recents.put(packageName, System.currentTimeMillis());

        // Save back
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : recents.entrySet()) {
            if (sb.length() > 0)
                sb.append(";");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }

        U.getSharedPreferences(context).edit()
                .putString(PREF_RECENT_APPS, sb.toString())
                .apply();
    }
}
