/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.model;

import java.util.Objects;

/** Not thread-safe; a batch is assembled by a single caller. */
public final class BatchResult {
    private int queued;
    private int skipped;
    private int failed;
    private int lost;

    public BatchResult() {
    }

    public void add(EnqueueState state) {
        Objects.requireNonNull(state, "state");
        switch (state) {
            case QUEUED:
                queued++;
                break;
            case SKIPPED:
                skipped++;
                break;
            case FAILED:
                failed++;
                break;
            case DESTINATION_LOST:
                lost++;
                break;
        }
    }

    public int queued() {
        return queued;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public int lost() {
        return lost;
    }

    public int total() {
        return queued + skipped + failed + lost;
    }
}
