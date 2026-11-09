package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import com.sun.jna.platform.win32.WinDef.HWND;

/** Creates foreground scopes without reserving or sending keyboard input. */
@EnabledOnOs(OS.WINDOWS)
class WindowsEventsTest {
    private WindowsEvents testEvents(java.util.function.Consumer<String> status) {
        return new WindowsEvents(status);
    }
    @Test void nativeForegroundHookCanBeRegisteredAndReleasedTwice() throws Exception {
        var errors = new CopyOnWriteArrayList<String>();
        for (int i = 0; i < 2; i++) {
            try (WindowsEvents events = testEvents(errors::add)) {
                events.start();
            }
        }
        assertTrue(errors.isEmpty(), errors.toString());
    }
    @Test void captureRequestsFailAfterMonitorClosesInsteadOfHanging() throws Exception {
        WindowsEvents events = testEvents(s -> {});
        events.start();
        events.close();
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> events.captureForeground().get(1, java.util.concurrent.TimeUnit.SECONDS));
    }
    @Test void monitorTerminationInvalidatesTrackedWindow() throws Exception {
        WindowsEvents events = testEvents(s -> {});
        WindowsEvents.Target target;
        try {
            events.start();
            target = events.track(new HWND());
        } finally { events.close(); }
        assertTrue(target.lost, "Monitor shutdown must permanently block delivery");
    }
}
