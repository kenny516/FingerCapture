package com.zkteco.enrollment;

import java.util.function.IntConsumer;

/** Countdown driven by the Swing timer; cancelling never opens the reader. */
final class ListenPreparation {
    private final IntConsumer remaining;
    private final Runnable ready;
    private int seconds;
    ListenPreparation(IntConsumer remaining, Runnable ready) {
        this.remaining = remaining; this.ready = ready;
    }
    void start() { seconds = 5; remaining.accept(seconds); }
    void tick() {
        if (seconds == 0) return;
        seconds--;
        if (seconds == 0) ready.run();
        else remaining.accept(seconds);
    }
    void cancel() { seconds = 0; }
}
