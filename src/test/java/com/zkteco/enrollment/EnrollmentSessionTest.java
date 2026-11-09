package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class EnrollmentSessionTest {
    // A removed guard or use of the full merge buffer must break these tests.
    static class Reader implements EnrollmentSession.Reader {
        int captures, closed, merges;
        boolean mismatch, fail, mergeFail;
        Runnable onCapture = () -> {};
        public void open() {}
        public byte[] capture() throws Exception {
            onCapture.run();
            if (fail) throw new Exception("Lecteur débranché");
            captures++;
            return new byte[]{(byte) captures};
        }
        public boolean matches(byte[] a, byte[] b) { return !mismatch; }
        public byte[] merge(byte[][] samples) throws Exception {
            merges++;
            if (mergeFail) throw new Exception("Fusion impossible");
            assertEquals(3, samples.length);
            return new byte[]{1, 2, 3};
        }
        public void close() { closed++; }
    }
    static class Desktop implements EnrollmentSession.Desktop {
        boolean focused = true;
        final List<String> output = new ArrayList<>();
        public boolean targetActive() { return focused; }
        public void pasteAndEnter(String value, EnrollmentSession.Guard guard) throws Exception {
            guard.check();
            output.add(value);
        }
    }
    static class Clock implements EnrollmentSession.Clock {
        long time;
        public long nanoTime() { return time; }
        public void pause() { time += 100_000_000L; }
    }
    final Reader reader = new Reader();
    final Desktop desktop = new Desktop();
    final Clock clock = new Clock();
    final AtomicBoolean cancelled = new AtomicBoolean();
    void run() throws Exception {
        new EnrollmentSession(reader, desktop, clock, s -> {}).run(cancelled::get);
    }
    @Test void threeSamplesProduceExactlyOneBase64InsertionAndReleaseReader() throws Exception {
        run();
        assertEquals(List.of("AQID"), desktop.output);
        assertEquals(3, reader.captures);
        assertEquals(1, reader.merges);
        assertEquals(1, reader.closed);
    }
    @Test void reportsAcceptedSampleCountIndependentlyFromMessages() throws Exception {
        List<Integer> counts = new ArrayList<>();
        new EnrollmentSession(reader, desktop, clock, s -> {}, counts::add).run(cancelled::get);
        assertEquals(List.of(0, 1, 2, 3), counts);
    }
    @Test void rejectedSampleDoesNotAdvanceVisibleProgress() {
        List<Integer> counts = new ArrayList<>();
        reader.mismatch = true;
        assertThrows(Exception.class, () -> new EnrollmentSession(reader, desktop, clock, s -> {}, counts::add).run(cancelled::get));
        assertEquals(List.of(0, 1), counts);
        assertTrue(desktop.output.isEmpty());
    }
    @Test void differentFingerDoesNotMergeOrInsert() {
        reader.mismatch = true;
        assertThrows(Exception.class, this::run);
        assertEquals(0, reader.merges);
        assertTrue(desktop.output.isEmpty());
        assertEquals(1, reader.closed);
    }
    @Test void cancellationDuringCapturePreventsInsertion() {
        reader.onCapture = () -> cancelled.set(true);
        assertThrows(Exception.class, this::run);
        assertTrue(desktop.output.isEmpty());
        assertEquals(1, reader.closed);
    }
    @Test void timeoutIncludesTimeSpentInsideNativeCapture() {
        reader.onCapture = () -> clock.time = 60_000_000_000L;
        assertThrows(Exception.class, this::run);
        assertTrue(desktop.output.isEmpty());
    }
    @Test void windowChangeEvenIfRestoredCancelsSession() {
        reader.onCapture = () -> desktop.focused = false;
        assertThrows(Exception.class, this::run);
        desktop.focused = true;
        assertTrue(desktop.output.isEmpty());
    }
    @Test void sdkErrorAndMergeErrorNeverInsertAndAlwaysClose() {
        reader.fail = true;
        assertThrows(Exception.class, this::run);
        reader.fail = false;
        reader.mergeFail = true;
        assertThrows(Exception.class, this::run);
        assertTrue(desktop.output.isEmpty());
        assertEquals(2, reader.closed);
    }
    @Test void nextSessionCanSucceedAfterReaderError() throws Exception {
        reader.fail = true;
        assertThrows(Exception.class, this::run);
        reader.fail = false;
        run();
        assertEquals(List.of("AQID"), desktop.output);
    }
    @Test void noFingerWaitsUntilTimeoutWithoutInsertion() {
        Reader absent = new Reader() { public byte[] capture() { return null; } };
        assertThrows(Exception.class, () -> new EnrollmentSession(absent, desktop, clock, s -> {}).run(() -> false));
        assertTrue(clock.time >= 60_000_000_000L);
        assertTrue(desktop.output.isEmpty());
        assertEquals(1, absent.closed);
    }

    @Test void sampleReturnedDuringCancellationTimeoutOrFocusLossIsStillErased() {
        for (int reason = 0; reason < 3; reason++) {
            byte[] returned = {42, 43, 44};
            AtomicBoolean stop = new AtomicBoolean();
            Clock localClock = new Clock();
            Desktop localDesktop = new Desktop();
            int failure = reason;
            Reader interrupted = new Reader() {
                public byte[] capture() {
                    if (failure == 0) stop.set(true);
                    if (failure == 1) localClock.time = 60_000_000_000L;
                    if (failure == 2) localDesktop.focused = false;
                    return returned;
                }
            };
            assertThrows(Exception.class, () -> new EnrollmentSession(
                    interrupted, localDesktop, localClock, s -> {}).run(stop::get));
            assertArrayEquals(new byte[3], returned, "Sample must be erased even when the post-capture guard fails");
            assertTrue(localDesktop.output.isEmpty());
            assertEquals(1, interrupted.closed);
        }
    }
}
