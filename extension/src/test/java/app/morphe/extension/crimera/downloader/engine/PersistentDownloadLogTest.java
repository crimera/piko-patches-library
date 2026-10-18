/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

    @Test
    public void disabledGateSkipsMessageResolutionAndDelegate() {
        AtomicInteger delegateCalls = new AtomicInteger();
        AtomicInteger messageResolutions = new AtomicInteger();
        DownloadLog delegate = new DownloadLog() {
            @Override public void info(Supplier<String> message) { delegateCalls.incrementAndGet(); }
            @Override public void error(Supplier<String> message, Throwable cause) {
                delegateCalls.incrementAndGet();
            }
        };
        Context context = new ContextWrapper(null) {
            @Override public Context getApplicationContext() { return this; }
        };
        PersistentDownloadLog log = new PersistentDownloadLog(context, delegate, () -> false);

        log.info(() -> {
            messageResolutions.incrementAndGet();
            return "disabled";
        });
        log.error(() -> "disabled", new IOException("ignored"));

        assertEquals(0, delegateCalls.get());
        assertEquals(0, messageResolutions.get());
    }
}
