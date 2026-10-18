/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.content.Context;

import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/** Streams a request's URL into a reserved document, retaining its reservation while deferred. */
public interface TransferRunner {
    /**
     * Downloads {@code request.url()}, resuming across transient failures, then tries each of
     * {@code request.fallbackUrls()} once. It may return {@link TransferOutcome.Kind#DEFERRED} when it needs
     * the engine to invoke it again after a delay; this is nonterminal and the reservation must remain live.
     * Never throws for a failed transfer: the failure is the outcome. Does not discard the reservation or post
     * notifications/events; it only informs the observer.
     */
    TransferOutcome run(Context context, Reservation reservation, DownloadRequest request, int id, TransferObserver observer);

    /**
     * Cancels download {@code id}: a read blocked on the socket is interrupted and {@link #run} ends with a
     * cancelled outcome. Call it only for a download that is queued, running, or deferred; a cancel that arrives
     * before {@code run} starts still applies. A deferred return is nonterminal, so implementations may retain
     * retry state for that id until the resumed run returns a terminal outcome.
     */
    void cancel(int id);
}
