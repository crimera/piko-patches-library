/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.model;

/** Synchronous reservation outcome when a download request is enqueued. */
public enum EnqueueState {
    QUEUED,
    SKIPPED,
    FAILED,
    DESTINATION_LOST
}
