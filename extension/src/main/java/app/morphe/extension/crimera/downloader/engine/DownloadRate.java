/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

/** Estimates transfer speed from monotonic progress samples. */
public final class DownloadRate {
    private long lastBytes = -1;
    private long lastSampleTimeMs = -1;

    /** Returns bytes per second, or {@code -1} until a valid interval is available. */
    public long update(long bytesDone, long sampleTimeMs) {
        if (bytesDone < 0 || sampleTimeMs < 0) {
            reset();
            return -1;
        }

        if (lastSampleTimeMs < 0 || sampleTimeMs <= lastSampleTimeMs || bytesDone < lastBytes) {
            lastBytes = bytesDone;
            lastSampleTimeMs = sampleTimeMs;
            return -1;
        }

        long elapsedMs = sampleTimeMs - lastSampleTimeMs;
        long transferredBytes = bytesDone - lastBytes;
        lastBytes = bytesDone;
        lastSampleTimeMs = sampleTimeMs;
        return (long) (transferredBytes * 1000d / elapsedMs);
    }

    public void reset() {
        lastBytes = -1;
        lastSampleTimeMs = -1;
    }
}
