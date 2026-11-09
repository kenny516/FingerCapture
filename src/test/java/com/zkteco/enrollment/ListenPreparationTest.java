package com.zkteco.enrollment;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListenPreparationTest {
    @Test void readerStartsOnlyAfterFiveSecondsAndOnlyOnce() {
        List<Integer> displayed = new ArrayList<>();
        AtomicInteger starts = new AtomicInteger();
        ListenPreparation preparation = new ListenPreparation(displayed::add, starts::incrementAndGet);
        preparation.start();
        for (int i = 0; i < 4; i++) preparation.tick();
        assertEquals(0, starts.get());
        preparation.tick();
        preparation.tick();
        assertEquals(1, starts.get());
        assertEquals(List.of(5, 4, 3, 2, 1), displayed);
    }
    @Test void cancelledCountdownNeverStartsAndCanBeRestarted() {
        AtomicInteger starts = new AtomicInteger();
        ListenPreparation preparation = new ListenPreparation(n -> {}, starts::incrementAndGet);
        preparation.start(); preparation.tick(); preparation.cancel();
        for (int i = 0; i < 6; i++) preparation.tick();
        assertEquals(0, starts.get());
        preparation.start();
        for (int i = 0; i < 5; i++) preparation.tick();
        assertEquals(1, starts.get());
    }
}
