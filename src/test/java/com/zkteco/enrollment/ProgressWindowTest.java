package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.User32;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledOnOs(OS.WINDOWS)
class ProgressWindowTest {
    @Test void compactLayoutKeepsActionsAndPreviewVisibleWithLongInstructions() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        EventQueue.invokeAndWait(() -> {
            ProgressWindow window = new ProgressWindow(() -> {}, () -> {});
            try {
                assertTrue(window.isResizable(), "Small displays must allow adjusting the window");
                window.setSize(360, 400);
                window.addNotify();
                window.validate();
                window.showStatus("Aucun formulaire sélectionné. Recommencez puis cliquez dans le champ du navigateur pendant les 5 secondes. ".repeat(4));
                Rectangle visible = new Rectangle(0, 0, window.getContentPane().getWidth(), window.getContentPane().getHeight());
                for (String command : new String[]{"listen", "quit"}) {
                    JButton action = button(window, command);
                    Rectangle bounds = SwingUtilities.convertRectangle(action.getParent(), action.getBounds(), window.getContentPane());
                    assertTrue(visible.contains(bounds), command + " must remain fully visible");
                    assertTrue(bounds.height >= 30, command + " has bounds " + bounds);
                }
                assertTrue(find(window, FingerprintPreview.class).getHeight() >= 80);
                assertNotNull(SwingUtilities.getAncestorOfClass(JScrollPane.class, find(window, JTextArea.class)),
                        "Long errors must remain readable without pushing actions offscreen");
            } finally { window.dispose(); }
        });
    }

    @Test void displaysLiveProgressWithoutRequestingFocus() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ProgressWindow[] window = new ProgressWindow[1];
        try {
            EventQueue.invokeAndWait(() -> {
                window[0] = new ProgressWindow(() -> {}, () -> {});
                window[0].showStatus("Lecture 2/3 : retirez puis reposez le même doigt.");
                window[0].accepted(1);
                window[0].showFingerprint(FingerprintPreview.image(new byte[]{42, 64}, 2, 1));
                // Default build tests never display windows on the user's desktop.
            });
            EventQueue.invokeAndWait(() -> {
                assertEquals(Window.Type.NORMAL, window[0].getType());
                assertFalse(window[0].isUndecorated());
                assertFalse(window[0].isAutoRequestFocus());
                assertTrue(window[0].isAlwaysOnTop());
                JProgressBar bar = find(window[0], JProgressBar.class);
                assertEquals(1, bar.getValue());
                assertTrue(find(window[0], FingerprintPreview.class).getAccessibleContext()
                        .getAccessibleDescription().contains("Dernière capture"));
                assertTrue(find(window[0], JTextArea.class).getText().contains("Lecture 2/3"));
                window[0].accepted(3);
                window[0].showStatus("Empreinte collée ; Entrée envoyée.");
                assertEquals(3, bar.getValue());
                assertTrue(find(window[0], JTextArea.class).getText().contains("Entrée envoyée"));
            });
        } finally {
            EventQueue.invokeAndWait(() -> { if (window[0] != null) window[0].dispose(); });
        }
    }

    /** Opt-in: requires an interactive desktop without user interaction during the check. */
    @Test
    @EnabledIfSystemProperty(named = "enrollment.desktopTests", matches = "true")
    void progressUpdatesKeepControlledTargetInForeground() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        JFrame[] target = new JFrame[1];
        ProgressWindow[] progress = new ProgressWindow[1];
        HWND previous = User32.INSTANCE.GetForegroundWindow();
        HWND[] targetHandle = new HWND[1], progressHandle = new HWND[1];
        try {
            EventQueue.invokeAndWait(() -> {
                target[0] = new JFrame("Test interactif : fenêtre cible");
                target[0].add(new JTextField("Cible du test de focus — aucune touche envoyée"));
                target[0].setSize(420, 100);
                target[0].setLocationRelativeTo(null);
                target[0].setVisible(true);
                progress[0] = new ProgressWindow(() -> {}, () -> {});
                progress[0].setVisible(true);
                targetHandle[0] = new HWND(Native.getWindowPointer(target[0]));
                progressHandle[0] = new HWND(Native.getWindowPointer(progress[0]));
            });
            // Initial application activation is separate from in-session status updates.
            User32.INSTANCE.SetForegroundWindow(targetHandle[0]);
            assumeTrue(WindowsEvents.sameWindow(targetHandle[0], User32.INSTANCE.GetForegroundWindow()),
                    "Windows did not allow activation of the controlled target");
            EventQueue.invokeAndWait(() -> {
                progress[0].accepted(1);
                progress[0].showFingerprint(FingerprintPreview.image(new byte[]{42}, 1, 1));
                progress[0].showStatus("Lecture 2/3 : retirez puis reposez le même doigt.");
                progress[0].accepted(3);
                progress[0].showStatus("Empreinte collée ; Entrée envoyée.");
                progress[0].clearFingerprint();
            });
            EventQueue.invokeAndWait(() -> {}); // Drain Swing events from the updates.
            HWND after = User32.INSTANCE.GetForegroundWindow();
            assumeTrue(WindowsEvents.sameWindow(after, targetHandle[0])
                            || WindowsEvents.sameWindow(after, progressHandle[0]),
                    "Another application changed the foreground during the interactive test");
            assertTrue(WindowsEvents.sameWindow(targetHandle[0], after),
                    "Progress updates activated the enrollment window instead of preserving the target");
        } finally {
            HWND foreground = User32.INSTANCE.GetForegroundWindow();
            boolean restore = WindowsEvents.sameWindow(foreground, targetHandle[0])
                    || WindowsEvents.sameWindow(foreground, progressHandle[0]);
            EventQueue.invokeAndWait(() -> {
                if (progress[0] != null) progress[0].dispose();
                if (target[0] != null) target[0].dispose();
            });
            if (restore && previous != null) User32.INSTANCE.SetForegroundWindow(previous);
        }
    }

    @Test void titleBarCloseAndQuitButtonBothRequestApplicationShutdown() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        for (boolean titleBar : new boolean[]{true, false}) {
            AtomicInteger requested = new AtomicInteger();
            EventQueue.invokeAndWait(() -> {
                ProgressWindow window = new ProgressWindow(() -> {}, requested::incrementAndGet);
                try {
                    if (titleBar) window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                    else button(window, "quit").doClick();
                    assertEquals(1, requested.get());
                } finally { window.dispose(); }
            });
        }
    }

    @Test void listenButtonStartsOrCancelsAndCanBeDisabledDuringCleanup() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger clicks = new AtomicInteger();
        EventQueue.invokeAndWait(() -> {
            ProgressWindow window = new ProgressWindow(clicks::incrementAndGet, () -> {});
            try {
                JButton listen = button(window, "listen");
                listen.doClick();
                window.listeningAction("Annuler l'écoute", true);
                listen.doClick();
                assertEquals(2, clicks.get());
                window.listeningAction("Annulation…", false);
                listen.doClick();
                assertEquals(2, clicks.get());
                assertTrue(button(window, "quit").isEnabled());
            } finally { window.dispose(); }
        });
    }

    private static JButton button(Container parent, String command) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && command.equals(button.getActionCommand())) return button;
            if (child instanceof Container container) {
                JButton found = button(container, command);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                T match = find(container, type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
