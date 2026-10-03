/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

/** Receives what a transfer is doing; the engine turns these calls into lifecycle events. */
public interface TransferObserver {
    /** There is no validated network yet and the transfer is waiting for one. */
    void onWaitingForConnection();

    /** A transfer attempt started or resumed. */
    void onTransferring();

    /** Bytes written so far; {@code totalBytes} is -1 when the server did not say. */
    void onProgress(long bytesDone, long totalBytes);
}
