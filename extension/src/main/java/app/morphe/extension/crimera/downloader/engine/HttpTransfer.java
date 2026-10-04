/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/**
 * Executes HTTP transfers into reserved documents, resuming across resets and trying fallback URLs.
 */
public final class HttpTransfer implements TransferRunner {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    // Per-read stall budget, not a total deadline. Large transfers on mobile routinely stall
    // for seconds at a time; 30s killed 20MB-class downloads on fluctuating connections.
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_REDIRECTS = 5;
    /** No HttpURLConnection constant exists for 416. */
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
    // Full passes over one URL: the first streams from zero, later ones resume from the
    // bytes already on disk. Large video variants (50-300MB) otherwise die on the first
    // reset and restart from zero on manual retry.
    // Backoff between attempts; DNS/link flaps that outlast fixed 1-2s delays used to
    // fail downloads that the next minute would have completed.
    private static final long[] RETRY_DELAYS_MS = {0, 2_000, 5_000, 15_000, 30_000, 60_000};
    private static final int MAX_ATTEMPTS = RETRY_DELAYS_MS.length;
    /** Total window an invocation keeps riding out flaps before failing closed. */
    private static final long RETRY_WINDOW_MS = 10 * 60 * 1_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    /** Cancel flags per in-flight transfer, keyed by download id. */
    private static final ConcurrentHashMap<Integer, AtomicBoolean> CANCEL_FLAGS =
            new ConcurrentHashMap<>();
    /** Live connections per in-flight transfer, so cancel unblocks a stalled read. */
    private static final ConcurrentHashMap<Integer, HttpURLConnection> ACTIVE_CONNECTIONS =
            new ConcurrentHashMap<>();

    private final DownloadLog log;

    /** Creates an HTTP transfer runner with no-op logging. */
    public HttpTransfer() {
        this(DownloadLog.NOOP);
    }

    /** Creates an HTTP transfer runner with the given diagnostic logger. */
    public HttpTransfer(DownloadLog log) {
        this.log = log != null ? log : DownloadLog.NOOP;
    }

    @Override
    public TransferOutcome run(
            Context context,
            Reservation reservation,
            DownloadRequest request,
            int id,
            TransferObserver observer
    ) {
        try {
            if (id > 0) {
                CANCEL_FLAGS.putIfAbsent(id, new AtomicBoolean(false));
            }
            if (isCancelled(id)) {
                return TransferOutcome.cancelled();
            }

            Failure failure = new Failure();
            if (saveWithAttempts(context, reservation, request.url(), id, observer, failure)) {
                return TransferOutcome.saved();
            }

            if (isCancelled(id)) {
                return TransferOutcome.cancelled();
            }

            List<String> fallbackUrls = request.fallbackUrls();
            if (fallbackUrls != null) {
                for (String fallbackUrl : fallbackUrls) {
                    if (isCancelled(id)) {
                        return TransferOutcome.cancelled();
                    }
                    Failure fallbackFailure = new Failure();
                    if (saveAttempt(context, reservation, fallbackUrl, id, 0, new TransferState(), observer, fallbackFailure)) {
                        return TransferOutcome.saved();
                    }
                    if (fallbackFailure.cause != null) {
                        failure.cause = fallbackFailure.cause;
                    }
                }
            }

            if (isCancelled(id)) {
                return TransferOutcome.cancelled();
            }

            return TransferOutcome.failed(failure.cause);
        } finally {
            if (id > 0) {
                clearCancelFlag(id);
            }
        }
    }

    @Override
    public void cancel(int id) {
        if (id <= 0) return;
        AtomicBoolean flag = CANCEL_FLAGS.computeIfAbsent(id, k -> new AtomicBoolean(false));
        flag.set(true);
        HttpURLConnection connection = ACTIVE_CONNECTIONS.get(id);
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static boolean isCancelled(int id) {
        if (id <= 0) return false;
        AtomicBoolean flag = CANCEL_FLAGS.get(id);
        return flag != null && flag.get();
    }

    private static void clearCancelFlag(int id) {
        if (id <= 0) return;
        CANCEL_FLAGS.remove(id);
    }

    /**
     * Streams the URL with resume across transient resets. Later attempts continue from the
     * bytes already on disk via Range; servers without range support restart from zero.
     */
    private boolean saveWithAttempts(
            Context context,
            Reservation reservation,
            String url,
            int id,
            TransferObserver observer,
            Failure failure
    ) {
        TransferState state = new TransferState();
        long deadline = SystemClock.elapsedRealtime() + RETRY_WINDOW_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            final int attemptNumber = attempt;
            if (id > 0 && isCancelled(id)) return false;
            if (attempt > 1 && !sleepCancellable(RETRY_DELAYS_MS[attempt - 1], id)) {
                return false;
            }
            // Flap riding: wait for a validated path instead of burning an attempt
            // into a dead link. Offline-at-tap lands here too, not in instant failure.
            if (!waitForOnline(context, id, observer, deadline)) {
                if (failure.cause == null) {
                    failure.cause = new UnknownHostException("No validated network");
                }
                return false;
            }
            if (id > 0 && isCancelled(id)) return false;
            long resumeFrom = attempt == 1 ? 0 : currentSize(context, reservation);
            if (state.expectedTotal > 0 && resumeFrom >= state.expectedTotal) {
                if (resumeFrom == state.expectedTotal) {
                    return true;
                }
                resumeFrom = 0;
            }
            Failure attemptFailure = new Failure();
            if (saveAttempt(context, reservation, url, id, resumeFrom, state, observer, attemptFailure)) {
                return true;
            }
            failure.cause = attemptFailure.cause;
            if (!isRetryable(attemptFailure.cause)) return false;
            log.info(() -> "Retrying download " + reservation.fileName()
                    + " (attempt " + attemptNumber + ")");
        }
        return false;
    }

    /** Chunked sleep that stays responsive to Cancel. False when cancelled. */
    private static boolean sleepCancellable(long totalMs, int id) {
        long end = SystemClock.elapsedRealtime() + totalMs;
        while (true) {
            if (id > 0 && isCancelled(id)) return false;
            long remaining = end - SystemClock.elapsedRealtime();
            if (remaining <= 0) return true;
            try {
                Thread.sleep(Math.min(remaining, 1_000));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * Validated internet path. Fail-open (assume online) when the service is missing,
     * so the attempt itself becomes the probe.
     */
    private static boolean isOnline(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            Network active = manager.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = manager.getNetworkCapabilities(active);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    /**
     * Waits for a validated path until the deadline, so transient DNS/link flaps ride
     * out instead of failing. False on cancel or deadline expiry.
     */
    private static boolean waitForOnline(
            Context context, int id, TransferObserver observer, long deadline) {
        if (isOnline(context)) return true;
        observer.onWaitingForConnection();
        while (SystemClock.elapsedRealtime() < deadline) {
            if (id > 0 && isCancelled(id)) return false;
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (isOnline(context)) return true;
        }
        return isOnline(context);
    }

    /** Transient network failures resume; dead links and lost folders fail closed. */
    static boolean isRetryable(@Nullable Throwable cause) {
        if (cause == null) return false;
        if (DestinationLoss.matches(cause)) return false;
        if (cause instanceof HttpStatusException) {
            HttpStatusException http = (HttpStatusException) cause;
            int status = http.status();
            return status == HTTP_RANGE_NOT_SATISFIABLE
                    || status == 429
                    || status >= 500;
        }
        return cause instanceof IOException;
    }

    private boolean saveAttempt(
            Context context,
            Reservation reservation,
            String url,
            int id,
            long resumeFrom,
            TransferState state,
            TransferObserver observer,
            Failure failure
    ) {
        HttpURLConnection connection = null;
        observer.onTransferring();
        try {
            connection = openConnection(url, resumeFrom);
            trackConnection(id, connection);
            int status = connection.getResponseCode();
            boolean resumed = resumeFrom > 0 && status == HttpURLConnection.HTTP_PARTIAL;
            long base = resumed ? resumeFrom : 0;
            long remaining = connection.getContentLengthLong();
            if (remaining >= 0) {
                state.expectedTotal = base + remaining;
            } else if (base == 0) {
                state.expectedTotal = -1;
            }

            OutputStream output;
            if (resumed) {
                try {
                    output = context.getContentResolver().openOutputStream(reservation.documentUri(), "wa");
                } catch (IOException | RuntimeException appendFailure) {
                    // Provider cannot append: redo this attempt from zero on a fresh connection.
                    untrackConnection(id, connection);
                    connection.disconnect();
                    connection = openConnection(url, 0);
                    trackConnection(id, connection);
                    remaining = connection.getContentLengthLong();
                    state.expectedTotal = remaining >= 0 ? remaining : -1;
                    output = context.getContentResolver().openOutputStream(reservation.documentUri(), "wt");
                }
            } else {
                output = context.getContentResolver().openOutputStream(reservation.documentUri(), "wt");
            }
            if (output == null) throw new IOException("Could not open " + reservation.fileName());

            final HttpURLConnection finalConnection = connection;
            long total = base;
            try (InputStream input = new BufferedInputStream(finalConnection.getInputStream());
                 OutputStream body = output) {
                byte[] buffer = new byte[64 * 1024];
                long lastUpdate = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (id > 0 && isCancelled(id)) return false;
                    body.write(buffer, 0, read);
                    total += read;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 200) {
                        observer.onProgress(total, state.expectedTotal >= 0 ? state.expectedTotal : -1);
                        lastUpdate = now;
                    }
                }
                body.flush();
            }

            // A cleanly-closed short stream used to report success, leaving a truncated
            // file behind. Fail closed so the next attempt resumes instead.
            if (state.expectedTotal > 0 && total != state.expectedTotal) {
                throw new IOException("Short read for " + reservation.fileName()
                        + ": got " + total + " of " + state.expectedTotal + " bytes");
            }

            return true;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof HttpStatusException) {
                HttpStatusException http = (HttpStatusException) exception;
                if (http.status() == HTTP_RANGE_NOT_SATISFIABLE) {
                    if (state.expectedTotal > 0
                            && currentSize(context, reservation) >= state.expectedTotal) {
                        // Stale size finished the file between attempts.
                        return true;
                    }
                    // Server file shrank: drop the stale partial so the retry restarts fresh.
                    truncate(context, reservation);
                    state.expectedTotal = -1;
                }
            }
            failure.cause = exception;
            log.error(() -> "Failed to download " + reservation.fileName(), exception);
            return false;
        } finally {
            untrackConnection(id, connection);
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    /** Bytes already stored in the reserved document; zero when the provider stays silent. */
    private static long currentSize(Context context, Reservation reservation) {
        try (Cursor cursor = context.getContentResolver().query(
                reservation.documentUri(), new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return Math.max(0, cursor.getLong(0));
            }
        } catch (RuntimeException ignored) {
            // Unknown size restarts from zero; never fail the transfer on a probe.
        }
        return 0;
    }

    /** Empties a reserved document so a stale partial cannot poison the next attempt. */
    private void truncate(Context context, Reservation reservation) {
        try (OutputStream output = context.getContentResolver()
                .openOutputStream(reservation.documentUri(), "wt")) {
            // Opening with "wt" truncates; nothing to write.
        } catch (IOException | RuntimeException exception) {
            log.error(() -> "Failed to truncate " + reservation.fileName(), exception);
        }
    }

    private static void trackConnection(int id, HttpURLConnection connection) {
        if (id > 0 && connection != null) {
            ACTIVE_CONNECTIONS.put(id, connection);
        }
    }

    private static void untrackConnection(int id, HttpURLConnection connection) {
        if (id > 0 && connection != null) {
            ACTIVE_CONNECTIONS.remove(id, connection);
        }
    }

    private static HttpURLConnection openConnection(String address, long resumeFrom) throws IOException {
        String current = address;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = openSingleConnection(current, resumeFrom);
            int status = connection.getResponseCode();
            if (!isRedirect(status)) {
                if (status < 200 || status >= 300) {
                    connection.disconnect();
                    throw new HttpStatusException(status, current);
                }
                return connection;
            }

            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null) {
                throw new IOException("Redirect without a location for " + current);
            }
            current = new URL(new URL(current), location).toString();
        }
        throw new IOException("Too many redirects for " + address);
    }

    private static HttpURLConnection openSingleConnection(String address, long resumeFrom) throws IOException {
        URLConnection connection = new URL(address).openConnection();
        if (!(connection instanceof HttpURLConnection)) {
            throw new IOException("Unsupported download URL " + address);
        }
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        httpConnection.setInstanceFollowRedirects(true);
        httpConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        httpConnection.setReadTimeout(READ_TIMEOUT_MS);
        httpConnection.setRequestProperty("User-Agent", USER_AGENT);
        if (resumeFrom > 0) {
            httpConnection.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
        }
        httpConnection.connect();
        return httpConnection;
    }

    static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    /** Bytes already written, kept across attempts so a reset resumes instead of restarting. */
    private static final class TransferState {
        long expectedTotal = -1;
    }

    /** HTTP status failures, so retries distinguish dead links (4xx) from sick servers (5xx). */
    static final class HttpStatusException extends IOException {
        private final int status;

        HttpStatusException(int status, String url) {
            super("HTTP " + status + " for " + url);
            this.status = status;
        }

        int status() {
            return status;
        }
    }

    /** Last transfer exception. */
    private static final class Failure {
        Throwable cause;
    }
}
