/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.messages;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import app.morphe.extension.crimera.downloader.model.BatchResult;
import app.morphe.extension.crimera.downloader.model.EnqueueState;

public final class DownloadMessagesTest {
    private final DownloadTexts texts = new EnglishDownloadTexts();

    private static BatchResult createResult(int queued, int skipped, int failed, int lost) {
        BatchResult result = new BatchResult();
        for (int i = 0; i < queued; i++) result.add(EnqueueState.QUEUED);
        for (int i = 0; i < skipped; i++) result.add(EnqueueState.SKIPPED);
        for (int i = 0; i < failed; i++) result.add(EnqueueState.FAILED);
        for (int i = 0; i < lost; i++) result.add(EnqueueState.DESTINATION_LOST);
        return result;
    }

    @Test
    public void row1PureQueuedSingle() {
        BatchResult result = createResult(1, 0, 0, 0);
        assertEquals("Download started", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row2PureQueuedBoundaryTwo() {
        BatchResult result = createResult(2, 0, 0, 0);
        assertEquals("2 downloads started", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row2PureQueuedMultiple() {
        BatchResult result = createResult(5, 0, 0, 0);
        assertEquals("5 downloads started", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row3PureSkippedSingle() {
        BatchResult result = createResult(0, 1, 0, 0);
        assertEquals("Already downloaded", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row4PureSkippedBoundaryTwo() {
        BatchResult result = createResult(0, 2, 0, 0);
        assertEquals("2 media already downloaded", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row4PureSkippedMultiple() {
        BatchResult result = createResult(0, 4, 0, 0);
        assertEquals("4 media already downloaded", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row5LostFolder() {
        BatchResult result = createResult(0, 0, 0, 1);
        assertEquals(
                "Download folder is no longer available \u2014 tap download again to choose a new one",
                DownloadMessages.summary(result, texts, null)
        );
    }

    @Test
    public void row5LostFolderTakesPrecedenceOverSkippedAndFailedWhenQueuedZero() {
        BatchResult result = createResult(0, 3, 2, 1);
        assertEquals(
                "Download folder is no longer available \u2014 tap download again to choose a new one",
                DownloadMessages.summary(result, texts, null)
        );
    }

    @Test
    public void row6EmptyResult() {
        BatchResult result = createResult(0, 0, 0, 0);
        assertEquals("Could not start download", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row6OnlyFailedWhenQueuedZero() {
        BatchResult result = createResult(0, 0, 3, 0);
        assertEquals("Could not start download", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row6SkippedAndFailedWhenQueuedZero() {
        BatchResult result = createResult(0, 2, 1, 0);
        assertEquals("Could not start download", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row7MixedBoundaryQueuedOneSkippedOne() {
        BatchResult result = createResult(1, 1, 0, 0);
        assertEquals("1 download started, 1 already downloaded", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row7MixedBoundaryQueuedTwoSkippedTwo() {
        BatchResult result = createResult(2, 2, 0, 0);
        assertEquals("2 downloads started, 2 already downloaded", DownloadMessages.summary(result, texts, null));
    }

    @Test
    public void row7MixedAllFourPartsSingular() {
        BatchResult result = createResult(1, 1, 1, 1);
        assertEquals(
                "1 download started, 1 already downloaded, 1 failed, 1 needs a new folder",
                DownloadMessages.summary(result, texts, null)
        );
    }

    @Test
    public void row7MixedAllFourPartsPlural() {
        BatchResult result = createResult(3, 2, 4, 5);
        assertEquals(
                "3 downloads started, 2 already downloaded, 4 failed, 5 need a new folder",
                DownloadMessages.summary(result, texts, null)
        );
    }

    @Test
    public void row7MixedExactOrderAndZeroOmission() {
        BatchResult r1 = createResult(2, 0, 1, 0);
        assertEquals("2 downloads started, 1 failed", DownloadMessages.summary(r1, texts, null));

        BatchResult r2 = createResult(1, 0, 0, 2);
        assertEquals("1 download started, 2 need a new folder", DownloadMessages.summary(r2, texts, null));

        BatchResult r3 = createResult(2, 1, 0, 1);
        assertEquals(
                "2 downloads started, 1 already downloaded, 1 needs a new folder",
                DownloadMessages.summary(r3, texts, null)
        );
    }

    @Test
    public void labelFormattingRules() {
        BatchResult result = createResult(1, 0, 0, 0);

        assertEquals("Download started", DownloadMessages.summary(result, texts, null));
        assertEquals("Download started", DownloadMessages.summary(result, texts, ""));
        assertEquals("Download started", DownloadMessages.summary(result, texts, "   "));
        assertEquals("Download started", DownloadMessages.summary(result, texts, "@"));
        assertEquals("Download started", DownloadMessages.summary(result, texts, "  @  "));
        assertEquals("Download started \u2014 @bob", DownloadMessages.summary(result, texts, "  @bob "));
        assertEquals("Download started \u2014 @bob", DownloadMessages.summary(result, texts, "bob"));
        assertEquals("Download started \u2014 @@bob", DownloadMessages.summary(result, texts, "@@bob"));
    }

    @Test
    public void mixedResultWithLabel() {
        BatchResult result = createResult(3, 0, 0, 0);
        assertEquals("3 downloads started \u2014 @alice", DownloadMessages.summary(result, texts, "alice"));
    }

    @Test(expected = NullPointerException.class)
    public void nullResultThrowsNullPointerException() {
        DownloadMessages.summary(null, texts, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullTextsThrowsNullPointerException() {
        DownloadMessages.summary(createResult(1, 0, 0, 0), null, null);
    }
}
