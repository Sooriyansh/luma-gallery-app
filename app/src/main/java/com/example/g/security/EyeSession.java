package com.example.g.security;
import android.content.Context;
import android.os.SystemClock;
public final class EyeSession {
    public static final long DURATION_MS = 30_000L;
    private static long unlockedAt, durationMs = DURATION_MS;
    private EyeSession() { }
    public static boolean isUnlocked() { return unlockedAt > 0 && SystemClock.elapsedRealtime() - unlockedAt < durationMs; }
    public static void unlock() { unlockedAt = SystemClock.elapsedRealtime(); }
    public static void unlock(Context context) { long configured=VaultSettings.autoLockMs(context); durationMs = configured == 0L ? Long.MAX_VALUE / 4 : configured; unlock(); }
    public static void lock() { unlockedAt = 0; }
    public static long remaining() { return isUnlocked() ? durationMs - (SystemClock.elapsedRealtime() - unlockedAt) : 0; }
}
