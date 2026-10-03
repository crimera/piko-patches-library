/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.events;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Non-blocking event dispatcher delivering ordered lifecycle events per listener lane.
 * Coalesces pending progress updates in place and ensures terminal events are never dropped.
 */
public final class DownloadEvents {
    private final Consumer<Throwable> errorSink;
    private final CopyOnWriteArrayList<Lane> lanes = new CopyOnWriteArrayList<>();

    public DownloadEvents(Consumer<Throwable> errorSink) {
        this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
    }

    /**
     * Registers a listener on the executor; replaces any previous registration for this listener.
     */
    public void register(DownloadListener listener, Executor executor) {
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(executor, "executor");
        unregister(listener);
        lanes.add(new Lane(listener, executor, errorSink));
    }

    public void unregister(DownloadListener listener) {
        Objects.requireNonNull(listener, "listener");
        for (Lane lane : lanes) {
            if (lane.listener == listener) {
                lane.unregister();
                lanes.remove(lane);
            }
        }
    }

    public void post(DownloadEvent event) {
        Objects.requireNonNull(event, "event");
        for (Lane lane : lanes) {
            lane.post(event);
        }
    }

    private static final class Entry {
        DownloadEvent event;

        Entry(DownloadEvent event) {
            this.event = event;
        }
    }

    private static final class Lane {
        private static final int MAX_TERMINATED_IDS = 1024;

        final DownloadListener listener;
        private final Executor executor;
        private final Consumer<Throwable> errorSink;

        // Synchronized on this to protect queue state and scheduling handoff across threads.
        private final ArrayDeque<Entry> queue = new ArrayDeque<>();
        private final Map<Integer, Entry> pendingProgress = new HashMap<>();
        private final Set<Integer> terminatedIds = new LinkedHashSet<>();
        private boolean drainScheduled = false;
        private boolean unregistered = false;

        Lane(DownloadListener listener, Executor executor, Consumer<Throwable> errorSink) {
            this.listener = listener;
            this.executor = executor;
            this.errorSink = errorSink;
        }

        void post(DownloadEvent event) {
            boolean scheduleDrain = false;
            synchronized (this) {
                if (unregistered) {
                    return;
                }

                if (event instanceof DownloadEvent.Queued) {
                    terminatedIds.remove(event.id());
                } else if (terminatedIds.contains(event.id())) {
                    return;
                }

                if (event.isTerminal()) {
                    if (terminatedIds.size() >= MAX_TERMINATED_IDS) {
                        // Safe to evict because downloads older than 1024 completions are finished and will not receive stale events.
                        Integer oldest = terminatedIds.iterator().next();
                        terminatedIds.remove(oldest);
                    }
                    terminatedIds.add(event.id());
                }

                if (event instanceof DownloadEvent.Progress) {
                    Entry existing = pendingProgress.get(event.id());
                    if (existing != null) {
                        existing.event = event;
                        return;
                    }
                }

                Entry entry = new Entry(event);
                if (event instanceof DownloadEvent.Progress) {
                    pendingProgress.put(event.id(), entry);
                }
                queue.addLast(entry);

                if (!drainScheduled) {
                    drainScheduled = true;
                    scheduleDrain = true;
                }
            }

            if (scheduleDrain) {
                try {
                    executor.execute(this::drain);
                } catch (Throwable t) {
                    synchronized (this) {
                        drainScheduled = false;
                    }
                    errorSink.accept(t);
                }
            }
        }

        private void drain() {
            while (true) {
                DownloadEvent event;
                synchronized (this) {
                    if (unregistered || queue.isEmpty()) {
                        drainScheduled = false;
                        return;
                    }
                    Entry entry = queue.removeFirst();
                    if (entry.event instanceof DownloadEvent.Progress) {
                        pendingProgress.remove(entry.event.id());
                    }
                    event = entry.event;
                }

                try {
                    deliver(listener, event);
                } catch (Throwable t) {
                    errorSink.accept(t);
                }
            }
        }

        synchronized void unregister() {
            unregistered = true;
            queue.clear();
            pendingProgress.clear();
            terminatedIds.clear();
        }

        private static void deliver(DownloadListener listener, DownloadEvent event) {
            if (event instanceof DownloadEvent.Queued) {
                listener.onQueued((DownloadEvent.Queued) event);
            } else if (event instanceof DownloadEvent.Started) {
                listener.onStarted((DownloadEvent.Started) event);
            } else if (event instanceof DownloadEvent.Waiting) {
                listener.onWaiting((DownloadEvent.Waiting) event);
            } else if (event instanceof DownloadEvent.Progress) {
                listener.onProgress((DownloadEvent.Progress) event);
            } else if (event instanceof DownloadEvent.Completed) {
                listener.onCompleted((DownloadEvent.Completed) event);
            } else if (event instanceof DownloadEvent.Failed) {
                listener.onFailed((DownloadEvent.Failed) event);
            } else if (event instanceof DownloadEvent.Cancelled) {
                listener.onCancelled((DownloadEvent.Cancelled) event);
            }
        }
    }
}
