/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import app.morphe.extension.crimera.downloader.engine.DownloadLog;
import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

public final class HttpTransferTest {
    @Test
    public void connectionAbortRetries() {
        assertTrue(HttpTransfer.isRetryable(
                new SocketException("Software caused connection abort")));
    }

    @Test
    public void transientDnsFlapRetries() {
        assertTrue(HttpTransfer.isRetryable(
                new UnknownHostException("Unable to resolve host \"video.twimg.com\"")));
    }

    @Test
    public void stallAndShortReadRetry() {
        assertTrue(HttpTransfer.isRetryable(new SocketTimeoutException()));
        assertTrue(HttpTransfer.isRetryable(new IOException("Short read")));
        assertTrue(HttpTransfer.isRetryable(new IOException("unexpected end of stream")));
    }

    @Test
    public void sickServersRetryDeadLinksDoNot() {
        assertTrue(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(500, "https://video.twimg.com/x.mp4")));
        assertTrue(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(429, "https://video.twimg.com/x.mp4")));
        assertFalse(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(403, "https://video.twimg.com/x.mp4")));
        assertFalse(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(404, "https://video.twimg.com/x.mp4")));
    }

    @Test
    public void destinationLossNeverRetries() {
        assertFalse(HttpTransfer.isRetryable(new FileNotFoundException()));
        assertFalse(HttpTransfer.isRetryable(new SecurityException()));
        assertFalse(HttpTransfer.isRetryable(
                new IOException(new FileNotFoundException())));
        assertFalse(HttpTransfer.isRetryable(null));
    }

    @Test
    public void unexpectedRuntimeFailuresDoNotRetry() {
        assertFalse(HttpTransfer.isRetryable(new IllegalStateException()));
    }

    @Test
    public void retryStatusBoundaries() {
        assertFalse(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(428, "https://example.com/item")));
        assertTrue(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(429, "https://example.com/item")));
        assertFalse(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(499, "https://example.com/item")));
        assertTrue(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(500, "https://example.com/item")));
        assertTrue(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(416, "https://example.com/item")));
        assertFalse(HttpTransfer.isRetryable(
                new HttpTransfer.HttpStatusException(404, "https://example.com/item")));
    }

    @Test
    public void retryCauseTypeBoundaries() {
        assertFalse(HttpTransfer.isRetryable(null));
        assertTrue(HttpTransfer.isRetryable(new IOException("transport reset")));
        assertFalse(HttpTransfer.isRetryable(new SecurityException("permission denied")));
        assertFalse(HttpTransfer.isRetryable(new FileNotFoundException("document gone")));
    }

    @Test
    public void resumedRangeMustStartAtRequestedOffsetAndReachTheRepresentationEnd() throws Exception {
        assertEquals(1_000L, HttpTransfer.validateContentRange("bytes 400-999/1000", 400, 600));
        assertEquals(1_000L, HttpTransfer.validateContentRange("BYTES 0-999/1000", 0, -1));
    }

    @Test
    public void incompleteOrMismatchedRangeIsRejectedAndNotRetried() {
        assertInvalidContentRange("bytes 399-999/1000", 400, 601);
        assertInvalidContentRange("bytes 400-998/1000", 400, 599);
        assertInvalidContentRange("bytes 400-999/1000", 400, 599);
        assertInvalidContentRange("bytes 400-999/*", 400, 600);
        assertInvalidContentRange("bytes 0-499/1000", 0, 500);
        assertInvalidContentRange(null, 400, 600);

        try {
            HttpTransfer.validateContentRange("bytes 400-999/1000", 400, 599);
            fail("Expected an inconsistent range to be rejected");
        } catch (HttpTransfer.InvalidContentRangeException expected) {
            assertFalse(HttpTransfer.isRetryable(expected));
        }
    }

    private static void assertInvalidContentRange(String header, long offset, long contentLength) {
        try {
            HttpTransfer.validateContentRange(header, offset, contentLength);
            fail("Expected invalid Content-Range to be rejected: " + header);
        } catch (HttpTransfer.InvalidContentRangeException expected) {
            // The response must be discarded rather than appended.
        }
    }

    @Test
    public void redirectClassification() {
        assertTrue(HttpTransfer.isRedirect(301));
        assertTrue(HttpTransfer.isRedirect(302));
        assertTrue(HttpTransfer.isRedirect(303));
        assertTrue(HttpTransfer.isRedirect(307));
        assertTrue(HttpTransfer.isRedirect(308));

        assertFalse(HttpTransfer.isRedirect(300));
        assertFalse(HttpTransfer.isRedirect(304));
        assertFalse(HttpTransfer.isRedirect(305));
        assertFalse(HttpTransfer.isRedirect(306));
        assertFalse(HttpTransfer.isRedirect(200));
        assertFalse(HttpTransfer.isRedirect(404));
    }

    @Test
    public void httpStatusExceptionCarriesStatusAndUrl() {
        HttpTransfer.HttpStatusException exception =
                new HttpTransfer.HttpStatusException(404, "https://example.com/missing.jpg");
        assertEquals(404, exception.status());
        assertEquals("HTTP 404 for https://example.com/missing.jpg", exception.getMessage());
    }

    @Test
    public void cancelArrivingBeforeRunEndsCancelledWithoutTouchingTheNetwork() {
        HttpTransfer transfer = new HttpTransfer(DownloadLog.NOOP, context -> false, () -> 0L);
        transfer.cancel(41);

        java.util.concurrent.atomic.AtomicInteger observed = new java.util.concurrent.atomic.AtomicInteger();
        TransferObserver observer = new TransferObserver() {
            @Override public void onWaitingForConnection() { observed.incrementAndGet(); }
            @Override public void onTransferring() { observed.incrementAndGet(); }
            @Override public void onProgress(long bytesDone, long totalBytes) { observed.incrementAndGet(); }
        };
        TransferOutcome outcome = transfer.run(
                null,
                new Reservation(null, "a.mp4", "video/mp4"),
                new app.morphe.extension.crimera.downloader.model.DownloadRequest(
                        "https://example.invalid/a.mp4", null, null, java.util.Collections.emptyList(),
                        "a.mp4", "video/mp4",
                        app.morphe.extension.crimera.downloader.model.ConflictPolicy.RENAME, null),
                41,
                observer);

        assertEquals(TransferOutcome.Kind.CANCELLED, outcome.kind());
        assertEquals(0, observed.get());
    }

    @Test
    public void offlineWaitParksAndLogsItsReasonAndActualDuration() {
        List<String> messages = new ArrayList<>();
        DownloadLog log = new DownloadLog() {
            @Override public void info(Supplier<String> message) { messages.add(message.get()); }
            @Override public void error(Supplier<String> message, Throwable cause) { }
        };
        AtomicLong elapsedRealtime = new AtomicLong(10_000);
        HttpTransfer transfer = new HttpTransfer(log, context -> false, elapsedRealtime::get);
        TransferObserver observer = new TransferObserver() {
            @Override public void onWaitingForConnection() { }
            @Override public void onTransferring() { }
            @Override public void onProgress(long bytesDone, long totalBytes) { }
        };
        Reservation reservation = new Reservation(null, "a.mp4", "video/mp4");
        DownloadRequest request = new DownloadRequest(
                "https://example.com/a.mp4", null, null, Collections.emptyList(),
                "a.mp4", "video/mp4", ConflictPolicy.RENAME, null);

        TransferOutcome parked = transfer.run(null, reservation, request, 27, observer);

        assertEquals(TransferOutcome.Kind.DEFERRED, parked.kind());
        assertEquals(1_000, parked.delayMillis());
        assertEquals(TransferOutcome.WaitReason.NETWORK_VALIDATION, parked.waitReason());
        assertTrue(messages.contains(
                "Download #27 wait started reason=validated_network_unavailable"));

        elapsedRealtime.addAndGet(10_500);
        transfer.cancel(27);
        TransferOutcome cancelled = transfer.run(null, reservation, request, 27, observer);

        assertEquals(TransferOutcome.Kind.CANCELLED, cancelled.kind());
        assertTrue(messages.contains("Download #27 wait ended reason=validated_network_unavailable"
                + " durationMs=10500 result=cancelled"));
    }
}
