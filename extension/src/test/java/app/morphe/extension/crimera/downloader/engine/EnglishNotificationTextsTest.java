/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class EnglishNotificationTextsTest {
    private final EnglishNotificationTexts texts = new EnglishNotificationTexts();

    @Test
    public void providesExactNotificationStrings() {
        assertEquals("Waiting for connection", texts.waitingForConnection());
        assertEquals("Cancel", texts.cancelAction());
        assertEquals("Share", texts.shareAction());
        assertEquals("Retry", texts.retryAction());
        assertEquals("Download Completed", texts.downloadCompleted());
        assertEquals("Download failed", texts.downloadFailed());
        assertEquals("Download folder is no longer available", texts.folderLost());
        assertEquals("No connection \u2014 tap Retry when online", texts.noConnection());
        assertTrue(texts.noConnection().contains("\u2014"));
    }

    @Test
    public void formatsShareChooserTitle() {
        assertEquals("Share sentinel.mp4", texts.shareChooserTitle("sentinel.mp4"));
        assertEquals("Share", texts.shareChooserTitle(null));
        assertEquals("Share", texts.shareChooserTitle(""));
    }
}
