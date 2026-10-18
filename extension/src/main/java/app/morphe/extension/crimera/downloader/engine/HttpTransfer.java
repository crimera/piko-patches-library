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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CONTENT_RANGE_PATTERN =
            Pattern.compile("(?i)^bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)$");
    // Full passes over one URL: the first streams from zero, later ones resume from the
    // bytes already on disk. Large video variants (50-300MB) otherwise die on the first
    // reset and restart from zero on manual retry.
    // Backoff between attempts; DNS/link flaps that outlast fixed 1-2s delays used to
    // fail downloads that the next minute would have completed.
    private static final long[] RETRY_DELAYS_MS = {0, 2_000, 5_000, 15_000, 30_000, 60_000};
    private static final int MAX_ATTEMPTS = RETRY_DELAYS_MS.length;
    /** Total window an invocation keeps riding out flaps before failing closed. */
    private static final long RETRY_WINDOW_MS = 10 * 60 * 1_000;
    private static final long NETWORK_POLL_INTERVAL_MS = 1_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    /** Cancel flags per in-flight transfer, keyed by download id. */
    private static final ConcurrentHashMap<Integer, AtomicBoolean> CANCEL_FLAGS =
            new ConcurrentHashMap<>();
    /** Live connections per in-flight transfer, so cancel unblocks a stalled read. */
    private static final ConcurrentHashMap<Integer, HttpURLConnection> ACTIVE_CONNECTIONS =
            new ConcurrentHashMap<>();
    /** Retry state survives a parked invocation until the engine schedules it again. */
    private static final ConcurrentHashMap<Integer, TransferSession> SESSIONS =
            new ConcurrentHashMap<>();

    private final DownloadLog log;
    private final NetworkAvailability networkAvailability;
    private final LongSupplier elapsedRealtime;

    /** Creates an HTTP transfer runner with no-op logging. */
    public HttpTransfer() {
        this(DownloadLog.NOOP);
    }

    /** Creates an HTTP transfer runner with the given diagnostic logger. */
    public HttpTransfer(DownloadLog log) {
        this(log, HttpTransfer::isOnline, SystemClock::elapsedRealtime);
    }

    HttpTransfer(DownloadLog log, NetworkAvailability networkAvailability, LongSupplier elapsedRealtime) {
        this.log = log != null ? log : DownloadLog.NOOP;
        this.networkAvailability = Objects.requireNonNull(networkAvailability, "networkAvailability");
        this.elapsedRealtime = Objects.requireNonNull(elapsedRealtime, "elapsedRealtime");
    }

    @Override
    public TransferOutcome run(
            Context context,
            Reservation reservation,
            DownloadRequest request,
            int id,
            TransferObserver observer
    ) {
        if (id > 0) {
            CANCEL_FLAGS.putIfAbsent(id, new AtomicBoolean(false));
        }
        TransferSession session = SESSIONS.computeIfAbsent(
                id, ignored -> new TransferSession(elapsedRealtime.getAsLong() + RETRY_WINDOW_MS));
        boolean deferred = false;
        String terminalResult = "failed";
        try {
            if (isCancelled(id)) {
                terminalResult = "cancelled";
                return TransferOutcome.cancelled();
            }
            TransferOutcome outcome = runSession(context, reservation, request, id, observer, session);
            deferred = outcome.kind() == TransferOutcome.Kind.DEFERRED;
            terminalResult = outcome.kind() == TransferOutcome.Kind.SAVED ? "completed"
                    : outcome.kind() == TransferOutcome.Kind.CANCELLED ? "cancelled" : "failed";
            return outcome;
        } finally {
            if (!deferred) {
                if (session.waitReason != null) {
                    finishWait(id, session, terminalResult, elapsedRealtime.getAsLong());
                }
                SESSIONS.remove(id, session);
                if (id > 0) clearCancelFlag(id);
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

    /** Runs one retry step and parks instead of sleeping on a transfer worker. */
    private TransferOutcome runSession(
            Context context,
            Reservation reservation,
            DownloadRequest request,
            int id,
            TransferObserver observer,
            TransferSession session
    ) {
        if (isCancelled(id)) return TransferOutcome.cancelled();

        if (session.waitReason == TransferOutcome.WaitReason.RETRY_BACKOFF) {
            long now = elapsedRealtime.getAsLong();
            long remaining = session.waitUntilMillis - now;
            if (remaining > 0) {
                return TransferOutcome.deferred(remaining, TransferOutcome.WaitReason.RETRY_BACKOFF);
            }
            finishWait(id, session, "elapsed", now);
        }

        long now = elapsedRealtime.getAsLong();
        boolean online;
        try {
            online = networkAvailability.isOnline(context);
        } catch (RuntimeException ignored) {
            online = true;
        }
        if (!online) {
            if (now >= session.deadlineMillis) {
                finishWait(id, session, "deadline", now);
                if (session.failure == null) {
                    session.failure = new UnknownHostException("No validated network");
                }
                return tryFallbacks(context, reservation, request, id, observer, session);
            }
            beginNetworkWait(id, session, observer, now);
            long remaining = session.deadlineMillis - now;
            return TransferOutcome.deferred(
                    Math.min(NETWORK_POLL_INTERVAL_MS, remaining),
                    TransferOutcome.WaitReason.NETWORK_VALIDATION);
        }

        if (session.waitReason == TransferOutcome.WaitReason.NETWORK_VALIDATION) {
            finishWait(id, session, "network_available", now);
        }
        if (isCancelled(id)) return TransferOutcome.cancelled();

        if (session.primaryAttempt == 1) {
            log.info(() -> "Download " + downloadLabel(id)
                    + " started; sourceHost=" + hostOf(request.url()));
        }
        long resumeFrom = session.primaryAttempt == 1 ? 0 : currentSize(context, reservation);
        if (session.state.expectedTotal > 0 && resumeFrom >= session.state.expectedTotal) {
            if (resumeFrom == session.state.expectedTotal) return TransferOutcome.saved();
            resumeFrom = 0;
        }

        final int attemptNumber = session.primaryAttempt;
        final long requestedOffset = resumeFrom;
        log.info(() -> "Download " + downloadLabel(id) + " attempt=" + attemptNumber
                + " resumeOffset=" + requestedOffset);
        Failure attemptFailure = new Failure();
        if (saveAttempt(context, reservation, request.url(), id, resumeFrom,
                session.state, observer, attemptFailure)) {
            return TransferOutcome.saved();
        }
        session.failure = attemptFailure.cause;
        if (isCancelled(id)) return TransferOutcome.cancelled();
        if (!isRetryable(attemptFailure.cause) || session.primaryAttempt >= MAX_ATTEMPTS) {
            return tryFallbacks(context, reservation, request, id, observer, session);
        }

        log.info(() -> "Download " + downloadLabel(id) + " retry scheduled after attempt "
                + attemptNumber);
        session.primaryAttempt++;
        long retryDelay = RETRY_DELAYS_MS[session.primaryAttempt - 1];
        beginBackoffWait(id, session, attemptNumber, retryDelay, elapsedRealtime.getAsLong());
        return TransferOutcome.deferred(retryDelay, TransferOutcome.WaitReason.RETRY_BACKOFF);
    }

    private TransferOutcome tryFallbacks(
            Context context,
            Reservation reservation,
            DownloadRequest request,
            int id,
            TransferObserver observer,
            TransferSession session
    ) {
        List<String> fallbackUrls = request.fallbackUrls();
        if (fallbackUrls != null) {
            for (String fallbackUrl : fallbackUrls) {
                if (isCancelled(id)) return TransferOutcome.cancelled();
                Failure fallbackFailure = new Failure();
                if (saveAttempt(context, reservation, fallbackUrl, id, 0,
                        new TransferState(), observer, fallbackFailure)) {
                    return TransferOutcome.saved();
                }
                if (fallbackFailure.cause != null) session.failure = fallbackFailure.cause;
            }
        }
        if (isCancelled(id)) return TransferOutcome.cancelled();
        return TransferOutcome.failed(session.failure);
    }

    private void beginNetworkWait(
            int id,
            TransferSession session,
            TransferObserver observer,
            long now
    ) {
        if (session.waitReason == TransferOutcome.WaitReason.NETWORK_VALIDATION) return;
        if (session.waitReason != null) finishWait(id, session, "interrupted", now);
        session.waitReason = TransferOutcome.WaitReason.NETWORK_VALIDATION;
        session.waitStartedAt = now;
        log.info(() -> "Download " + downloadLabel(id)
                + " wait started reason=validated_network_unavailable");
        observer.onWaitingForConnection();
    }

    private void beginBackoffWait(
            int id,
            TransferSession session,
            int afterAttempt,
            long delayMillis,
            long now
    ) {
        session.waitReason = TransferOutcome.WaitReason.RETRY_BACKOFF;
        session.waitStartedAt = now;
        session.waitUntilMillis = now + delayMillis;
        log.info(() -> "Download " + downloadLabel(id) + " wait scheduled reason=retry_backoff"
                + " durationMs=" + delayMillis + " afterAttempt=" + afterAttempt);
    }

    private void finishWait(int id, TransferSession session, String result, long now) {
        if (session.waitReason == null) return;
        TransferOutcome.WaitReason waitReason = session.waitReason;
        long durationMillis = Math.max(0, now - session.waitStartedAt);
        String reason = waitReason == TransferOutcome.WaitReason.NETWORK_VALIDATION
                ? "validated_network_unavailable" : "retry_backoff";
        log.info(() -> "Download " + downloadLabel(id) + " wait ended reason=" + reason
                + " durationMs=" + durationMillis + " result=" + result);
        session.waitReason = null;
        session.waitStartedAt = -1;
        session.waitUntilMillis = 0;
    }

    /** Validated internet path; fail open when the service is missing so the attempt probes it. */
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

    /** Transient network failures resume; dead links and lost folders fail closed. */
    static boolean isRetryable(@Nullable Throwable cause) {
        if (cause == null) return false;
        if (DestinationLoss.matches(cause)) return false;
        if (cause instanceof InvalidContentRangeException) return false;
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
        long attemptStartedAt = SystemClock.elapsedRealtime();
        long bytesWritten = 0;
        long readWaitMs = 0;
        long outputWriteMs = 0;
        observer.onTransferring();
        try {
            connection = openConnection(url, resumeFrom, id);
            trackConnection(id, connection);
            int status = connection.getResponseCode();
            boolean resumed = resumeFrom > 0 && status == HttpURLConnection.HTTP_PARTIAL;
            long base = resumed ? resumeFrom : 0;
            try {
                state.expectedTotal = expectedTotal(connection, resumed ? resumeFrom : 0);
            } catch (InvalidContentRangeException invalidRange) {
                if (!resumed) throw invalidRange;

                log.info(() -> "Download " + downloadLabel(id)
                        + " received an invalid range; restarting from byte zero");
                untrackConnection(id, connection);
                connection.disconnect();
                connection = null;
                state.expectedTotal = -1;
                truncate(context, reservation);
                if (id > 0 && isCancelled(id)) return false;

                connection = openConnection(url, 0, id);
                trackConnection(id, connection);
                state.expectedTotal = expectedTotal(connection, 0);
                resumed = false;
                base = 0;
            }

            OutputStream output;
            if (resumed) {
                try {
                    output = context.getContentResolver().openOutputStream(reservation.documentUri(), "wa");
                } catch (IOException | RuntimeException appendFailure) {
                    // Provider cannot append: redo this attempt from zero on a fresh connection.
                    untrackConnection(id, connection);
                    connection.disconnect();
                    connection = openConnection(url, 0, id);
                    trackConnection(id, connection);
                    state.expectedTotal = expectedTotal(connection, 0);
                    resumeFrom = 0;
                    resumed = false;
                    base = 0;
                    output = context.getContentResolver().openOutputStream(reservation.documentUri(), "wt");
                }
            } else {
                output = context.getContentResolver().openOutputStream(reservation.documentUri(), "wt");
            }
            if (output == null) throw new IOException("Could not open " + reservation.fileName());

            final HttpURLConnection finalConnection = connection;
            long total = base;
            long lastLoggedAt = SystemClock.elapsedRealtime();
            long lastLoggedBytes = total;
            long lastLoggedReadWaitMs = 0;
            long lastLoggedWriteMs = 0;
            try (InputStream input = new BufferedInputStream(finalConnection.getInputStream());
                 OutputStream body = output) {
                byte[] buffer = new byte[64 * 1024];
                long lastUpdate = SystemClock.elapsedRealtime();
                int read;
                while (true) {
                    long readStartedAt = SystemClock.elapsedRealtime();
                    read = input.read(buffer);
                    readWaitMs += SystemClock.elapsedRealtime() - readStartedAt;
                    if (read == -1) break;
                    if (id > 0 && isCancelled(id)) return false;
                    long writeStartedAt = SystemClock.elapsedRealtime();
                    body.write(buffer, 0, read);
                    outputWriteMs += SystemClock.elapsedRealtime() - writeStartedAt;
                    total += read;
                    bytesWritten += read;

                    long now = SystemClock.elapsedRealtime();
                    if (now - lastUpdate > 200) {
                        observer.onProgress(total, state.expectedTotal >= 0 ? state.expectedTotal : -1);
                        lastUpdate = now;
                    }
                    if (now - lastLoggedAt >= 5_000) {
                        final long windowBytes = total - lastLoggedBytes;
                        final long windowDurationMs = now - lastLoggedAt;
                        final long windowReadWaitMs = readWaitMs - lastLoggedReadWaitMs;
                        final long windowWriteMs = outputWriteMs - lastLoggedWriteMs;
                        final long currentTotal = total;
                        final long expectedTotal = state.expectedTotal;
                        log.info(() -> "Download " + downloadLabel(id) + " progress bytes=" + currentTotal
                                + " total=" + expectedTotal + " windowBytesPerSecond="
                                + bytesPerSecond(windowBytes, windowDurationMs)
                                + " readWaitMs=" + windowReadWaitMs + " outputWriteMs=" + windowWriteMs);
                        lastLoggedAt = now;
                        lastLoggedBytes = total;
                        lastLoggedReadWaitMs = readWaitMs;
                        lastLoggedWriteMs = outputWriteMs;
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

            final long completedBytes = bytesWritten;
            final long elapsedMs = Math.max(1, SystemClock.elapsedRealtime() - attemptStartedAt);
            final long expectedTotal = state.expectedTotal;
            final long totalWritten = total;
            final long totalReadWaitMs = readWaitMs;
            final long totalOutputWriteMs = outputWriteMs;
            log.info(() -> "Download " + downloadLabel(id) + " transfer complete bytes=" + completedBytes
                    + " fileBytes=" + totalWritten + " total=" + expectedTotal + " elapsedMs=" + elapsedMs
                    + " averageBytesPerSecond=" + bytesPerSecond(completedBytes, elapsedMs)
                    + " readWaitMs=" + totalReadWaitMs + " outputWriteMs=" + totalOutputWriteMs);

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
            final long failedBytes = bytesWritten;
            final long failedElapsedMs = Math.max(0, SystemClock.elapsedRealtime() - attemptStartedAt);
            final long failedReadWaitMs = readWaitMs;
            final long failedWriteMs = outputWriteMs;
            log.info(() -> "Download " + downloadLabel(id) + " attempt stopped bytes=" + failedBytes
                    + " elapsedMs=" + failedElapsedMs + " readWaitMs=" + failedReadWaitMs
                    + " outputWriteMs=" + failedWriteMs);
            log.error(() -> "Failed download " + downloadLabel(id), exception);
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

    /** Returns the complete representation length, rejecting partial or inconsistent responses. */
    private static long expectedTotal(HttpURLConnection connection, long requestedOffset)
            throws IOException {
        if (connection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
            return validateContentRange(
                    connection.getHeaderField("Content-Range"),
                    requestedOffset,
                    connection.getContentLengthLong());
        }

        long contentLength = connection.getContentLengthLong();
        return contentLength >= 0 ? contentLength : -1;
    }

    /**
     * Validates an open-ended byte range before its body is appended. A range must begin at the
     * requested offset, reach the end of the representation, and agree with Content-Length when
     * that header is available.
     */
    static long validateContentRange(
            @Nullable String header,
            long requestedOffset,
            long contentLength
    ) throws InvalidContentRangeException {
        if (requestedOffset < 0 || header == null) {
            throw invalidContentRange(header, requestedOffset);
        }

        Matcher matcher = CONTENT_RANGE_PATTERN.matcher(header.trim());
        if (!matcher.matches() || "*".equals(matcher.group(3))) {
            throw invalidContentRange(header, requestedOffset);
        }

        final long start;
        final long end;
        final long total;
        try {
            start = Long.parseLong(matcher.group(1));
            end = Long.parseLong(matcher.group(2));
            total = Long.parseLong(matcher.group(3));
        } catch (NumberFormatException exception) {
            throw invalidContentRange(header, requestedOffset);
        }

        if (start != requestedOffset || end < start || total <= end || end != total - 1) {
            throw invalidContentRange(header, requestedOffset);
        }

        long rangeLength = end - start + 1;
        if (contentLength >= 0 && contentLength != rangeLength) {
            throw invalidContentRange(header, requestedOffset);
        }
        return total;
    }

    private static InvalidContentRangeException invalidContentRange(
            @Nullable String header,
            long requestedOffset
    ) {
        return new InvalidContentRangeException(
                "Invalid Content-Range for byte " + requestedOffset + ": " + header);
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

    private HttpURLConnection openConnection(String address, long resumeFrom, int id) throws IOException {
        String current = address;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            long responseStartedAt = SystemClock.elapsedRealtime();
            HttpURLConnection connection = openSingleConnection(current, resumeFrom);
            int status = connection.getResponseCode();
            long responseTimeMs = SystemClock.elapsedRealtime() - responseStartedAt;
            logResponse(id, connection, status, resumeFrom, responseTimeMs);
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
            URL redirectedUrl = new URL(new URL(current), location);
            final String fromHost = hostOf(current);
            final String toHost = redirectedUrl.getHost();
            log.info(() -> "Download " + downloadLabel(id) + " redirect status=" + status
                    + " fromHost=" + fromHost + " toHost=" + toHost);
            current = redirectedUrl.toString();
        }
        throw new IOException("Too many redirects for " + address);
    }

    private void logResponse(
            int id,
            HttpURLConnection connection,
            int status,
            long requestedOffset,
            long responseTimeMs
    ) {
        final String host = connection.getURL().getHost();
        final long contentLength = connection.getContentLengthLong();
        final String contentRange = headerOrDash(connection.getHeaderField("Content-Range"));
        final String contentType = headerOrDash(connection.getContentType());
        final String acceptRanges = headerOrDash(connection.getHeaderField("Accept-Ranges"));
        final String contentEncoding = headerOrDash(connection.getHeaderField("Content-Encoding"));
        log.info(() -> "Download " + downloadLabel(id) + " response status=" + status + " host=" + host
                + " responseMs=" + responseTimeMs + " rangeOffset=" + requestedOffset
                + " contentLength=" + contentLength + " contentRange=" + contentRange
                + " contentType=" + contentType + " acceptRanges=" + acceptRanges
                + " contentEncoding=" + contentEncoding);
    }

    private static String headerOrDash(@Nullable String value) {
        return value == null || value.isEmpty() ? "-" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String hostOf(String address) {
        try {
            return new URL(address).getHost();
        } catch (IOException | RuntimeException exception) {
            return "unknown";
        }
    }

    private static String downloadLabel(int id) {
        return id > 0 ? "#" + id : "(untracked)";
    }

    private static long bytesPerSecond(long bytes, long durationMs) {
        if (bytes <= 0 || durationMs <= 0) return 0;
        return (long) (bytes * 1000d / durationMs);
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

    interface NetworkAvailability {
        boolean isOnline(Context context);
    }

    /** Retry progress retained while the engine parks the transfer. */
    private static final class TransferSession {
        final long deadlineMillis;
        final TransferState state = new TransferState();
        int primaryAttempt = 1;
        @Nullable Throwable failure;
        @Nullable TransferOutcome.WaitReason waitReason;
        long waitStartedAt = -1;
        long waitUntilMillis;

        TransferSession(long deadlineMillis) {
            this.deadlineMillis = deadlineMillis;
        }
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

    /** A malformed or incomplete range response must never be retried as an append. */
    static final class InvalidContentRangeException extends IOException {
        InvalidContentRangeException(String message) {
            super(message);
        }
    }

    /** Last transfer exception. */
    private static final class Failure {
        Throwable cause;
    }
}
