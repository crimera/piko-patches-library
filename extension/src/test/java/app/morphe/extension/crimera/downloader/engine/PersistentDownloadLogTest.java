/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

public final class PersistentDownloadLogTest {
    @Test
    public void logLineRedactsSignedUrlsAndKeepsOneEntryOnOneLine() {
        String line = PersistentDownloadLog.formatLine(
                "ERROR",
                "Request failed https://cdn.example.com/media.mp4?token=secret\nnext line",
                new IOException("HTTP 403 for https://cdn.example.com/private?sig=secret"),
                0);

        assertTrue(line.contains("ERROR Request failed [URL redacted] next line"));
        assertTrue(line.contains("IOException: HTTP 403 for [URL redacted]"));
        assertFalse(line.contains("secret"));
        assertFalse(line.contains("\n"));
    }
}
