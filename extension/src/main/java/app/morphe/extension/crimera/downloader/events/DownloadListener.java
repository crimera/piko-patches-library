/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.events;

/**
 * Observer for download lifecycle events.
 */
public interface DownloadListener {
    default void onQueued(DownloadEvent.Queued event) {}

    default void onStarted(DownloadEvent.Started event) {}

    default void onWaiting(DownloadEvent.Waiting event) {}

    default void onProgress(DownloadEvent.Progress event) {}

    default void onCompleted(DownloadEvent.Completed event) {}

    default void onFailed(DownloadEvent.Failed event) {}

    default void onCancelled(DownloadEvent.Cancelled event) {}
}
