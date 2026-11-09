package com.zkteco.enrollment;

import java.awt.event.KeyEvent;

/** Keeps focus/cancellation checks next to each irreversible desktop action. */
final class GuardedPaste {
    interface Output {
        boolean modifiersDown();
        void pause() throws InterruptedException;
        void clipboard(String text) throws Exception;
        void keyPress(int key);
        void keyRelease(int key);
        void settle() throws InterruptedException;
    }
    static void deliver(String text, EnrollmentSession.Guard guard, Output output) throws Exception {
        guard.check();
        while (output.modifiersDown()) {
            guard.check();
            output.pause();
        }
        guard.check();
        output.clipboard(text);
        guard.check();
        try {
            output.keyPress(KeyEvent.VK_CONTROL);
            guard.check(); // Robot may wait after Ctrl; the target can change during that delay.
            try { output.keyPress(KeyEvent.VK_V); }
            finally { output.keyRelease(KeyEvent.VK_V); }
        } finally { output.keyRelease(KeyEvent.VK_CONTROL); }
        output.settle();
        guard.check();
        // If the operator presses another modifier while paste is being processed, do not submit.
        if (output.modifiersDown()) throw new Exception("Touches pressées : validation annulée. Vérifiez le formulaire.");
        guard.check();
        try { output.keyPress(KeyEvent.VK_ENTER); }
        finally { output.keyRelease(KeyEvent.VK_ENTER); }
    }
}
