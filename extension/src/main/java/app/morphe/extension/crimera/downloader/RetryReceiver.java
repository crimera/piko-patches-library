/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.downloader.engine.DownloadController;
import app.morphe.extension.crimera.downloader.engine.DownloadControllers;
import app.morphe.extension.crimera.downloader.engine.DownloadNotifications;
import app.morphe.extension.crimera.downloader.engine.RequestExtras;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/** Retries a failed download from its failure notification. */
public final class RetryReceiver extends BroadcastReceiver {
    public static final String ACTION_RETRY =
            "app.morphe.extension.crimera.downloader.action.RETRY_DOWNLOAD";

    private static final ExecutorService RETRY_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!ACTION_RETRY.equals(intent.getAction())) {
            return;
        }

        int notificationId = RequestExtras.getId(intent);
        DownloadRequest request = RequestExtras.fromIntent(intent);
        if (notificationId <= 0 || request == null) {
            return;
        }

        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        String channelId = RequestExtras.getChannelId(intent, "downloads");
        String channelName = RequestExtras.getChannelName(intent, "Downloads");

        // The process was restarted and the app has not installed the downloader: nothing can
        // retry, so dismiss the notice instead of swapping it for a progress notice nobody ends.
        if (DownloadControllers.get() == null) {
            DownloadNotifications.cancelNotification(safeContext, notificationId);
            return;
        }

        // Swap the failure notice for progress immediately so a second tap cannot queue
        // a duplicate transfer while the retry is dispatched to the engine.
        try {
            DownloadNotifications.showIndeterminate(
                    safeContext, channelId, channelName, notificationId, request.fileName());
        } catch (RuntimeException ignored) {
        }

        RETRY_EXECUTOR.execute(() -> {
            DownloadController controller = DownloadControllers.get();
            if (controller != null) {
                controller.retry(notificationId, request);
            }
        });
    }
}
