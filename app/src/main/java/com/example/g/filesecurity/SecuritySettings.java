package com.example.g.filesecurity;

import android.content.Context;
import android.content.SharedPreferences;

/** Local, opt-in security preferences and the durable current scan checkpoint. */
public final class SecuritySettings {
    private SecuritySettings() { }
    public static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences("file_security", Context.MODE_PRIVATE);
    }
    public static boolean notificationsEnabled(Context context) { return get(context).getBoolean("notifications", true); }
    public static void setNotificationsEnabled(Context context, boolean enabled) { get(context).edit().putBoolean("notifications", enabled).apply(); }
    public static String status(Context context) { return get(context).getString("status", "IDLE"); }
}
