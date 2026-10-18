/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

/** Schedules a parked transfer without occupying a transfer worker while it waits. */
public interface DeferredScheduler {
    Cancellable schedule(Runnable task, long delayMillis);

    interface Cancellable {
        void cancel();
    }
}
