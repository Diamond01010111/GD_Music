package com.diamond.gdmusic;

/** Serializes cache invalidation with asynchronous cache writes. */
public final class CacheGeneration {
    private static long generation;
    private CacheGeneration() {}
    public static synchronized long current() { return generation; }
    public static synchronized boolean isCurrent(long expected) { return expected == generation; }
    public static synchronized void invalidate(Runnable clear) {
        generation++;
        clear.run();
    }
    public static synchronized boolean runIfCurrent(long expected, Runnable action) {
        if (expected != generation) return false;
        action.run();
        return true;
    }
}
