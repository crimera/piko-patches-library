/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.IOException;

import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/** Creates and removes the document a download is written into. */
public interface DestinationWriter {
    /**
     * Creates the document inside the request's destination tree and relative path, applying the request's
     * conflict rule. Returns null when the rule is SKIP and the file already exists.
     */
    @Nullable
    Reservation reserve(Context context, DownloadRequest request) throws IOException;

    /** Deletes a reserved document after a failed or cancelled transfer. */
    void discard(Context context, Reservation reservation);
}
