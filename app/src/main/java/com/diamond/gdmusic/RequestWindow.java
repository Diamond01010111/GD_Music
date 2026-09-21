package com.diamond.gdmusic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Rolling window shared by every GD API operation, including automatic fallback. */
public final class RequestWindow {
    private static final long WINDOW_MS = 300_000L;
    private final ArrayDeque<Long> timestamps = new ArrayDeque<>();
    public synchronized void restore(long timestamp) { timestamps.addLast(timestamp); }
    public synchronized boolean tryAcquire(long now) {
        trim(now);
        if (timestamps.size() >= 50) return false;
        timestamps.addLast(now);
        return true;
    }
    public synchronized int count(long now) { trim(now); return timestamps.size(); }
    public synchronized List<Long> snapshot() { return new ArrayList<>(timestamps); }
    private void trim(long now) {
        // Clamp future timestamps after a clock rollback instead of resetting the allowance.
        ArrayDeque<Long> remaining = new ArrayDeque<>();
        for (long timestamp : timestamps) {
            if (now - timestamp < WINDOW_MS) remaining.addLast(Math.min(now, timestamp));
        }
        timestamps.clear();
        timestamps.addAll(remaining);
    }
}
