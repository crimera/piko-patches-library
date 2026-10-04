/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import androidx.annotation.Nullable;

import java.util.Objects;

import app.morphe.extension.crimera.downloader.model.EnqueueState;

/** Synchronous reservation and enqueue outcome for a download request. */
public final class EnqueueResult {
    private final EnqueueState state;
    @Nullable private final DownloadHandle handle;
    @Nullable private final Throwable cause;

    public EnqueueResult(EnqueueState state, @Nullable DownloadHandle handle, @Nullable Throwable cause) {
        this.state = Objects.requireNonNull(state, "state");
        this.handle = handle;
        this.cause = cause;
    }

    public static EnqueueResult queued(DownloadHandle handle) {
        return new EnqueueResult(EnqueueState.QUEUED, Objects.requireNonNull(handle, "handle"), null);
    }

    public static EnqueueResult skipped() {
        return new EnqueueResult(EnqueueState.SKIPPED, null, null);
    }

    public static EnqueueResult failed(Throwable cause) {
        return new EnqueueResult(EnqueueState.FAILED, null, Objects.requireNonNull(cause, "cause"));
    }

    public static EnqueueResult destinationLost(Throwable cause) {
        return new EnqueueResult(EnqueueState.DESTINATION_LOST, null, Objects.requireNonNull(cause, "cause"));
    }

    public EnqueueState state() {
        return state;
    }

    @Nullable
    public DownloadHandle handle() {
        return handle;
    }

    @Nullable
    public Throwable cause() {
        return cause;
    }
}
