/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.crimera.downloader.events.DownloadEvent;
import app.morphe.extension.crimera.downloader.events.DownloadListener;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/**
 * DownloadListener that presents progress, completion, and failure notifications.
 */
public final class NotificationListener implements DownloadListener {
    private final Context context;
    private final String channelId;
    private final String channelName;
    private final NotificationTexts texts;
    private final RequestLookup requests;
    private final ConcurrentHashMap<Integer, DownloadRate> rates = new ConcurrentHashMap<>();

    public NotificationListener(
            Context applicationContext,
            String channelId,
            String channelName,
            NotificationTexts texts,
            RequestLookup requests
    ) {
        Objects.requireNonNull(applicationContext, "applicationContext");
        Context appContext = applicationContext.getApplicationContext();
        this.context = appContext != null ? appContext : applicationContext;
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.channelName = Objects.requireNonNull(channelName, "channelName");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    @Override
    public void onQueued(DownloadEvent.Queued event) {
        if (event == null || event.id() <= 0) {
            return;
        }
        rates.remove(event.id());
        String fileName = resolveFileName(event.id(), event.label());
        DownloadNotifications.showIndeterminate(context, channelId, channelName, texts, event.id(), fileName);
    }

    @Override
    public void onStarted(DownloadEvent.Started event) {
        if (event == null || event.id() <= 0) {
            return;
        }
        rates.remove(event.id());
        String fileName = resolveFileName(event.id(), event.label());
        DownloadNotifications.showIndeterminate(context, channelId, channelName, texts, event.id(), fileName);
    }

    @Override
    public void onWaiting(DownloadEvent.Waiting event) {
        if (event == null || event.id() <= 0) {
            return;
        }
        rates.remove(event.id());
        String fileName = resolveFileName(event.id(), event.label());
        DownloadNotifications.showWaiting(context, channelId, channelName, texts, event.id(), fileName);
    }

    @Override
    public void onProgress(DownloadEvent.Progress event) {
        if (event == null || event.id() <= 0) {
            return;
        }
        DownloadRate rate = rates.computeIfAbsent(event.id(), ignored -> new DownloadRate());
        long bytesPerSecond = rate.update(event.bytesDone(), SystemClock.elapsedRealtime());
        String fileName = resolveFileName(event.id(), event.label());
        if (event.totalBytes() <= 0) {
            DownloadNotifications.updateIndeterminateNotification(
                    context, channelId, channelName, texts, event.id(), fileName, bytesPerSecond);
        } else {
            int progress = DownloadNotifications.progressOf(event.bytesDone(), event.totalBytes());
            DownloadNotifications.updateNotification(
                    context, channelId, channelName, texts, event.id(), fileName, progress, bytesPerSecond);
        }
    }

    @Override
    public void onCompleted(DownloadEvent.Completed event) {
        if (event == null || event.id() <= 0) {
            return;
        }
        rates.remove(event.id());
        DownloadNotifications.completeNotification(
                context,
                channelId,
                channelName,
                texts,
                event.id(),
                event.fileName(),
                event.uri(),
                event.mimeType()
        );
    }

    @Override
    public void onFailed(DownloadEvent.Failed event) {
        if (event == null || event.id() <= 0) {
            return;
        }
        rates.remove(event.id());
        DownloadRequest request = requests.requestFor(event.id());
        String fileName = request != null ? request.fileName() : (event.label() != null ? event.label() : "");
        DownloadNotifications.notifyFailure(
                context,
                channelId,
                channelName,
                texts,
                event.id(),
                fileName,
                event.reason(),
                event.retriable(),
                request
        );
    }

    @Override
    public void onCancelled(DownloadEvent.Cancelled event) {
        if (event == null || event.id() <= 0) {
            return;
        }
        rates.remove(event.id());
        DownloadNotifications.cancelNotification(context, event.id());
    }

    private String resolveFileName(int id, @Nullable String fallbackLabel) {
        DownloadRequest request = requests.requestFor(id);
        if (request != null && request.fileName() != null && !request.fileName().isEmpty()) {
            return request.fileName();
        }
        return fallbackLabel != null ? fallbackLabel : "";
    }
}
