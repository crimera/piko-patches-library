/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import androidx.annotation.Nullable;

/** Where the receivers find the engine: an app installs its controller once at startup. */
public final class DownloadControllers {
    private static volatile DownloadController current;

    private DownloadControllers() {
    }

    public static void install(DownloadController controller) {
        current = controller;
    }

    @Nullable
    public static DownloadController get() {
        return current;
    }
}
