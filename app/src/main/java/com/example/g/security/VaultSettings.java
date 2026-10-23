package com.example.g.security;

import android.content.Context;

/** Non-sensitive vault preferences. The access code is intentionally not stored here. */
public final class VaultSettings {
    private static final String PREF = "vault_settings", SHORTCUT = "show_shortcut", AUTO_LOCK = "auto_lock_ms";
    private VaultSettings() { }
    public static boolean showShortcut(Context c) { return c.getSharedPreferences(PREF, 0).getBoolean(SHORTCUT, false); }
    public static void setShowShortcut(Context c, boolean value) { c.getSharedPreferences(PREF, 0).edit().putBoolean(SHORTCUT, value).apply(); }
    public static long autoLockMs(Context c) { return c.getSharedPreferences(PREF, 0).getLong(AUTO_LOCK, 30_000L); }
    public static void setAutoLockMs(Context c, long value) { c.getSharedPreferences(PREF, 0).edit().putLong(AUTO_LOCK, value).apply(); }
}
