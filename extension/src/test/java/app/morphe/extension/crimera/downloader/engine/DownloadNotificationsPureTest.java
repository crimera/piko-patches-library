/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.morphe.extension.crimera.downloader.DownloadEngine;
import app.morphe.extension.crimera.downloader.events.FailureReason;

public final class DownloadNotificationsPureTest {
    private final NotificationTexts texts = new EnglishNotificationTexts();

    @Test
    public void terminalNoticeIdsNeverCollideWithDownloadIds() {
        // A download's progress notice has the download's id; a completed notice that reused it
        // would be dismissed right after being posted.
        assertTrue(DownloadNotifications.FIRST_TERMINAL_ID > DownloadEngine.MAX_FRESH_ID);
        int first = DownloadNotifications.newNotificationId();
        int second = DownloadNotifications.newNotificationId();
        assertTrue(first > DownloadEngine.MAX_FRESH_ID);
        assertEquals(first + 1, second);
    }

    @Test
    public void progressNormalPercent() {
        assertEquals(0, DownloadNotifications.progressOf(0, 100));
        assertEquals(1, DownloadNotifications.progressOf(1, 100));
        assertEquals(50, DownloadNotifications.progressOf(50, 100));
        assertEquals(98, DownloadNotifications.progressOf(98, 100));
    }

    @Test
    public void progressCappedAt99() {
        assertEquals(99, DownloadNotifications.progressOf(99, 100));
        assertEquals(99, DownloadNotifications.progressOf(100, 100));
        assertEquals(99, DownloadNotifications.progressOf(150, 100));
        assertEquals(99, DownloadNotifications.progressOf(1000, 50));
    }

    @Test
    public void progressZeroAndNegativeTotals() {
        assertEquals(0, DownloadNotifications.progressOf(50, 0));
        assertEquals(0, DownloadNotifications.progressOf(50, -1));
        assertEquals(0, DownloadNotifications.progressOf(50, -100));
        assertEquals(0, DownloadNotifications.progressOf(0, 0));
        assertEquals(0, DownloadNotifications.progressOf(-10, 100));
        assertEquals(0, DownloadNotifications.progressOf(-5, 0));
        assertEquals(0, DownloadNotifications.progressOf(-5, -5));
    }

    @Test
    public void failureTextSelectionDestinationLostBeatsNoConnectionBeatsGeneric() {
        DownloadNotifications.FailureResolution lost =
                DownloadNotifications.resolveFailure(FailureReason.DESTINATION_LOST, true, true);
        assertEquals(DownloadNotifications.FailureTextSelection.DESTINATION_LOST, lost.textSelection());
        assertEquals("Download folder is no longer available",
                DownloadNotifications.selectFailureText(texts, lost.textSelection()));

        DownloadNotifications.FailureResolution noConn =
                DownloadNotifications.resolveFailure(FailureReason.NO_CONNECTION, true, true);
        assertEquals(DownloadNotifications.FailureTextSelection.NO_CONNECTION, noConn.textSelection());
        assertEquals("No connection \u2014 tap Retry when online",
                DownloadNotifications.selectFailureText(texts, noConn.textSelection()));

        DownloadNotifications.FailureResolution unknown =
                DownloadNotifications.resolveFailure(FailureReason.UNKNOWN, true, true);
        assertEquals(DownloadNotifications.FailureTextSelection.GENERIC, unknown.textSelection());
        assertEquals("Download failed",
                DownloadNotifications.selectFailureText(texts, unknown.textSelection()));

        DownloadNotifications.FailureResolution nullReason =
                DownloadNotifications.resolveFailure(null, true, true);
        assertEquals(DownloadNotifications.FailureTextSelection.GENERIC, nullReason.textSelection());
        assertEquals("Download failed",
                DownloadNotifications.selectFailureText(texts, nullReason.textSelection()));
    }

    @Test
    public void retryPresentOnlyWithRetriableAndFoundRequest() {
        assertTrue(DownloadNotifications.resolveFailure(FailureReason.UNKNOWN, true, true).showRetry());
        assertFalse(DownloadNotifications.resolveFailure(FailureReason.UNKNOWN, true, false).showRetry());
        assertFalse(DownloadNotifications.resolveFailure(FailureReason.UNKNOWN, false, true).showRetry());
        assertFalse(DownloadNotifications.resolveFailure(FailureReason.UNKNOWN, false, false).showRetry());
    }
}
