package com.example.g.security;

import android.os.SystemClock;

public final class LockSession {
    public static final long GRACE_MS = 3 * 60 * 1000L;
    private static long unlockedAt;
    private LockSession() { }
    public static synchronized void unlock() { unlockedAt = SystemClock.elapsedRealtime(); }
    public static synchronized boolean isUnlocked() {
        return unlockedAt > 0 && SystemClock.elapsedRealtime() - unlockedAt < GRACE_MS;
    }
    public static synchronized long remainingMillis() {
        return isUnlocked() ? GRACE_MS - (SystemClock.elapsedRealtime() - unlockedAt) : 0;
    }
}
