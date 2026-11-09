package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureCycleTest {
    @Test void poorExtractionAndBusyReaderCanRecoverWithoutRestartingEnrollment() throws Exception {
        CaptureCycle cycle = new CaptureCycle();
        assertNull(cycle.accept(-9, new byte[2048], 2048));
        assertNull(cycle.accept(-12, new byte[2048], 2048));
        assertArrayEquals(new byte[]{1, 2}, cycle.accept(0, new byte[]{1, 2, 99}, 2));
    }
    @Test void transientErrorDoesNotCountAsFingerRemoval() throws Exception {
        CaptureCycle cycle = new CaptureCycle();
        assertNotNull(cycle.accept(0, new byte[]{1}, 1));
        assertNull(cycle.accept(-9, new byte[2048], 2048));
        assertNull(cycle.accept(-12, new byte[2048], 2048));
        assertNull(cycle.accept(0, new byte[]{2}, 1));
        assertNull(cycle.accept(-8, new byte[2048], 2048));
        assertArrayEquals(new byte[]{3}, cycle.accept(0, new byte[]{3}, 1));
    }
    @Test void fatalSdkErrorsStillStopTheCapture() {
        CaptureCycle cycle = new CaptureCycle();
        assertThrows(Exception.class, () -> cycle.accept(-7, new byte[2048], 2048));
        assertThrows(Exception.class, () -> cycle.accept(-3, new byte[2048], 2048));
    }
}
