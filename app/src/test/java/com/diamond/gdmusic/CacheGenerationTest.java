package com.diamond.gdmusic;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class CacheGenerationTest {
    @Test public void lateResponseCannotRepopulateClearedCache() {
        Map<String, String> cache = new HashMap<>();
        long request = CacheGeneration.current();
        cache.put("old", "value");
        CacheGeneration.invalidate(cache::clear);
        assertFalse(CacheGeneration.runIfCurrent(request, () -> cache.put("late", "value")));
        assertTrue(cache.isEmpty());
        assertTrue(CacheGeneration.runIfCurrent(CacheGeneration.current(),
                () -> cache.put("fresh", "value")));
        assertEquals("value", cache.get("fresh"));
    }

    @Test public void clearWaitsForAlreadyCommittingResponseThenRemovesIt() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Map<String, String> cache = new HashMap<>();
        long request = CacheGeneration.current();
        try {
            Future<?> writer = workers.submit(() -> CacheGeneration.runIfCurrent(request, () -> {
                writing.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out");
                } catch (InterruptedException error) {
                    throw new AssertionError(error);
                }
                cache.put("response", "value");
            }));
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            Future<?> clear = workers.submit(() -> CacheGeneration.invalidate(cache::clear));
            release.countDown();
            writer.get(5, TimeUnit.SECONDS);
            clear.get(5, TimeUnit.SECONDS);
            assertTrue(cache.isEmpty());
            assertFalse(CacheGeneration.isCurrent(request));
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test public void cleanupFailureStillInvalidatesEarlierRequests() {
        long request = CacheGeneration.current();
        try {
            CacheGeneration.invalidate(() -> { throw new IllegalStateException("disk failure"); });
            fail("Expected failure");
        } catch (IllegalStateException expected) {
            assertFalse(CacheGeneration.isCurrent(request));
        }
    }
}
