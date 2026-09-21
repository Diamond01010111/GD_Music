package com.diamond.gdmusic;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class RequestWindowTest {
    @Test public void boundaryAndRejectedCalls() {
        RequestWindow window = new RequestWindow();
        for (int i = 0; i < 50; i++) assertTrue(window.tryAcquire(1000));
        assertFalse(window.tryAcquire(300999));
        assertEquals(50, window.count(300999));
        assertTrue(window.tryAcquire(301000));
        assertEquals(1, window.count(301000));
    }
    @Test public void concurrentRequestsCannotOverrunBudget() throws Exception {
        RequestWindow window = new RequestWindow();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger accepted = new AtomicInteger();
        try {
            for (int i = 0; i < 200; i++) pool.submit(() -> {
                if (window.tryAcquire(1000)) accepted.incrementAndGet();
            });
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(50, accepted.get());
        } finally { pool.shutdownNow(); }
    }
    @Test public void restoreAndClockRollbackKeepBudget() {
        RequestWindow window = new RequestWindow();
        for (int i = 0; i < 50; i++) window.restore(2000);
        assertFalse(window.tryAcquire(1000));
        assertTrue(window.tryAcquire(301000));
    }
}
