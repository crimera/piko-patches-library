/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import java.util.function.Supplier;

/** Diagnostics sink the engine writes to; the default drops everything. */
public interface DownloadLog {
    DownloadLog NOOP = new DownloadLog() {
        @Override
        public void info(Supplier<String> message) {
        }

        @Override
        public void error(Supplier<String> message, Throwable cause) {
        }
    };

    void info(Supplier<String> message);

    void error(Supplier<String> message, Throwable cause);
}
