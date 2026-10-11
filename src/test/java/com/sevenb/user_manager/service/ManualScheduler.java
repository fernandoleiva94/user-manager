package com.sevenb.user_manager.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Scheduler de test: guarda las tareas con su demora y las corre a pedido, sin esperas reales. */
final class ManualScheduler {

    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final List<Duration> delays = new ArrayList<>();
    private final Deque<Runnable> pending = new ArrayDeque<>();

    ManualScheduler() {
        when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(inv -> {
            long delay = inv.getArgument(1);
            TimeUnit unit = inv.getArgument(2);
            delays.add(Duration.of(delay, unit.toChronoUnit()));
            pending.add(inv.getArgument(0));
            return null;
        });
    }

    void runAll() {
        while (!pending.isEmpty()) {
            pending.poll().run();
        }
    }

    int pendingCount() {
        return pending.size();
    }
}
