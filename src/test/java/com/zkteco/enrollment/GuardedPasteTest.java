package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuardedPasteTest {
    static class Output implements GuardedPaste.Output {
        final List<String> events = new ArrayList<>();
        boolean held;
        Runnable afterPaste = () -> {};
        Runnable afterControl = () -> {};
        public boolean modifiersDown() { return held; }
        public void pause() { events.add("release"); held = false; }
        public void clipboard(String text) { events.add("clipboard:" + text); }
        public void keyPress(int key) {
            events.add("down:" + key);
            if (key == 17) afterControl.run();
            if (key == 86) afterPaste.run();
        }
        public void keyRelease(int key) { events.add("up:" + key); }
        public void settle() {}
    }
    @Test void releasesShortcutBeforePastingAndSendsEnterExactlyOnce() throws Exception {
        Output output = new Output(); output.held = true;
        GuardedPaste.deliver("AQID", () -> {}, output);
        assertEquals(List.of("release", "clipboard:AQID", "down:17", "down:86", "up:86", "up:17", "down:10", "up:10"), output.events);
    }
    @Test void failedGuardNeverChangesClipboardOrSendsKeys() {
        Output output = new Output();
        assertThrows(Exception.class, () -> GuardedPaste.deliver("AQID", () -> { throw new Exception("cancelled"); }, output));
        assertTrue(output.events.isEmpty());
    }
    @Test void windowChangeAfterPastePreventsEnter() {
        Output output = new Output(); boolean[] changed = {false};
        output.afterPaste = () -> changed[0] = true;
        assertThrows(Exception.class, () -> GuardedPaste.deliver("AQID", () -> {
            if (changed[0]) throw new Exception("changed");
        }, output));
        assertEquals(List.of("clipboard:AQID", "down:17", "down:86", "up:86", "up:17"), output.events);
    }
    @Test void clipboardFailureNeverPastesOrSubmits() {
        Output output = new Output() { public void clipboard(String text) { throw new IllegalStateException("busy"); } };
        assertThrows(Exception.class, () -> GuardedPaste.deliver("AQID", () -> {}, output));
        assertTrue(output.events.isEmpty());
    }
    @Test void cancellationOrFocusChangeDuringControlDelayNeverPressesVAndReleasesControl() {
        Output output = new Output(); boolean[] cancelled = {false};
        output.afterControl = () -> cancelled[0] = true;
        assertThrows(Exception.class, () -> GuardedPaste.deliver("AQID", () -> {
            if (cancelled[0]) throw new Exception("cancelled while Ctrl was pressed");
        }, output));
        assertEquals(List.of("clipboard:AQID", "down:17", "up:17"), output.events);
    }
}
