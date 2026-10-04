/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

/** Handle to an active or recently completed download. */
public final class DownloadHandle {
    public enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final int id;
    private final DownloadEngine engine;
    private final DownloadEngine.DownloadRecord record;

    DownloadHandle(int id, DownloadEngine engine, DownloadEngine.DownloadRecord record) {
        this.id = id;
        this.engine = engine;
        this.record = record;
    }

    public int id() {
        return id;
    }

    public void cancel() {
        engine.cancel(id);
    }

    public State state() {
        return record.state;
    }
}
