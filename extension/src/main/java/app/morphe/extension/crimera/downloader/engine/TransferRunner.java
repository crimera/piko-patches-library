/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.content.Context;

import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/** Streams a request's URL into a reserved document. Blocks the calling thread until the transfer ends. */
public interface TransferRunner {
    /**
     * Downloads {@code request.url()}, resuming across transient failures, then tries each of
     * {@code request.fallbackUrls()} once. Never throws for a failed transfer: the failure is the outcome.
     * Does not discard the reservation and does not post notifications or events; it only informs the observer.
     */
    TransferOutcome run(Context context, Reservation reservation, DownloadRequest request, int id, TransferObserver observer);

    /**
     * Cancels download {@code id}: a read blocked on the socket is interrupted and {@link #run} ends with a
     * cancelled outcome. Call it only for a download that is queued or running; a cancel that arrives before
     * {@code run} starts still applies. {@code run} forgets everything about the id when it returns, so the id can be
     * used again by a retry.
     */
    void cancel(int id);
}
