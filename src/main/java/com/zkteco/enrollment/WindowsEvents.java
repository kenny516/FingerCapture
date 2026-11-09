package com.zkteco.enrollment;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import java.util.concurrent.*;
import java.util.Queue;
import java.util.function.Consumer;

/** Foreground tracking and scope creation share the Windows message thread. */
final class WindowsEvents implements AutoCloseable {
    private static final int FOREGROUND_EVENT = 3, CAPTURE_TARGET_MESSAGE = 0x8001;
    private final Consumer<String> status;
    private final Queue<CompletableFuture<Target>> pendingTargets = new ConcurrentLinkedQueue<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile int threadId;
    private volatile boolean stopping;
    private volatile Target target;
    private Thread thread;
    // Strong reference required until UnhookWinEvent has completed.
    private final WinUser.WinEventProc foregroundHook = (hook, event, window, object, child, tid, time) -> {
        Target current = target;
        if (current != null && !sameWindow(current.window, window)) current.lost = true;
    };

    WindowsEvents(Consumer<String> status) {
        this.status = status;
    }

    void start() throws Exception {
        thread = new Thread(this::messageLoop, "windows-foreground");
        thread.start();
        try { ready.get(5, TimeUnit.SECONDS); }
        catch (Exception e) { close(); throw e; }
    }

    private void messageLoop() {
        User32 user = User32.INSTANCE;
        HANDLE hook = null;
        try {
            threadId = Kernel32.INSTANCE.GetCurrentThreadId();
            WinUser.MSG message = new WinUser.MSG();
            user.PeekMessage(message, null, 0, 0, 0); // Create the message queue before publishing readiness.
            hook = user.SetWinEventHook(FOREGROUND_EVENT, FOREGROUND_EVENT, null, foregroundHook, 0, 0, 0);
            if (hook == null) throw new IllegalStateException("Impossible de surveiller la fenêtre active.");
            ready.complete(null);
            if (stopping) return;
            int result;
            while ((result = user.GetMessage(message, null, 0, 0)) > 0) {
                if (message.message == CAPTURE_TARGET_MESSAGE) {
                    CompletableFuture<Target> pending = pendingTargets.poll();
                    if (pending != null) {
                        HWND window = user.GetForegroundWindow();
                        if (window == null) pending.completeExceptionally(new IllegalStateException("Sélectionnez le champ du navigateur puis recommencez."));
                        else pending.complete(track(window));
                    }
                } else {
                    user.TranslateMessage(message);
                    user.DispatchMessage(message);
                }
            }
            if (result == -1) throw new IllegalStateException("Erreur de la boucle de messages Windows.");
        } catch (Throwable e) {
            ready.completeExceptionally(e);
            status.accept(e.getMessage() == null ? "Fonctions Windows indisponibles." : e.getMessage());
        } finally {
            Target current = target;
            if (current != null) current.lost = true;
            if (hook != null) user.UnhookWinEvent(hook);
            synchronized (this) {
                stopping = true;
                CompletableFuture<Target> pending;
                while ((pending = pendingTargets.poll()) != null)
                    pending.completeExceptionally(new IllegalStateException("Surveillance des fenêtres arrêtée."));
                threadId = 0;
            }
        }
    }

    synchronized CompletableFuture<Target> captureForeground() {
        CompletableFuture<Target> pending = new CompletableFuture<>();
        if (stopping || threadId == 0 || !ready.isDone() || ready.isCompletedExceptionally()) {
            pending.completeExceptionally(new IllegalStateException("Surveillance des fenêtres indisponible."));
            return pending;
        }
        pendingTargets.add(pending);
        if (User32.INSTANCE.PostThreadMessage(threadId, CAPTURE_TARGET_MESSAGE, null, null) == 0) {
            pendingTargets.remove(pending);
            pending.completeExceptionally(new IllegalStateException("Impossible de sélectionner la fenêtre cible."));
        }
        return pending;
    }

    static boolean sameWindow(HWND a, HWND b) {
        return a != null && b != null && Pointer.nativeValue(a.getPointer()) == Pointer.nativeValue(b.getPointer());
    }

    Target track(HWND window) {
        Target next = new Target(window);
        target = next;
        return next;
    }

    static final class Target {
        final HWND window;
        volatile boolean lost;
        Target(HWND window) { this.window = window; }
        boolean belongsToThisProcess() {
            IntByReference process = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, process);
            return process.getValue() == Kernel32.INSTANCE.GetCurrentProcessId();
        }
        boolean active() {
            if (!sameWindow(window, User32.INSTANCE.GetForegroundWindow())) lost = true;
            return !lost;
        }
    }

    public void close() {
        synchronized (this) { stopping = true; }
        int id = threadId;
        if (id != 0) User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, null, null);
        if (thread != null && Thread.currentThread() != thread) {
            try { thread.join(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
