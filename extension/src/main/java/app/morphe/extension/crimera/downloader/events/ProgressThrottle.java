/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.events;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Throttles high-frequency progress events per download.
 */
public final class ProgressThrottle {
    private static final long TIME_WINDOW_MS = 250L;

    private final LongSupplier clockMillis;
    private final ConcurrentHashMap<Integer, State> states = new ConcurrentHashMap<>();

    public ProgressThrottle(LongSupplier clockMillis) {
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    public boolean shouldEmit(int downloadId, long bytesDone, long totalBytes) {
        long now = clockMillis.getAsLong();
        int currentPercent = totalBytes > 0 ? (int) ((bytesDone * 100L) / totalBytes) : -1;

        boolean[] emitted = new boolean[1];
        states.compute(downloadId, (id, state) -> {
            if (state == null) {
                emitted[0] = true;
                return new State(now, currentPercent);
            }

            boolean timePassed = (now - state.lastEmitTimeMs) >= TIME_WINDOW_MS;
            boolean percentAdvanced = totalBytes > 0 && (state.lastPercent >= 0
                    ? (currentPercent - state.lastPercent >= 1)
                    : (currentPercent >= 1));
            boolean completed = totalBytes > 0 && bytesDone >= totalBytes;

            if (timePassed || percentAdvanced || completed) {
                emitted[0] = true;
                return new State(now, currentPercent);
            }
            return state;
        });

        return emitted[0];
    }

    public void forget(int downloadId) {
        states.remove(downloadId);
    }

    private static final class State {
        final long lastEmitTimeMs;
        final int lastPercent;

        State(long lastEmitTimeMs, int lastPercent) {
            this.lastEmitTimeMs = lastEmitTimeMs;
            this.lastPercent = lastPercent;
        }
    }
}
