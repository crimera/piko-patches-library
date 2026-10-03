/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/** What a notification action can ask the engine to do. */
public interface DownloadController {
    /** Cancels a queued or running download. Returns false when {@code id} is not active, so the caller can dismiss a stale notice. */
    boolean cancel(int id);

    /** Starts the download again under the same id, as a new lifecycle. */
    void retry(int id, DownloadRequest request);
}
