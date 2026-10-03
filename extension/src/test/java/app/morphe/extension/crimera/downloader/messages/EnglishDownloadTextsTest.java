/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.messages;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class EnglishDownloadTextsTest {
    private final EnglishDownloadTexts texts = new EnglishDownloadTexts();

    @Test
    public void standaloneMessagesReturnExpectedStrings() {
        assertEquals("Download started", texts.get(DownloadText.DOWNLOAD_STARTED, 0));
        assertEquals("2 downloads started", texts.get(DownloadText.DOWNLOADS_STARTED, 2));
        assertEquals("Already downloaded", texts.get(DownloadText.ALREADY_DOWNLOADED, 0));
        assertEquals("2 media already downloaded", texts.get(DownloadText.MEDIA_ALREADY_DOWNLOADED, 2));
        assertEquals(
                "Download folder is no longer available \u2014 tap download again to choose a new one",
                texts.get(DownloadText.FOLDER_LOST, 0)
        );
        assertEquals("Could not start download", texts.get(DownloadText.COULD_NOT_START, 0));
    }

    @Test
    public void mixedPartMessagesHandleSingularAndPlural() {
        assertEquals("1 download started", texts.get(DownloadText.PART_QUEUED, 1));
        assertEquals("3 downloads started", texts.get(DownloadText.PART_QUEUED, 3));

        assertEquals("1 already downloaded", texts.get(DownloadText.PART_SKIPPED, 1));
        assertEquals("3 already downloaded", texts.get(DownloadText.PART_SKIPPED, 3));

        assertEquals("1 failed", texts.get(DownloadText.PART_FAILED, 1));
        assertEquals("3 failed", texts.get(DownloadText.PART_FAILED, 3));

        assertEquals("1 needs a new folder", texts.get(DownloadText.PART_LOST, 1));
        assertEquals("3 need a new folder", texts.get(DownloadText.PART_LOST, 3));
    }

    @Test(expected = NullPointerException.class)
    public void nullIdThrowsNullPointerException() {
        texts.get(null, 0);
    }
}
