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
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.downloader.engine.DownloadNotifications;

/** Deletes a finished download from its completed notification. */
public final class DeleteReceiver extends BroadcastReceiver {
    public static final String ACTION_DELETE =
            "app.morphe.extension.crimera.downloader.action.DELETE_DOWNLOAD";
    public static final String EXTRA_NOTIFICATION_ID =
            "app.morphe.extension.crimera.downloader.extra.DELETE_NOTIFICATION_ID";

    private static final Executor DELETE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "download-delete");
        thread.setDaemon(true);
        return thread;
    });

    /** PendingIntent for the completed notification's delete button. Null when unusable. */
    @Nullable
    public static PendingIntent deletePendingIntent(Context context, @Nullable Uri uri, int notificationId) {
        if (context == null || uri == null || notificationId <= 0) {
            return null;
        }
        try {
            Intent intent = new Intent(context, DeleteReceiver.class);
            intent.setAction(ACTION_DELETE);
            intent.setData(uri);
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
        if (!ACTION_DELETE.equals(intent.getAction())) {
            return;
        }

        Uri uri = intent.getData();
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        if (uri == null || notificationId <= 0) {
            return;
        }

        // Provider calls can block on slow storage, so the delete runs off the main thread.
        Context applicationContext = context.getApplicationContext();
        Context deleteContext = applicationContext != null ? applicationContext : context;
        BroadcastReceiver.PendingResult result = goAsync();
        DELETE_EXECUTOR.execute(() -> {
            try {
                deleteDownload(deleteContext, uri, notificationId);
            } finally {
                result.finish();
            }
        });
    }

    /** Removes the saved file, and dismisses the notice only once the file is gone. */
    private static void deleteDownload(Context context, Uri uri, int notificationId) {
        boolean removed;
        try {
            removed = DocumentsContract.deleteDocument(context.getContentResolver(), uri);
        } catch (FileNotFoundException exception) {
            // Already removed outside the app, so the notice has nothing left to act on.
            removed = true;
        } catch (RuntimeException exception) {
            removed = false;
        }
        if (removed) {
            DownloadNotifications.cancelNotification(context, notificationId);
        }
    }
}
