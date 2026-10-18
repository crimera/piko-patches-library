/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DownloadRateTest {
    @Test
    public void estimatesRateFromMonotonicSamples() {
        DownloadRate rate = new DownloadRate();

        assertEquals(-1, rate.update(100, 1_000));
        assertEquals(500, rate.update(600, 2_000));
        assertEquals(1_000, rate.update(1_100, 2_500));
    }

    @Test
    public void resetsAfterResumeCounterMovesBackwardOrClockIsNotMonotonic() {
        DownloadRate rate = new DownloadRate();
        rate.update(1_000, 1_000);

        assertEquals(-1, rate.update(100, 2_000));
        assertEquals(400, rate.update(500, 3_000));
        assertEquals(-1, rate.update(600, 3_000));
        assertEquals(200, rate.update(800, 4_000));
    }
}
