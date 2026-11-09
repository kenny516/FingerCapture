package com.zkteco.enrollment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A cancelling session keeps its slot until native calls and cleanup finish. */
final class EnrollmentController implements AutoCloseable {
    interface Task { void run(AtomicBoolean cancelled) throws Exception; }
    private final ExecutorService worker;
    private final Consumer<String> status;
    private final Runnable finished;
    private AtomicBoolean active;
    private boolean closed;

    EnrollmentController(ExecutorService worker, Consumer<String> status) {
        this(worker, status, () -> {});
    }

    EnrollmentController(ExecutorService worker, Consumer<String> status, Runnable finished) {
        this.worker = worker; this.status = status;
        this.finished = finished;
    }

    synchronized void toggle(Task task) {
        if (closed) return;
        if (active != null) {
            active.set(true);
            status.accept("Annulation en cours… Attendez la libération du lecteur.");
            return;
        }
        AtomicBoolean token = new AtomicBoolean();
        active = token;
        worker.execute(() -> {
            try {
                task.run(token);
            } catch (LinkageError e) {
                status.accept("SDK indisponible : vérifiez libzkfp.dll, ses dépendances et l'architecture Java (x86/x64).");
            } catch (Exception e) {
                status.accept(e.getMessage() == null ? "Échec de l'enrôlement. Recommencez." : e.getMessage());
            } finally {
                synchronized (EnrollmentController.this) { active = null; }
                finished.run();
            }
        });
    }

    synchronized void cancel() {
        if (active != null) active.set(true);
    }

    public synchronized void close() {
        closed = true;
        if (active != null) active.set(true);
        worker.shutdown(); // Do not release SDK handles concurrently with a native call.
    }
}
