/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import app.morphe.extension.crimera.downloader.engine.DestinationLoss;
import app.morphe.extension.crimera.downloader.engine.DestinationWriter;
import app.morphe.extension.crimera.downloader.engine.DeferredScheduler;
import app.morphe.extension.crimera.downloader.engine.DownloadController;
import app.morphe.extension.crimera.downloader.engine.FailureClassifier;
import app.morphe.extension.crimera.downloader.engine.Reservation;
import app.morphe.extension.crimera.downloader.engine.TransferObserver;
import app.morphe.extension.crimera.downloader.engine.TransferOutcome;
import app.morphe.extension.crimera.downloader.engine.TransferRunner;
import app.morphe.extension.crimera.downloader.events.DownloadEvent;
import app.morphe.extension.crimera.downloader.events.DownloadEvents;
import app.morphe.extension.crimera.downloader.events.FailureReason;
import app.morphe.extension.crimera.downloader.events.ProgressThrottle;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/** Orchestrates destination reservations, transfers, and lifecycle events. */
public final class DownloadEngine implements DownloadController {
    private static final int MAX_RECENT = 256;
    private static final ScheduledThreadPoolExecutor DEFERRED_EXECUTOR = createDeferredExecutor();
    private static final DeferredScheduler DEFAULT_DEFERRED_SCHEDULER = (task, delayMillis) -> {
        ScheduledFuture<?> future = DEFERRED_EXECUTOR.schedule(
                task, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    };

    /**
     * Highest id the engine hands out itself. A download's progress notice uses its id as the
     * notification id, so ids above this are left to the notification code for completed and failed
     * notices, and the two can never be the same notification.
     */
    public static final int MAX_FRESH_ID = (1 << 30) - 1;

    @Nullable private final Context applicationContext;
    private final DestinationWriter destination;
    private final TransferRunner transfer;
    private final DownloadEvents events;
    private final Executor transferExecutor;
    private final DeferredScheduler deferredScheduler;
    private final ProgressThrottle throttle;

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, DownloadRecord> activeDownloads = new ConcurrentHashMap<>();
    private final Object recentLock = new Object();
    private final LinkedHashMap<Integer, DownloadRecord> recent = new LinkedHashMap<Integer, DownloadRecord>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, DownloadRecord> eldest) {
            return size() > MAX_RECENT;
        }
    };

    public DownloadEngine(
            @Nullable Context applicationContext,
            DestinationWriter destination,
            TransferRunner transfer,
            DownloadEvents events,
            Executor transferExecutor,
            LongSupplier clockMillis
    ) {
        this(applicationContext, destination, transfer, events, transferExecutor,
                DEFAULT_DEFERRED_SCHEDULER, clockMillis);
    }

    public DownloadEngine(
            @Nullable Context applicationContext,
            DestinationWriter destination,
            TransferRunner transfer,
            DownloadEvents events,
            Executor transferExecutor,
            DeferredScheduler deferredScheduler,
            LongSupplier clockMillis
    ) {
        this.applicationContext = applicationContext;
        this.destination = Objects.requireNonNull(destination, "destination");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.events = Objects.requireNonNull(events, "events");
        this.transferExecutor = Objects.requireNonNull(transferExecutor, "transferExecutor");
        this.deferredScheduler = Objects.requireNonNull(deferredScheduler, "deferredScheduler");
        this.throttle = new ProgressThrottle(Objects.requireNonNull(clockMillis, "clockMillis"));
    }

    /** Reserves the destination on the caller's thread, then runs the transfer on the executor. */
    public EnqueueResult enqueue(DownloadRequest request) {
        Objects.requireNonNull(request, "request");
        return enqueue(nextFreshId(), request);
    }

    public EnqueueResult enqueue(int id, DownloadRequest request) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive: " + id);
        }
        Objects.requireNonNull(request, "request");
        if (activeDownloads.containsKey(id)) {
            throw new IllegalStateException("download " + id + " is already queued or running");
        }

        Reservation reservation;
        try {
            reservation = destination.reserve(applicationContext, request);
        } catch (IOException | RuntimeException e) {
            if (DestinationLoss.matches(e)) {
                return EnqueueResult.destinationLost(e);
            }
            return EnqueueResult.failed(e);
        }

        if (reservation == null) {
            return EnqueueResult.skipped();
        }

        DownloadRecord record = new DownloadRecord(id, request, reservation, this);
        if (activeDownloads.putIfAbsent(id, record) != null) {
            try {
                destination.discard(applicationContext, reservation);
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("download " + id + " is already queued or running");
        }

        synchronized (recentLock) {
            recent.remove(id);
            recent.put(id, record);
        }

        events.post(new DownloadEvent.Queued(id, request.label()));

        RejectedExecutionException rejection = submitTransfer(record);
        if (rejection != null) return EnqueueResult.failed(rejection);

        return EnqueueResult.queued(record.handle);
    }

    @Override
    public boolean cancel(int id) {
        DownloadRecord record = activeDownloads.get(id);
        if (record == null) {
            return false;
        }
        DeferredScheduler.Cancellable wakeup;
        boolean resumeNow;
        synchronized (record) {
            if (record.terminal) return false;
            record.cancelRequested = true;
            wakeup = record.wakeup;
            record.wakeup = null;
            resumeNow = wakeup != null && !record.transferRunning && !record.transferQueued;
        }
        transfer.cancel(id);
        if (wakeup != null) wakeup.cancel();
        if (resumeNow) submitTransfer(record);
        return true;
    }

    @Override
    public void retry(int id, DownloadRequest request) {
        try {
            enqueue(id, request);
        } catch (IllegalStateException ignored) {
        }
    }

    @Nullable
    public DownloadHandle handle(int id) {
        DownloadRecord active = activeDownloads.get(id);
        if (active != null) {
            return active.handle;
        }
        synchronized (recentLock) {
            DownloadRecord record = recent.get(id);
            return record != null ? record.handle : null;
        }
    }

    @Nullable
    public DownloadRequest requestFor(int id) {
        DownloadRecord active = activeDownloads.get(id);
        if (active != null) {
            return active.request;
        }
        synchronized (recentLock) {
            DownloadRecord record = recent.get(id);
            return record != null ? record.request : null;
        }
    }

    private int nextFreshId() {
        while (true) {
            int id = generateId();
            if (!activeDownloads.containsKey(id)) {
                return id;
            }
        }
    }

    private int generateId() {
        while (true) {
            int current = nextId.get();
            if (current <= 0) {
                if (nextId.compareAndSet(current, 2)) {
                    return 1;
                }
                continue;
            }
            int next = current >= MAX_FRESH_ID ? 1 : current + 1;
            if (nextId.compareAndSet(current, next)) {
                return current;
            }
        }
    }

    private RejectedExecutionException submitTransfer(DownloadRecord record) {
        synchronized (record) {
            if (record.terminal || record.transferQueued || record.transferRunning) return null;
            record.transferQueued = true;
        }

        try {
            transferExecutor.execute(() -> runTransfer(record));
            return null;
        } catch (RejectedExecutionException rejection) {
            synchronized (record) {
                record.transferQueued = false;
            }
            rejectTransfer(record, rejection);
            return rejection;
        }
    }

    private void runTransfer(DownloadRecord record) {
        int id = record.id;
        DownloadRequest request = record.request;
        boolean postStarted = false;

        synchronized (record) {
            record.transferQueued = false;
            if (record.terminal) return;
            record.transferRunning = true;
            if (!record.started) {
                record.started = true;
                record.state = DownloadHandle.State.RUNNING;
                record.lastEventWasWaiting = false;
                postStarted = true;
            }
        }
        if (postStarted) events.post(new DownloadEvent.Started(id, request.label()));

        TransferObserver observer = new TransferObserver() {
            @Override
            public void onWaitingForConnection() {
                record.lastEventWasWaiting = true;
                events.post(new DownloadEvent.Waiting(id, request.label()));
            }

            @Override
            public void onTransferring() {
                if (record.lastEventWasWaiting) {
                    record.lastEventWasWaiting = false;
                    events.post(new DownloadEvent.Started(id, request.label()));
                }
            }

            @Override
            public void onProgress(long bytesDone, long totalBytes) {
                if (throttle.shouldEmit(id, bytesDone, totalBytes)) {
                    record.lastEventWasWaiting = false;
                    events.post(new DownloadEvent.Progress(id, request.label(), bytesDone, totalBytes));
                }
            }
        };

        TransferOutcome outcome;
        try {
            outcome = transfer.run(applicationContext, record.reservation, request, id, observer);
            if (outcome == null) {
                outcome = TransferOutcome.failed(new IllegalStateException("transfer returned null outcome"));
            }
        } catch (RuntimeException e) {
            outcome = TransferOutcome.failed(e);
        }

        boolean deferred = outcome.kind() == TransferOutcome.Kind.DEFERRED;
        try {
            if (deferred) {
                deferTransfer(record, outcome);
                return;
            }

            if (outcome.kind() == TransferOutcome.Kind.SAVED) {
                events.post(new DownloadEvent.Completed(
                        id,
                        request.label(),
                        record.reservation.documentUri(),
                        record.reservation.fileName(),
                        record.reservation.mimeType()
                ));
                record.state = DownloadHandle.State.COMPLETED;
            } else if (outcome.kind() == TransferOutcome.Kind.CANCELLED) {
                try {
                    destination.discard(applicationContext, record.reservation);
                } catch (Exception ignored) {
                }
                events.post(new DownloadEvent.Cancelled(id, request.label()));
                record.state = DownloadHandle.State.CANCELLED;
            } else {
                try {
                    destination.discard(applicationContext, record.reservation);
                } catch (Exception ignored) {
                }
                Throwable cause = outcome.cause();
                FailureReason reason = FailureClassifier.classify(cause);
                boolean retriable = reason != FailureReason.DESTINATION_LOST;
                events.post(new DownloadEvent.Failed(id, request.label(), reason, retriable));
                record.state = DownloadHandle.State.FAILED;
            }
        } finally {
            if (!deferred) finishTransfer(record);
        }
    }

    private void deferTransfer(DownloadRecord record, TransferOutcome outcome) {
        RejectedExecutionException rejection = null;
        synchronized (record) {
            if (record.terminal) return;
            record.transferRunning = false;
            long delayMillis = record.cancelRequested ? 0 : outcome.delayMillis();
            try {
                record.wakeup = deferredScheduler.schedule(
                        () -> wakeTransfer(record), delayMillis);
            } catch (RejectedExecutionException exception) {
                rejection = exception;
            }
        }
        if (rejection != null) rejectTransfer(record, rejection);
    }

    private void wakeTransfer(DownloadRecord record) {
        synchronized (record) {
            record.wakeup = null;
        }
        submitTransfer(record);
    }

    private void finishTransfer(DownloadRecord record) {
        synchronized (record) {
            if (record.terminal) return;
            record.terminal = true;
            record.transferRunning = false;
            record.transferQueued = false;
            if (record.wakeup != null) {
                record.wakeup.cancel();
                record.wakeup = null;
            }
        }
        throttle.forget(record.id);
        activeDownloads.remove(record.id, record);
    }

    private void rejectTransfer(DownloadRecord record, RejectedExecutionException rejection) {
        synchronized (record) {
            if (record.terminal) return;
            record.terminal = true;
            record.transferQueued = false;
            record.transferRunning = false;
            if (record.wakeup != null) {
                record.wakeup.cancel();
                record.wakeup = null;
            }
        }
        try {
            destination.discard(applicationContext, record.reservation);
        } catch (Exception ignored) {
        }
        events.post(new DownloadEvent.Failed(
                record.id, record.request.label(), FailureReason.UNKNOWN, true));
        record.state = DownloadHandle.State.FAILED;
        throttle.forget(record.id);
        activeDownloads.remove(record.id, record);
    }

    private static ScheduledThreadPoolExecutor createDeferredExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "download-retries");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    static final class DownloadRecord {
        final int id;
        final DownloadRequest request;
        final Reservation reservation;
        final DownloadHandle handle;
        volatile DownloadHandle.State state = DownloadHandle.State.QUEUED;
        volatile boolean lastEventWasWaiting = false;
        boolean started;
        boolean transferQueued;
        boolean transferRunning;
        boolean terminal;
        boolean cancelRequested;
        DeferredScheduler.Cancellable wakeup;

        DownloadRecord(int id, DownloadRequest request, Reservation reservation, DownloadEngine engine) {
            this.id = id;
            this.request = request;
            this.reservation = reservation;
            this.handle = new DownloadHandle(id, engine, this);
        }
    }
}
