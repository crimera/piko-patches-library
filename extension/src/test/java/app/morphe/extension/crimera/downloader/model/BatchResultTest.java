/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BatchResultTest {
    @Test
    public void initialStateIsAllZeroes() {
        BatchResult result = new BatchResult();
        assertEquals(0, result.queued());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        assertEquals(0, result.lost());
        assertEquals(0, result.total());
    }

    @Test
    public void addIncrementsMatchingCounterAndTotal() {
        BatchResult result = new BatchResult();

        result.add(EnqueueState.QUEUED);
        assertEquals(1, result.queued());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        assertEquals(0, result.lost());
        assertEquals(1, result.total());

        result.add(EnqueueState.SKIPPED);
        assertEquals(1, result.queued());
        assertEquals(1, result.skipped());
        assertEquals(0, result.failed());
        assertEquals(0, result.lost());
        assertEquals(2, result.total());

        result.add(EnqueueState.FAILED);
        assertEquals(1, result.queued());
        assertEquals(1, result.skipped());
        assertEquals(1, result.failed());
        assertEquals(0, result.lost());
        assertEquals(3, result.total());

        result.add(EnqueueState.DESTINATION_LOST);
        assertEquals(1, result.queued());
        assertEquals(1, result.skipped());
        assertEquals(1, result.failed());
        assertEquals(1, result.lost());
        assertEquals(4, result.total());
    }

    @Test
    public void multipleAddsAccumulateCorrectly() {
        BatchResult result = new BatchResult();

        result.add(EnqueueState.QUEUED);
        result.add(EnqueueState.QUEUED);
        result.add(EnqueueState.SKIPPED);
        result.add(EnqueueState.SKIPPED);
        result.add(EnqueueState.SKIPPED);
        result.add(EnqueueState.FAILED);
        result.add(EnqueueState.DESTINATION_LOST);
        result.add(EnqueueState.DESTINATION_LOST);

        assertEquals(2, result.queued());
        assertEquals(3, result.skipped());
        assertEquals(1, result.failed());
        assertEquals(2, result.lost());
        assertEquals(8, result.total());
    }

    @Test(expected = NullPointerException.class)
    public void addNullThrowsNullPointerException() {
        new BatchResult().add(null);
    }
}
