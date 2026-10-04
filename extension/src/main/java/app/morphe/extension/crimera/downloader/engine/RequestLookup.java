/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/**
 * Access to download requests by id for building retry actions.
 */
public interface RequestLookup {
    @Nullable
    DownloadRequest requestFor(int id);
}
