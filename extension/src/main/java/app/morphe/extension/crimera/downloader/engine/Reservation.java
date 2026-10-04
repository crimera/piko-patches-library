/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.net.Uri;

/** A destination document that exists but has no content yet, created before any network work. */
public final class Reservation {
    private final Uri documentUri;
    private final String fileName;
    private final String mimeType;

    public Reservation(Uri documentUri, String fileName, String mimeType) {
        this.documentUri = documentUri;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public Uri documentUri() {
        return documentUri;
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }
}
