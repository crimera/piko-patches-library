/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import androidx.annotation.Nullable;

/** How a transfer ended. A failure keeps its cause so the caller can classify it. */
public final class TransferOutcome {
    public enum Kind {
        SAVED,
        CANCELLED,
        FAILED
    }

    private static final TransferOutcome SAVED = new TransferOutcome(Kind.SAVED, null);
    private static final TransferOutcome CANCELLED = new TransferOutcome(Kind.CANCELLED, null);

    private final Kind kind;
    @Nullable private final Throwable cause;

    private TransferOutcome(Kind kind, @Nullable Throwable cause) {
        this.kind = kind;
        this.cause = cause;
    }

    public static TransferOutcome saved() {
        return SAVED;
    }

    public static TransferOutcome cancelled() {
        return CANCELLED;
    }

    public static TransferOutcome failed(@Nullable Throwable cause) {
        return new TransferOutcome(Kind.FAILED, cause);
    }

    public Kind kind() {
        return kind;
    }

    @Nullable
    public Throwable cause() {
        return cause;
    }
}
