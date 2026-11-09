package com.zkteco.enrollment;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.Arrays;
import java.util.Base64;

final class EnrollmentSession {
    interface Reader extends AutoCloseable {
        void open() throws Exception;
        byte[] capture() throws Exception;
        boolean matches(byte[] first, byte[] next) throws Exception;
        byte[] merge(byte[][] samples) throws Exception;
        void close() throws Exception;
    }
    interface Desktop {
        boolean targetActive();
        void pasteAndEnter(String base64, Guard guard) throws Exception;
    }
    interface Clock {
        long nanoTime();
        void pause() throws InterruptedException;
    }
    interface Guard { void check() throws Exception; }
    private final Reader reader;
    private final Desktop desktop;
    private final Clock clock;
    private final Consumer<String> status;
    private final IntConsumer acceptedSamples;
    EnrollmentSession(Reader reader, Desktop desktop, Clock clock, Consumer<String> status) {
        this(reader, desktop, clock, status, count -> {});
    }
    EnrollmentSession(Reader reader, Desktop desktop, Clock clock, Consumer<String> status, IntConsumer acceptedSamples) {
        this.reader = reader; this.desktop = desktop; this.clock = clock; this.status = status;
        this.acceptedSamples = acceptedSamples;
    }
    void run(BooleanSupplier cancelled) throws Exception {
        acceptedSamples.accept(0);
        long started = clock.nanoTime();
        Guard guard = () -> {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
                throw new Exception("Enrôlement annulé. Recommencez dans le champ souhaité.");
            if (clock.nanoTime() - started >= 60_000_000_000L)
                throw new Exception("Délai de 60 secondes dépassé. Recommencez.");
            if (!desktop.targetActive())
                throw new Exception("Fenêtre changée : insertion annulée. Recommencez dans le formulaire.");
        };
        byte[][] samples = new byte[3][];
        byte[] merged = null;
        try (Reader resource = reader) {
            guard.check();
            status.accept("Ouverture du lecteur…");
            resource.open();
            for (int index = 0; index < 3; index++) {
                status.accept(index == 0 ? "Posez le doigt."
                        : "Retirez puis reposez le même doigt.");
                byte[] sample;
                do {
                    guard.check();
                    sample = resource.capture();
                    // Take ownership before a cancellation, timeout or focus check can throw.
                    samples[index] = sample;
                    guard.check();
                    if (sample == null) clock.pause();
                } while (sample == null);
                if (index > 0 && !resource.matches(samples[0], sample))
                    throw new Exception("Doigt différent ou lecture insuffisante : recommencez avec le même doigt.");
                acceptedSamples.accept(index + 1);
                status.accept("Lecture " + (index + 1) + "/3 acceptée.");
            }
            guard.check();
            status.accept("Finalisation…");
            merged = resource.merge(samples);
            guard.check();
            if (merged == null || merged.length == 0)
                throw new Exception("Le SDK a retourné un gabarit vide.");
            status.accept("Envoi au formulaire…");
            desktop.pasteAndEnter(Base64.getEncoder().encodeToString(merged), guard);
            status.accept("Empreinte envoyée.");
        } finally {
            for (byte[] sample : samples) if (sample != null) Arrays.fill(sample, (byte) 0);
            if (merged != null) Arrays.fill(merged, (byte) 0);
        }
    }
}
