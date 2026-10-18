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
        FAILED,
        DEFERRED
    }

    public enum WaitReason {
        NETWORK_VALIDATION,
        RETRY_BACKOFF
    }

    private static final TransferOutcome SAVED = new TransferOutcome(Kind.SAVED, null);
    private static final TransferOutcome CANCELLED = new TransferOutcome(Kind.CANCELLED, null);

    private final Kind kind;
    @Nullable private final Throwable cause;
    private final long delayMillis;
    @Nullable private final WaitReason waitReason;

    private TransferOutcome(Kind kind, @Nullable Throwable cause) {
        this(kind, cause, 0, null);
    }

    private TransferOutcome(
            Kind kind,
            @Nullable Throwable cause,
            long delayMillis,
            @Nullable WaitReason waitReason
    ) {
        this.kind = kind;
        this.cause = cause;
        this.delayMillis = delayMillis;
        this.waitReason = waitReason;
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

    /** The transfer can continue after the scheduler wakes it; its reservation remains live. */
    public static TransferOutcome deferred(long delayMillis, WaitReason waitReason) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative: " + delayMillis);
        }
        if (waitReason == null) {
            throw new NullPointerException("waitReason");
        }
        return new TransferOutcome(Kind.DEFERRED, null, delayMillis, waitReason);
    }

    public Kind kind() {
        return kind;
    }

    @Nullable
    public Throwable cause() {
        return cause;
    }

    public long delayMillis() {
        return delayMillis;
    }

    @Nullable
    public WaitReason waitReason() {
        return waitReason;
    }
}
