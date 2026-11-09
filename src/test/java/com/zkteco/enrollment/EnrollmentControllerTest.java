package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class EnrollmentControllerTest {
    @Test void explicitCancelWaitsForCleanupBeforeSignallingButtonReady() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicInteger finished = new AtomicInteger();
        EnrollmentController controller = new EnrollmentController(worker, s -> {}, finished::incrementAndGet);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<AtomicBoolean> token = new AtomicReference<>();
        try {
            controller.toggle(cancelled -> { token.set(cancelled); entered.countDown(); release.await(); });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            controller.cancel();
            assertTrue(token.get().get());
            assertEquals(0, finished.get());
            release.countDown();
            worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(1, finished.get());
        } finally { release.countDown(); controller.close(); }
    }
    @Test void repeatedTriggersCancelWithoutStartingAnotherReaderAndNextAttemptWorks() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        EnrollmentController controller = new EnrollmentController(worker, s -> {});
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<AtomicBoolean> token = new AtomicReference<>();
        AtomicInteger sessions = new AtomicInteger();
        try {
            controller.toggle(cancelled -> {
                token.set(cancelled); sessions.incrementAndGet(); entered.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS));
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            controller.toggle(cancelled -> sessions.incrementAndGet());
            controller.toggle(cancelled -> sessions.incrementAndGet());
            assertTrue(token.get().get());
            assertEquals(1, sessions.get());
            release.countDown();
            worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
            controller.toggle(cancelled -> sessions.incrementAndGet());
            worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(2, sessions.get());
        } finally { release.countDown(); controller.close(); }
        assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
    }
    @Test void closingCancelsSessionAndRejectsFurtherStarts() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        EnrollmentController controller = new EnrollmentController(worker, s -> {});
        AtomicReference<AtomicBoolean> token = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        controller.toggle(cancelled -> { token.set(cancelled); entered.countDown(); release.await(); });
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            controller.close();
            assertTrue(token.get().get());
            controller.toggle(cancelled -> fail("No session after close"));
        } finally { release.countDown(); controller.close(); }
        assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
    }
}
