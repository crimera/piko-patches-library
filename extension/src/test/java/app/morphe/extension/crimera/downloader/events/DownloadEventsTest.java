package app.morphe.extension.crimera.downloader.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class DownloadEventsTest {
    @Test
    public void orderingPerDownload() {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
        }, executor);

        dispatcher.post(new DownloadEvent.Queued(1, "user1"));
        dispatcher.post(new DownloadEvent.Started(1, "user1"));
        dispatcher.post(new DownloadEvent.Progress(1, "user1", 50, 100));
        dispatcher.post(new DownloadEvent.Completed(1, "user1", null, "video.mp4"));

        executor.runAll();

        assertEquals(4, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Queued);
        assertEquals(1, received.get(0).id());
        assertEquals("user1", received.get(0).label());

        assertTrue(received.get(1) instanceof DownloadEvent.Started);
        assertEquals(1, received.get(1).id());

        assertTrue(received.get(2) instanceof DownloadEvent.Progress);
        DownloadEvent.Progress progress = (DownloadEvent.Progress) received.get(2);
        assertEquals(50, progress.bytesDone());
        assertEquals(100, progress.totalBytes());

        assertTrue(received.get(3) instanceof DownloadEvent.Completed);
        DownloadEvent.Completed completed = (DownloadEvent.Completed) received.get(3);
        assertEquals("video.mp4", completed.fileName());
    }

    @Test
    public void postAfterDrainCompletesDeliversSubsequentEventsDirectExecutor() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
        }, Runnable::run);

        dispatcher.post(new DownloadEvent.Queued(1, null));
        assertEquals(1, received.size());

        // Second post after drain completed must clear drainScheduled and schedule delivery
        dispatcher.post(new DownloadEvent.Started(1, null));
        assertEquals(2, received.size());
        assertTrue(received.get(1) instanceof DownloadEvent.Started);
    }

    @Test
    public void postAfterDrainCompletesDeliversSubsequentEventsManualExecutor() {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
        }, executor);

        dispatcher.post(new DownloadEvent.Queued(1, null));
        executor.runAll();
        assertEquals(1, received.size());

        dispatcher.post(new DownloadEvent.Started(1, null));
        executor.runAll();
        assertEquals(2, received.size());
        assertTrue(received.get(1) instanceof DownloadEvent.Started);
    }

    @Test(timeout = 10000)
    public void deliveryNeverOverlapsWithMultiThreadedExecutor() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        ExecutorService producers = Executors.newFixedThreadPool(4);
        try {
            DownloadEvents dispatcher = new DownloadEvents(t -> {});
            AtomicInteger activeCallbacks = new AtomicInteger();
            AtomicBoolean overlapped = new AtomicBoolean();
            int eventCount = 100;
            CountDownLatch latch = new CountDownLatch(eventCount);

            DownloadListener listener = new DownloadListener() {
                @Override
                public void onStarted(DownloadEvent.Started event) {
                    int count = activeCallbacks.incrementAndGet();
                    if (count > 1) {
                        overlapped.set(true);
                    }
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        activeCallbacks.decrementAndGet();
                        latch.countDown();
                    }
                }
            };

            dispatcher.register(listener, executor);

            for (int i = 0; i < eventCount; i++) {
                final int id = i + 1;
                producers.execute(() -> dispatcher.post(new DownloadEvent.Started(id, null)));
            }
            producers.shutdown();
            assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse("Listener callback must never overlap itself", overlapped.get());
        } finally {
            producers.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void postReturnsWhileExecutorIsBlocked() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch blockStarted = new CountDownLatch(1);
            CountDownLatch releaseBlock = new CountDownLatch(1);
            CountDownLatch eventDelivered = new CountDownLatch(1);

            executor.execute(() -> {
                blockStarted.countDown();
                try {
                    releaseBlock.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(blockStarted.await(2, TimeUnit.SECONDS));

            DownloadEvents dispatcher = new DownloadEvents(t -> {});
            dispatcher.register(new DownloadListener() {
                @Override
                public void onQueued(DownloadEvent.Queued event) {
                    eventDelivered.countDown();
                }
            }, executor);

            long startTime = System.currentTimeMillis();
            dispatcher.post(new DownloadEvent.Queued(1, null));
            long durationMs = System.currentTimeMillis() - startTime;
            assertTrue("post must return immediately while executor is blocked, took " + durationMs + "ms", durationMs < 200);

            releaseBlock.countDown();
            assertTrue(eventDelivered.await(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void progressCoalescesInPlace() {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
        }, executor);

        dispatcher.post(new DownloadEvent.Queued(1, "u"));
        dispatcher.post(new DownloadEvent.Started(1, "u"));
        dispatcher.post(new DownloadEvent.Progress(1, "u", 100, 1000));
        dispatcher.post(new DownloadEvent.Queued(2, "v"));
        dispatcher.post(new DownloadEvent.Progress(1, "u", 500, 1000));

        executor.runAll();

        assertEquals(4, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Queued);
        assertEquals(1, received.get(0).id());
        assertTrue(received.get(1) instanceof DownloadEvent.Started);
        assertEquals(1, received.get(1).id());

        // In-place replacement: Progress for id 1 stays at position 2 ahead of Queued(2) at position 3
        assertTrue(received.get(2) instanceof DownloadEvent.Progress);
        DownloadEvent.Progress progress = (DownloadEvent.Progress) received.get(2);
        assertEquals(1, progress.id());
        assertEquals(500, progress.bytesDone());

        assertTrue(received.get(3) instanceof DownloadEvent.Queued);
        assertEquals(2, received.get(3).id());
    }

    @Test
    public void progressCoalescingDoesNotCrossIds() {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent.Progress> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
        }, executor);

        dispatcher.post(new DownloadEvent.Progress(1, null, 10, 100));
        dispatcher.post(new DownloadEvent.Progress(2, null, 20, 200));
        dispatcher.post(new DownloadEvent.Progress(1, null, 50, 100));
        dispatcher.post(new DownloadEvent.Progress(2, null, 80, 200));

        executor.runAll();

        assertEquals(2, received.size());
        assertEquals(1, received.get(0).id());
        assertEquals(50, received.get(0).bytesDone());
        assertEquals(2, received.get(1).id());
        assertEquals(80, received.get(1).bytesDone());
    }

    @Test
    public void terminalEventsSurviveBacklog() {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
        }, executor);

        dispatcher.post(new DownloadEvent.Queued(1, null));
        dispatcher.post(new DownloadEvent.Started(1, null));
        dispatcher.post(new DownloadEvent.Progress(1, null, 50, 100));
        dispatcher.post(new DownloadEvent.Progress(1, null, 90, 100));
        dispatcher.post(new DownloadEvent.Completed(1, null, null, "out.mp4"));

        executor.runAll();

        assertEquals(4, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Queued);
        assertTrue(received.get(1) instanceof DownloadEvent.Started);
        assertTrue(received.get(2) instanceof DownloadEvent.Progress);
        assertEquals(90, ((DownloadEvent.Progress) received.get(2)).bytesDone());
        assertTrue(received.get(3) instanceof DownloadEvent.Completed);
        assertEquals("out.mp4", ((DownloadEvent.Completed) received.get(3)).fileName());
    }

    @Test
    public void eventsAfterCompletedAreIgnored() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
            @Override
            public void onFailed(DownloadEvent.Failed event) { received.add(event); }
            @Override
            public void onCancelled(DownloadEvent.Cancelled event) { received.add(event); }
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
        }, Runnable::run);

        dispatcher.post(new DownloadEvent.Completed(1, null, null, "done.mp4"));
        dispatcher.post(new DownloadEvent.Failed(1, null, FailureReason.NO_CONNECTION, false));
        dispatcher.post(new DownloadEvent.Cancelled(1, null));
        dispatcher.post(new DownloadEvent.Progress(1, null, 100, 100));

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Completed);
    }

    @Test
    public void eventsAfterFailedAreIgnored() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
            @Override
            public void onFailed(DownloadEvent.Failed event) { received.add(event); }
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
        }, Runnable::run);

        dispatcher.post(new DownloadEvent.Failed(1, null, FailureReason.DESTINATION_LOST, true));
        dispatcher.post(new DownloadEvent.Progress(1, null, 100, 100));
        dispatcher.post(new DownloadEvent.Completed(1, null, null, "late.mp4"));

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Failed);
    }

    @Test
    public void eventsAfterCancelledAreIgnored() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
            @Override
            public void onCancelled(DownloadEvent.Cancelled event) { received.add(event); }
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
        }, Runnable::run);

        dispatcher.post(new DownloadEvent.Cancelled(1, null));
        dispatcher.post(new DownloadEvent.Progress(1, null, 100, 100));
        dispatcher.post(new DownloadEvent.Completed(1, null, null, "late.mp4"));

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Cancelled);
    }

    @Test
    public void completedThenFailedDeliversOnlyCompleted() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
            @Override
            public void onFailed(DownloadEvent.Failed event) { received.add(event); }
        }, Runnable::run);

        dispatcher.post(new DownloadEvent.Completed(1, null, null, "first.mp4"));
        dispatcher.post(new DownloadEvent.Failed(1, null, FailureReason.UNKNOWN, false));

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Completed);
    }

    @Test
    public void retryCycleReusingDownloadIdDeliversNewLifecycleInFull() {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
            @Override
            public void onFailed(DownloadEvent.Failed event) { received.add(event); }
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
        }, executor);

        // Cycle 1 ends in Failure
        dispatcher.post(new DownloadEvent.Queued(1, "user"));
        dispatcher.post(new DownloadEvent.Started(1, "user"));
        dispatcher.post(new DownloadEvent.Failed(1, "user", FailureReason.NO_CONNECTION, true));

        // Stale event while terminated is ignored
        dispatcher.post(new DownloadEvent.Progress(1, "user", 50, 100));

        // Cycle 2 reuses the same id, Queued clears terminal mark
        dispatcher.post(new DownloadEvent.Queued(1, "user"));
        dispatcher.post(new DownloadEvent.Started(1, "user"));
        dispatcher.post(new DownloadEvent.Completed(1, "user", null, "final.mp4"));

        executor.runAll();

        assertEquals(6, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Queued);
        assertTrue(received.get(1) instanceof DownloadEvent.Started);
        assertTrue(received.get(2) instanceof DownloadEvent.Failed);
        assertTrue(received.get(3) instanceof DownloadEvent.Queued);
        assertTrue(received.get(4) instanceof DownloadEvent.Started);
        assertTrue(received.get(5) instanceof DownloadEvent.Completed);
    }

    @Test
    public void retryCycleReusingDownloadIdFromDifferentThreads() throws InterruptedException {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        dispatcher.register(new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
            @Override
            public void onFailed(DownloadEvent.Failed event) { received.add(event); }
            @Override
            public void onCompleted(DownloadEvent.Completed event) { received.add(event); }
        }, executor);

        // Thread 1 posts cycle 1
        Thread t1 = new Thread(() -> {
            dispatcher.post(new DownloadEvent.Queued(42, "user"));
            dispatcher.post(new DownloadEvent.Started(42, "user"));
            dispatcher.post(new DownloadEvent.Failed(42, "user", FailureReason.DESTINATION_LOST, true));
        });
        t1.start();
        t1.join();

        // Thread 2 posts cycle 2 for same id
        Thread t2 = new Thread(() -> {
            dispatcher.post(new DownloadEvent.Queued(42, "user"));
            dispatcher.post(new DownloadEvent.Started(42, "user"));
            dispatcher.post(new DownloadEvent.Completed(42, "user", null, "done.mp4"));
        });
        t2.start();
        t2.join();

        executor.runAll();

        assertEquals(6, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Queued);
        assertTrue(received.get(2) instanceof DownloadEvent.Failed);
        assertTrue(received.get(3) instanceof DownloadEvent.Queued);
        assertTrue(received.get(5) instanceof DownloadEvent.Completed);
    }

    @Test
    public void throwingListenerIsIsolatedAndStillReceivesNextEventsManualExecutor() {
        ManualExecutor executor = new ManualExecutor();
        List<Throwable> capturedErrors = new ArrayList<>();
        DownloadEvents dispatcher = new DownloadEvents(capturedErrors::add);

        List<DownloadEvent> failingReceived = new ArrayList<>();
        DownloadListener failingListener = new DownloadListener() {
            @Override
            public void onStarted(DownloadEvent.Started event) {
                throw new RuntimeException("listener failure");
            }
            @Override
            public void onCompleted(DownloadEvent.Completed event) {
                failingReceived.add(event);
            }
        };

        List<DownloadEvent> healthyReceived = new ArrayList<>();
        DownloadListener healthyListener = new DownloadListener() {
            @Override
            public void onStarted(DownloadEvent.Started event) {
                healthyReceived.add(event);
            }
            @Override
            public void onCompleted(DownloadEvent.Completed event) {
                healthyReceived.add(event);
            }
        };

        dispatcher.register(failingListener, executor);
        dispatcher.register(healthyListener, executor);

        dispatcher.post(new DownloadEvent.Started(1, null));
        dispatcher.post(new DownloadEvent.Completed(1, null, null, "out.mp4"));

        executor.runAll();

        assertEquals(1, capturedErrors.size());
        assertEquals("listener failure", capturedErrors.get(0).getMessage());

        // Failing listener must not break drain loop; it must receive its next event
        assertEquals(1, failingReceived.size());
        assertTrue(failingReceived.get(0) instanceof DownloadEvent.Completed);

        // Healthy listener received both events
        assertEquals(2, healthyReceived.size());
    }

    @Test
    public void unregisterUsesReferenceEqualityNotObjectEquals() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});

        class ValueEqualListener implements DownloadListener {
            final List<DownloadEvent> events = new ArrayList<>();

            @Override
            public void onStarted(DownloadEvent.Started event) {
                events.add(event);
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof ValueEqualListener;
            }

            @Override
            public int hashCode() {
                return 1;
            }
        }

        ValueEqualListener listener1 = new ValueEqualListener();
        ValueEqualListener listener2 = new ValueEqualListener();
        assertTrue("Precondition: listeners are equals()", listener1.equals(listener2));

        dispatcher.register(listener1, Runnable::run);
        dispatcher.register(listener2, Runnable::run);

        // Unregistering listener1 must NOT unregister listener2
        dispatcher.unregister(listener1);

        dispatcher.post(new DownloadEvent.Started(1, null));

        assertTrue("listener1 was unregistered", listener1.events.isEmpty());
        assertEquals("listener2 must still receive events", 1, listener2.events.size());
    }

    @Test
    public void registerReplacesExistingRegistrationAndDropsPreviousPendingEvents() {
        ManualExecutor executor1 = new ManualExecutor();
        ManualExecutor executor2 = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        DownloadListener listener = new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
        };

        dispatcher.register(listener, executor1);
        dispatcher.post(new DownloadEvent.Queued(1, null));

        // Re-register listener on executor2; replaces previous registration
        dispatcher.register(listener, executor2);

        // executor1 runs; replaced registration must receive nothing
        executor1.runAll();
        assertTrue("Replaced registration must receive nothing", received.isEmpty());

        // New events post to executor2
        dispatcher.post(new DownloadEvent.Started(1, null));
        executor2.runAll();
        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Started);
    }

    @Test
    public void unregisterStopsDeliveryIncludingPendingEvents() {
        ManualExecutor executor = new ManualExecutor();
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        DownloadListener listener = new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) { received.add(event); }
            @Override
            public void onStarted(DownloadEvent.Started event) { received.add(event); }
        };

        dispatcher.register(listener, executor);

        dispatcher.post(new DownloadEvent.Queued(1, null));
        dispatcher.post(new DownloadEvent.Started(1, null));

        dispatcher.unregister(listener);

        executor.runAll();

        assertTrue("Unregistered listener must receive no events from backlog", received.isEmpty());
    }

    @Test
    public void registerFromInsideCallback() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> l2Received = new ArrayList<>();

        DownloadListener listener2 = new DownloadListener() {
            @Override
            public void onCompleted(DownloadEvent.Completed event) {
                l2Received.add(event);
            }
        };

        DownloadListener listener1 = new DownloadListener() {
            @Override
            public void onStarted(DownloadEvent.Started event) {
                dispatcher.register(listener2, Runnable::run);
            }
        };

        dispatcher.register(listener1, Runnable::run);

        dispatcher.post(new DownloadEvent.Started(1, null));
        dispatcher.post(new DownloadEvent.Completed(1, null, null, "done.mp4"));

        assertEquals(1, l2Received.size());
        assertTrue(l2Received.get(0) instanceof DownloadEvent.Completed);
    }

    @Test
    public void unregisterFromInsideCallback() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();

        DownloadListener listener = new DownloadListener() {
            @Override
            public void onQueued(DownloadEvent.Queued event) {
                received.add(event);
                dispatcher.unregister(this);
            }

            @Override
            public void onStarted(DownloadEvent.Started event) {
                received.add(event);
            }
        };

        dispatcher.register(listener, Runnable::run);

        dispatcher.post(new DownloadEvent.Queued(1, null));
        dispatcher.post(new DownloadEvent.Started(1, null));

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof DownloadEvent.Queued);
    }

    @Test
    public void terminalMarksAreCappedAtTheMostRecentThousandAndTwentyFourIds() {
        DownloadEvents dispatcher = new DownloadEvents(t -> {});
        List<DownloadEvent> received = new ArrayList<>();
        dispatcher.register(new DownloadListener() {
            @Override
            public void onProgress(DownloadEvent.Progress event) { received.add(event); }
        }, Runnable::run);

        for (int id = 0; id <= 1024; id++) {
            dispatcher.post(new DownloadEvent.Cancelled(id, null));
        }
        // 1025 ids terminated: the oldest (0) lost its mark, the next oldest (1) still has it.
        dispatcher.post(new DownloadEvent.Progress(0, null, 1, 2));
        dispatcher.post(new DownloadEvent.Progress(1, null, 1, 2));

        assertEquals(1, received.size());
        assertEquals(0, received.get(0).id());
    }
}
