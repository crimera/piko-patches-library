/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import androidx.annotation.Nullable;

import java.net.UnknownHostException;

import app.morphe.extension.crimera.downloader.events.FailureReason;

/** Maps the cause of a failed transfer to the reason an event reports. */
public final class FailureClassifier {
    private FailureClassifier() {
    }

    /** A lost destination wins over a lost connection: the folder must be chosen again either way. */
    public static FailureReason classify(@Nullable Throwable cause) {
        if (DestinationLoss.matches(cause)) return FailureReason.DESTINATION_LOST;
        if (isNoConnection(cause)) return FailureReason.NO_CONNECTION;
        return FailureReason.UNKNOWN;
    }

    /** True when DNS or connectivity was down, so the transfer never started. */
    static boolean isNoConnection(@Nullable Throwable cause) {
        Throwable current = cause;
        for (int depth = 0; current != null && depth < DestinationLoss.MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof UnknownHostException) return true;
            current = current.getCause();
        }
        return false;
    }
}
