/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import app.morphe.extension.crimera.downloader.engine.DestinationWriter;
import app.morphe.extension.crimera.downloader.engine.Reservation;
import app.morphe.extension.crimera.downloader.engine.TransferObserver;
import app.morphe.extension.crimera.downloader.engine.TransferOutcome;
import app.morphe.extension.crimera.downloader.engine.TransferRunner;
import app.morphe.extension.crimera.downloader.events.DownloadEvent;
import app.morphe.extension.crimera.downloader.events.DownloadEvents;
import app.morphe.extension.crimera.downloader.events.DownloadListener;
import app.morphe.extension.crimera.downloader.events.FailureReason;
import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;
import app.morphe.extension.crimera.downloader.model.EnqueueState;

public final class DownloadEngineTest {
    private FakeDestinationWriter destination;
    private FakeTransferRunner transfer;
    private DownloadEvents events;
    private RecordingListener listener;
    private QueuedExecutor queuedExecutor;
    private AtomicLong clock;
    private DownloadEngine engine;

    @Before
    public void setUp() {
        destination = new FakeDestinationWriter();
        transfer = new FakeTransferRunner();
        events = new DownloadEvents(t -> {});
        listener = new RecordingListener();
        events.register(listener, Runnable::run);
        queuedExecutor = new QueuedExecutor();
        clock = new AtomicLong(1000L);
        engine = new DownloadEngine(null, destination, transfer, events, queuedExecutor, clock::get);
    }

    private static DownloadRequest newRequest(String label) {
        return new DownloadRequest(
                "https://example.com/video.mp4",
                null,
                null,
                Collections.emptyList(),
                "video.mp4",
                "video/mp4",
                ConflictPolicy.OVERWRITE,
                label
        );
    }

    @Test
    public void successfulEventSequenceAndCompletedContents() {
        DownloadRequest request = newRequest("user-label");
        EnqueueResult result = engine.enqueue(request);

        assertEquals(EnqueueState.QUEUED, result.state());
        assertNotNull(result.handle());
        assertNull(result.cause());
        int id = result.handle().id();
        assertEquals(DownloadHandle.State.QUEUED, result.handle().state());

        assertEquals(1, listener.events.size());
        assertTrue(listener.events.get(0) instanceof DownloadEvent.Queued);
        assertEquals(id, listener.events.get(0).id());
        assertEquals("user-label", listener.events.get(0).label());

        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.COMPLETED, result.handle().state());
        assertEquals(3, listener.events.size());

        assertTrue(listener.events.get(1) instanceof DownloadEvent.Started);
        assertEquals(id, listener.events.get(1).id());
        assertEquals("user-label", listener.events.get(1).label());

        assertTrue(listener.events.get(2) instanceof DownloadEvent.Completed);
        DownloadEvent.Completed completed = (DownloadEvent.Completed) listener.events.get(2);
        assertEquals(id, completed.id());
        assertEquals("user-label", completed.label());
        assertEquals("video.mp4", completed.fileName());
        assertEquals("video/mp4", completed.mimeType());

        assertTrue("Saved transfer must not call discard", destination.discardCalls.isEmpty());
    }

    @Test
    public void skippedPostingNothing() {
        destination.returnNullReservation = true;
        DownloadRequest request = newRequest("skip-me");
        EnqueueResult result = engine.enqueue(request);

        assertEquals(EnqueueState.SKIPPED, result.state());
        assertNull(result.handle());
        assertNull(result.cause());
        assertTrue(listener.events.isEmpty());
        assertEquals(0, queuedExecutor.size());
        assertTrue(destination.discardCalls.isEmpty());
    }

    @Test
    public void reserveIOExceptionYieldsFailedWithoutEvents() {
        IOException diskError = new IOException("disk full");
        destination.reserveException = diskError;
        DownloadRequest request = newRequest("io-error");

        EnqueueResult result = engine.enqueue(request);

        assertEquals(EnqueueState.FAILED, result.state());
        assertNull(result.handle());
        assertSame(diskError, result.cause());
        assertTrue(listener.events.isEmpty());
        assertEquals(0, queuedExecutor.size());
    }

    @Test
    public void reserveSecurityExceptionYieldsDestinationLostWithoutEvents() {
        SecurityException secError = new SecurityException("permission revoked");
        destination.reserveException = secError;
        DownloadRequest request = newRequest("sec-error");

        EnqueueResult result = engine.enqueue(request);

        assertEquals(EnqueueState.DESTINATION_LOST, result.state());
        assertNull(result.handle());
        assertSame(secError, result.cause());
        assertTrue(listener.events.isEmpty());
        assertEquals(0, queuedExecutor.size());
    }

    @Test
    public void transferOutcomeCancelledCallsDiscardAndPostsCancelled() {
        transfer.cannedOutcome = TransferOutcome.cancelled();
        DownloadRequest request = newRequest("cancel-test");
        EnqueueResult result = engine.enqueue(request);
        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.CANCELLED, result.handle().state());
        assertEquals(1, destination.discardCalls.size());
        assertEquals(3, listener.events.size());
        assertTrue(listener.events.get(0) instanceof DownloadEvent.Queued);
        assertTrue(listener.events.get(1) instanceof DownloadEvent.Started);
        assertTrue(listener.events.get(2) instanceof DownloadEvent.Cancelled);
        assertEquals("cancel-test", listener.events.get(2).label());
    }

    @Test
    public void transferOutcomeFailedUnknownCallsDiscardAndReportsRetriable() {
        Throwable cause = new RuntimeException("connection reset");
        transfer.cannedOutcome = TransferOutcome.failed(cause);
        DownloadRequest request = newRequest("fail-unknown");
        EnqueueResult result = engine.enqueue(request);
        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.FAILED, result.handle().state());
        assertEquals(1, destination.discardCalls.size());
        assertEquals(3, listener.events.size());
        assertTrue(listener.events.get(2) instanceof DownloadEvent.Failed);
        DownloadEvent.Failed failed = (DownloadEvent.Failed) listener.events.get(2);
        assertEquals(FailureReason.UNKNOWN, failed.reason());
        assertTrue("Unknown failure must be retriable", failed.retriable());
    }

    @Test
    public void transferOutcomeFailedLostDestinationNotRetriable() {
        Throwable cause = new SecurityException("SAF permission revoked");
        transfer.cannedOutcome = TransferOutcome.failed(cause);
        DownloadRequest request = newRequest("fail-dest-lost");
        EnqueueResult result = engine.enqueue(request);
        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.FAILED, result.handle().state());
        assertEquals(1, destination.discardCalls.size());
        assertEquals(3, listener.events.size());
        assertTrue(listener.events.get(2) instanceof DownloadEvent.Failed);
        DownloadEvent.Failed failed = (DownloadEvent.Failed) listener.events.get(2);
        assertEquals(FailureReason.DESTINATION_LOST, failed.reason());
        assertFalse("Destination lost must not be retriable", failed.retriable());
    }

    @Test
    public void transferOutcomeFailedLostConnectionIsRetriable() {
        Throwable cause = new UnknownHostException("dns lookup failed");
        transfer.cannedOutcome = TransferOutcome.failed(cause);
        DownloadRequest request = newRequest("fail-no-conn");
        EnqueueResult result = engine.enqueue(request);
        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.FAILED, result.handle().state());
        assertEquals(1, destination.discardCalls.size());
        assertEquals(3, listener.events.size());
        assertTrue(listener.events.get(2) instanceof DownloadEvent.Failed);
        DownloadEvent.Failed failed = (DownloadEvent.Failed) listener.events.get(2);
        assertEquals(FailureReason.NO_CONNECTION, failed.reason());
        assertTrue("No connection failure must be retriable", failed.retriable());
    }

    @Test
    public void transferThrowsRuntimeExceptionTreatedAsFailed() {
        transfer.runException = new RuntimeException("crash in transfer");
        DownloadRequest request = newRequest("throw-test");
        EnqueueResult result = engine.enqueue(request);
        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.FAILED, result.handle().state());
        assertEquals(1, destination.discardCalls.size());
        assertEquals(3, listener.events.size());
        assertTrue(listener.events.get(2) instanceof DownloadEvent.Failed);
        DownloadEvent.Failed failed = (DownloadEvent.Failed) listener.events.get(2);
        assertEquals(FailureReason.UNKNOWN, failed.reason());
        assertTrue(failed.retriable());
    }

    @Test
    public void exactlyOneTerminalEventInEveryCase() {
        TransferOutcome[] outcomes = {
                TransferOutcome.saved(),
                TransferOutcome.cancelled(),
                TransferOutcome.failed(new RuntimeException()),
                TransferOutcome.failed(new SecurityException())
        };

        for (TransferOutcome outcome : outcomes) {
            listener.events.clear();
            transfer.cannedOutcome = outcome;
            transfer.runException = null;
            engine.enqueue(newRequest("terminal-test"));
            queuedExecutor.runAll();

            int terminalCount = 0;
            for (DownloadEvent e : listener.events) {
                if (e.isTerminal()) {
                    terminalCount++;
                }
            }
            assertEquals(1, terminalCount);
        }

        // Transfer throws
        listener.events.clear();
        transfer.runException = new RuntimeException("throw");
        engine.enqueue(newRequest("terminal-throw"));
        queuedExecutor.runAll();
        int terminalCount = 0;
        for (DownloadEvent e : listener.events) {
            if (e.isTerminal()) {
                terminalCount++;
            }
        }
        assertEquals(1, terminalCount);

        // Executor rejects
        listener.events.clear();
        transfer.runException = null;
        queuedExecutor.reject = true;
        engine.enqueue(newRequest("terminal-reject"));
        queuedExecutor.reject = false;
        terminalCount = 0;
        for (DownloadEvent e : listener.events) {
            if (e.isTerminal()) {
                terminalCount++;
            }
        }
        assertEquals(1, terminalCount);
    }

    @Test
    public void cancelForwardingRules() {
        DownloadRequest request = newRequest("cancel-fwd");
        EnqueueResult result = engine.enqueue(request);
        int id = result.handle().id();

        // 1. Cancel while queued
        assertTrue(engine.cancel(id));
        assertTrue(transfer.cancelledIds.contains(id));

        // 2. Run transfer to completion
        queuedExecutor.runAll();
        assertEquals(DownloadHandle.State.CANCELLED, result.handle().state());

        // 3. Cancel after finished must NOT forward to transfer
        transfer.cancelledIds.clear();
        assertFalse(engine.cancel(id));
        assertFalse("Stale cancel for finished download must not reach transfer", transfer.cancelledIds.contains(id));

        // 4. Cancel for unknown id must NOT forward
        transfer.cancelledIds.clear();
        assertFalse(engine.cancel(9999));
        assertFalse("Cancel for unknown id must not reach transfer", transfer.cancelledIds.contains(9999));
    }

    @Test
    public void cancelWhileRunningForwardsToTransfer() {
        DownloadEngine inlineEngine = new DownloadEngine(
                null, destination, transfer, events, Runnable::run, clock::get
        );
        transfer.observerScript = obs -> {
            // While running on the runner
            inlineEngine.cancel(1);
        };
        transfer.cannedOutcome = TransferOutcome.cancelled();

        EnqueueResult result = inlineEngine.enqueue(1, newRequest("running-cancel"));
        assertTrue(transfer.cancelledIds.contains(1));
        assertEquals(DownloadHandle.State.CANCELLED, result.handle().state());
    }

    @Test
    public void cancelArrivingWhileQueuedEndsInCancelled() {
        DownloadRequest request = newRequest("queued-cancel");
        EnqueueResult result = engine.enqueue(request);
        int id = result.handle().id();

        engine.cancel(id);
        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.CANCELLED, result.handle().state());
        assertTrue(listener.events.get(listener.events.size() - 1) instanceof DownloadEvent.Cancelled);
    }

    @Test
    public void waitingStartedRule() {
        // Transferring without a preceding wait posts NO extra Started
        transfer.observerScript = obs -> {
            obs.onTransferring();
            obs.onTransferring();
        };
        engine.enqueue(newRequest("no-wait"));
        queuedExecutor.runAll();

        long startedCount = listener.events.stream().filter(e -> e instanceof DownloadEvent.Started).count();
        assertEquals("Started must only be posted once when no Waiting occurred", 1, startedCount);

        // Waiting then transferring posts one extra Started
        listener.events.clear();
        transfer.observerScript = obs -> {
            obs.onWaitingForConnection();
            obs.onTransferring();
            // A second transferring without another wait does not post
            obs.onTransferring();
        };
        engine.enqueue(newRequest("with-wait"));
        queuedExecutor.runAll();

        List<String> eventNames = new ArrayList<>();
        for (DownloadEvent e : listener.events) {
            if (e instanceof DownloadEvent.Started) eventNames.add("Started");
            else if (e instanceof DownloadEvent.Waiting) eventNames.add("Waiting");
        }
        assertEquals(List.of("Started", "Waiting", "Started"), eventNames);
    }

    @Test
    public void progressThrottlingAndThrottleForgettingId() {
        transfer.observerScript = obs -> {
            obs.onProgress(10, 100);
            clock.addAndGet(10); // only 10ms passed, same percent
            obs.onProgress(10, 100); // throttled
            clock.addAndGet(300); // 310ms passed
            obs.onProgress(15, 100); // emitted
        };
        EnqueueResult res1 = engine.enqueue(1, newRequest("throttle-test"));
        queuedExecutor.runAll();

        long progressCount = listener.events.stream().filter(e -> e instanceof DownloadEvent.Progress).count();
        assertEquals(2, progressCount);

        // Retry on same id at the exact same clock and percent:
        // Because throttle.forget(id) was called, the first progress MUST emit.
        listener.events.clear();
        transfer.observerScript = obs -> {
            obs.onProgress(15, 100);
        };
        engine.enqueue(1, newRequest("retry-throttle"));
        queuedExecutor.runAll();

        long retryProgressCount = listener.events.stream().filter(e -> e instanceof DownloadEvent.Progress).count();
        assertEquals("Throttle must forget id upon completion so first progress emits", 1, retryProgressCount);
    }

    @Test
    public void retryReusingSameIdDeliversCompleteSecondLifecycle() {
        transfer.cannedOutcome = TransferOutcome.failed(new IOException("fail 1"));
        DownloadRequest request = newRequest("retry-cycle");

        engine.enqueue(42, request);
        queuedExecutor.runAll();

        assertEquals(3, listener.events.size());
        assertTrue(listener.events.get(0) instanceof DownloadEvent.Queued);
        assertTrue(listener.events.get(1) instanceof DownloadEvent.Started);
        assertTrue(listener.events.get(2) instanceof DownloadEvent.Failed);

        transfer.cannedOutcome = TransferOutcome.saved();
        engine.retry(42, request);
        queuedExecutor.runAll();

        assertEquals(6, listener.events.size());
        assertTrue(listener.events.get(3) instanceof DownloadEvent.Queued);
        assertTrue(listener.events.get(4) instanceof DownloadEvent.Started);
        assertTrue(listener.events.get(5) instanceof DownloadEvent.Completed);
    }

    @Test
    public void enqueueOfActiveIdThrowsAndRetrySwallowsIt() {
        DownloadRequest request = newRequest("active-conflict");
        engine.enqueue(10, request);

        // Still queued in queuedExecutor
        try {
            engine.enqueue(10, request);
            fail("Expected IllegalStateException for already active id");
        } catch (IllegalStateException expected) {
            // Expected
        }

        // Retry must catch and swallow IllegalStateException
        engine.retry(10, request);
    }

    @Test
    public void rejectingExecutorDiscardsReservationAndPostsFailed() {
        queuedExecutor.reject = true;
        DownloadRequest request = newRequest("reject-test");

        EnqueueResult result = engine.enqueue(request);

        assertEquals(EnqueueState.FAILED, result.state());
        assertNull(result.handle());
        assertTrue(result.cause() instanceof RejectedExecutionException);

        assertEquals(1, destination.discardCalls.size());

        assertEquals(2, listener.events.size());
        assertTrue(listener.events.get(0) instanceof DownloadEvent.Queued);
        assertTrue(listener.events.get(1) instanceof DownloadEvent.Failed);
        DownloadEvent.Failed failed = (DownloadEvent.Failed) listener.events.get(1);
        assertEquals(FailureReason.UNKNOWN, failed.reason());
        assertTrue(failed.retriable());

        int id = listener.events.get(0).id();
        assertNotNull(engine.handle(id));
        assertEquals(DownloadHandle.State.FAILED, engine.handle(id).state());
        assertNotNull(engine.requestFor(id));
    }

    @Test
    public void requestForRetainsRecentAndEvictsAfter256LaterRequests() {
        transfer.cannedOutcome = TransferOutcome.failed(new RuntimeException("failed"));
        DownloadRequest request1 = newRequest("first-request");
        engine.enqueue(1, request1);
        queuedExecutor.runAll();

        assertSame(request1, engine.requestFor(1));
        assertNotNull(engine.handle(1));
        assertEquals(DownloadHandle.State.FAILED, engine.handle(1).state());

        // Enqueue 256 more requests (ids 2 to 257)
        for (int i = 2; i <= 257; i++) {
            engine.enqueue(i, newRequest("req-" + i));
            queuedExecutor.runAll();
        }

        // ID 1 should be evicted now (> 256 later requests)
        assertNull("Oldest request must be evicted after 256 later requests", engine.requestFor(1));
        assertNull("Oldest handle must be evicted after 256 later requests", engine.handle(1));

        // ID 2 through 257 must still exist
        assertNotNull("Request 2 must not be evicted", engine.requestFor(2));
        assertNotNull("Request 257 must not be evicted", engine.requestFor(257));
    }

    @Test
    public void handleStatesAlongTheWay() {
        DownloadRequest request = newRequest("state-trace");
        EnqueueResult result = engine.enqueue(request);
        DownloadHandle handle = result.handle();
        assertNotNull(handle);

        assertEquals(DownloadHandle.State.QUEUED, handle.state());

        // Run until observer starts
        transfer.observerScript = obs -> {
            assertEquals(DownloadHandle.State.RUNNING, handle.state());
        };

        queuedExecutor.runAll();
        assertEquals(DownloadHandle.State.COMPLETED, handle.state());

        // Cancel via handle
        DownloadRequest req2 = newRequest("handle-cancel");
        EnqueueResult res2 = engine.enqueue(req2);
        DownloadHandle h2 = res2.handle();
        h2.cancel();
        assertTrue(transfer.cancelledIds.contains(h2.id()));
    }

    @Test
    public void idsPositiveAndIncreasingAndNonPositiveRestartsAtOne() {
        EnqueueResult r1 = engine.enqueue(newRequest("r1"));
        EnqueueResult r2 = engine.enqueue(newRequest("r2"));
        EnqueueResult r3 = engine.enqueue(newRequest("r3"));

        assertEquals(1, r1.handle().id());
        assertEquals(2, r2.handle().id());
        assertEquals(3, r3.handle().id());

        try {
            engine.enqueue(0, newRequest("zero"));
            fail("Expected IllegalArgumentException for id 0");
        } catch (IllegalArgumentException expected) {
        }

        try {
            engine.enqueue(-5, newRequest("neg"));
            fail("Expected IllegalArgumentException for negative id");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void completedCarriesTheReservedNameNotTheRequestedOne() {
        destination.cannedReservation = new Reservation(null, "video_1.mp4", "video/mp4");
        engine.enqueue(newRequest("renamed"));
        queuedExecutor.runAll();

        DownloadEvent.Completed completed = (DownloadEvent.Completed) listener.events.get(2);
        assertEquals("video_1.mp4", completed.fileName());
    }

    @Test
    public void activeIdIsRefusedBeforeAnythingIsReserved() {
        engine.enqueue(10, newRequest("first"));
        assertEquals(1, destination.reserveCalls.size());

        try {
            engine.enqueue(10, newRequest("second"));
            fail("Expected IllegalStateException for already active id");
        } catch (IllegalStateException expected) {
        }
        assertEquals("a refused enqueue must not reserve (it could create a renamed file)",
                1, destination.reserveCalls.size());
    }

    @Test
    public void rejectedExecutorReleasesTheId() {
        queuedExecutor.reject = true;
        assertEquals(EnqueueState.FAILED, engine.enqueue(5, newRequest("rejected")).state());

        queuedExecutor.reject = false;
        assertEquals(EnqueueState.QUEUED, engine.enqueue(5, newRequest("retried")).state());
    }

    @Test
    public void nullTransferOutcomeIsAFailureNotACrash() {
        transfer.cannedOutcome = null;
        EnqueueResult result = engine.enqueue(newRequest("null-outcome"));
        queuedExecutor.runAll();

        assertEquals(DownloadHandle.State.FAILED, result.handle().state());
        assertTrue(listener.events.get(listener.events.size() - 1) instanceof DownloadEvent.Failed);
    }

    @Test
    public void reusingAnIdRefreshesItsPlaceInTheRecentRequests() {
        for (int i = 1; i <= 256; i++) {
            engine.enqueue(i, newRequest("req-" + i));
            queuedExecutor.runAll();
        }
        engine.enqueue(1, newRequest("reused"));
        queuedExecutor.runAll();

        engine.enqueue(257, newRequest("newest"));
        queuedExecutor.runAll();

        assertNotNull("a reused id is the newest entry, so it must outlive older ones", engine.requestFor(1));
        assertNull("the oldest untouched id is the one evicted", engine.requestFor(2));
    }

    static final class FakeDestinationWriter implements DestinationWriter {
        Reservation cannedReservation = new Reservation(null, "video.mp4", "video/mp4");
        Throwable reserveException = null;
        boolean returnNullReservation = false;
        final List<DownloadRequest> reserveCalls = new ArrayList<>();
        final List<Reservation> discardCalls = new ArrayList<>();

        @Override
        public Reservation reserve(Context context, DownloadRequest request) throws IOException {
            reserveCalls.add(request);
            if (reserveException instanceof IOException) {
                throw (IOException) reserveException;
            } else if (reserveException instanceof RuntimeException) {
                throw (RuntimeException) reserveException;
            } else if (reserveException != null) {
                throw new RuntimeException(reserveException);
            }
            if (returnNullReservation) {
                return null;
            }
            return cannedReservation;
        }

        @Override
        public void discard(Context context, Reservation reservation) {
            discardCalls.add(reservation);
        }
    }

    static final class FakeTransferRunner implements TransferRunner {
        TransferOutcome cannedOutcome = TransferOutcome.saved();
        RuntimeException runException = null;
        Consumer<TransferObserver> observerScript = null;
        final List<Integer> runCalls = new ArrayList<>();
        final Set<Integer> cancelledIds = new HashSet<>();

        @Override
        public TransferOutcome run(Context context, Reservation reservation, DownloadRequest request, int id, TransferObserver observer) {
            runCalls.add(id);
            if (cancelledIds.contains(id)) {
                return TransferOutcome.cancelled();
            }
            if (runException != null) {
                throw runException;
            }
            if (observerScript != null) {
                observerScript.accept(observer);
            }
            if (cancelledIds.contains(id)) {
                return TransferOutcome.cancelled();
            }
            return cannedOutcome;
        }

        @Override
        public void cancel(int id) {
            cancelledIds.add(id);
        }
    }

    static final class RecordingListener implements DownloadListener {
        final List<DownloadEvent> events = new ArrayList<>();

        @Override
        public void onQueued(DownloadEvent.Queued event) { events.add(event); }
        @Override
        public void onStarted(DownloadEvent.Started event) { events.add(event); }
        @Override
        public void onWaiting(DownloadEvent.Waiting event) { events.add(event); }
        @Override
        public void onProgress(DownloadEvent.Progress event) { events.add(event); }
        @Override
        public void onCompleted(DownloadEvent.Completed event) { events.add(event); }
        @Override
        public void onFailed(DownloadEvent.Failed event) { events.add(event); }
        @Override
        public void onCancelled(DownloadEvent.Cancelled event) { events.add(event); }
    }

    static final class QueuedExecutor implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();
        boolean reject = false;

        @Override
        public void execute(Runnable command) {
            if (reject) {
                throw new RejectedExecutionException("rejected");
            }
            queue.add(command);
        }

        public void runAll() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }

        public int size() {
            return queue.size();
        }
    }
}
