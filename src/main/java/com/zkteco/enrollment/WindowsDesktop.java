package com.zkteco.enrollment;

import com.sun.jna.platform.win32.User32;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

final class WindowsDesktop implements EnrollmentSession.Desktop {
    private final WindowsEvents.Target target;
    private final Robot robot;

    WindowsDesktop(WindowsEvents.Target target) throws AWTException {
        this.target = target;
        robot = new Robot();
        robot.setAutoDelay(20);
    }
    public boolean targetActive() { return target.active(); }

    public void pasteAndEnter(String text, EnrollmentSession.Guard guard) throws Exception {
        GuardedPaste.deliver(text, guard, new GuardedPaste.Output() {
            public boolean modifiersDown() {
                for (int key : new int[]{0x10, 0x11, 0x12, 0x5B, 0x5C})
                    if ((User32.INSTANCE.GetAsyncKeyState(key) & 0x8000) != 0) return true;
                return false;
            }
            public void pause() throws InterruptedException { Thread.sleep(20); }
            public void clipboard(String value) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
            }
            public void keyPress(int key) { robot.keyPress(key); }
            public void keyRelease(int key) { robot.keyRelease(key); }
            public void settle() throws InterruptedException { Thread.sleep(200); }
        });
    }
}
