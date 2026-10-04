/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.downloader.engine.DownloadController;
import app.morphe.extension.crimera.downloader.engine.DownloadControllers;
import app.morphe.extension.crimera.downloader.engine.DownloadNotifications;

/** Cancels an in-flight download from its progress notification. */
public final class CancelReceiver extends BroadcastReceiver {
    public static final String ACTION_CANCEL =
            "app.morphe.extension.crimera.downloader.action.CANCEL_DOWNLOAD";
    public static final String EXTRA_NOTIFICATION_ID =
            "app.morphe.extension.crimera.downloader.extra.CANCEL_NOTIFICATION_ID";

    /** PendingIntent for the progress notification's cancel button. Null when unusable. */
    @Nullable
    public static PendingIntent cancelPendingIntent(Context context, int notificationId) {
        if (context == null || notificationId <= 0) {
            return null;
        }
        try {
            Intent intent = new Intent(context, CancelReceiver.class);
            intent.setAction(ACTION_CANCEL);
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            return PendingIntent.getBroadcast(context, notificationId, intent, flags);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!ACTION_CANCEL.equals(intent.getAction())) {
            return;
        }

        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        if (notificationId <= 0) {
            return;
        }

        // An id nobody is running (the process died, the download already ended) leaves an ongoing
        // notice that users cannot swipe away before Android 14, so dismiss it here.
        DownloadController controller = DownloadControllers.get();
        if (controller == null || !controller.cancel(notificationId)) {
            Context applicationContext = context.getApplicationContext();
            DownloadNotifications.cancelNotification(
                    applicationContext != null ? applicationContext : context, notificationId);
        }
    }
}
