/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.downloader.engine.DownloadControllers;
import app.morphe.extension.crimera.downloader.engine.DownloadLog;
import app.morphe.extension.crimera.downloader.engine.HttpTransfer;
import app.morphe.extension.crimera.downloader.engine.NotificationListener;
import app.morphe.extension.crimera.downloader.engine.NotificationTexts;
import app.morphe.extension.crimera.downloader.engine.SafDestination;
import app.morphe.extension.crimera.downloader.events.DownloadEvents;

/**
 * Wires the engine the usual way: SAF destination, HTTP transfer, one transfer thread, and
 * notifications on their own thread. An app that needs something else builds a {@link DownloadEngine} itself.
 */
public final class Downloader {
    private static DownloadEngine engine;

    private Downloader() {
    }

    /**
     * Creates the engine and installs it for the notification receivers. Call it once when the
     * process starts, so a Cancel or Retry tapped after the process was killed finds an engine. Later
     * calls return the engine of the first.
     */
    public static synchronized DownloadEngine install(
            Context context,
            String channelId,
            String channelName,
            NotificationTexts texts,
            DownloadLog log
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(log, "log");
        if (engine != null) {
            return engine;
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }

        DownloadEvents events = new DownloadEvents(error -> log.error(() -> "Download event listener failed", error));
        DownloadEngine created = new DownloadEngine(
                applicationContext,
                new SafDestination(log),
                new HttpTransfer(log),
                events,
                singleThread("downloads"),
                System::currentTimeMillis);
        events.register(
                new NotificationListener(applicationContext, channelId, channelName, texts, created::requestFor),
                singleThread("download-notifications"));
        DownloadControllers.install(created);
        engine = created;
        return created;
    }

    /** The installed engine, or null before {@link #install}. */
    @Nullable
    public static synchronized DownloadEngine get() {
        return engine;
    }

    private static Executor singleThread(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }
}
