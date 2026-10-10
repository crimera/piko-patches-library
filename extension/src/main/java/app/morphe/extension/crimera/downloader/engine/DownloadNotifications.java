/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Size;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.crimera.downloader.CancelReceiver;
import app.morphe.extension.crimera.downloader.DeleteReceiver;
import app.morphe.extension.crimera.downloader.DownloadEngine;
import app.morphe.extension.crimera.downloader.events.FailureReason;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/**
 * Static helpers for posting, updating, and dismissing download notifications.
 */
public final class DownloadNotifications {
    /** Completed and failed notices count up from just above every id the engine hands out. */
    static final int FIRST_TERMINAL_ID = DownloadEngine.MAX_FRESH_ID + 1;

    private static final AtomicInteger NEXT_NOTIFICATION_ID = new AtomicInteger(FIRST_TERMINAL_ID);

    /** Upper bound for the completed notice's preview; the provider keeps the aspect ratio within it. */
    private static final Size PREVIEW_SIZE = new Size(512, 512);

    enum FailureTextSelection {
        DESTINATION_LOST,
        NO_CONNECTION,
        GENERIC
    }

    static final class FailureResolution {
        private final FailureTextSelection textSelection;
        private final boolean showRetry;

        FailureResolution(FailureTextSelection textSelection, boolean showRetry) {
            this.textSelection = textSelection;
            this.showRetry = showRetry;
        }

        public FailureTextSelection textSelection() {
            return textSelection;
        }

        public boolean showRetry() {
            return showRetry;
        }
    }

    private DownloadNotifications() {
    }

    static int newNotificationId() {
        return NEXT_NOTIFICATION_ID.getAndUpdate(id -> id == Integer.MAX_VALUE ? FIRST_TERMINAL_ID : id + 1);
    }

    static int progressOf(long total, long contentLength) {
        if (contentLength <= 0 || total <= 0) {
            return 0;
        }
        int percent = (int) (total * 100 / contentLength);
        return Math.min(Math.max(percent, 0), 99);
    }

    static FailureResolution resolveFailure(
            @Nullable FailureReason reason,
            boolean retriable,
            boolean hasRequest
    ) {
        FailureTextSelection selection;
        if (reason == FailureReason.DESTINATION_LOST) {
            selection = FailureTextSelection.DESTINATION_LOST;
        } else if (reason == FailureReason.NO_CONNECTION) {
            selection = FailureTextSelection.NO_CONNECTION;
        } else {
            selection = FailureTextSelection.GENERIC;
        }
        boolean showRetry = retriable && hasRequest;
        return new FailureResolution(selection, showRetry);
    }

    static String selectFailureText(NotificationTexts texts, FailureTextSelection selection) {
        switch (selection) {
            case DESTINATION_LOST:
                return texts.folderLost();
            case NO_CONNECTION:
                return texts.noConnection();
            case GENERIC:
            default:
                return texts.downloadFailed();
        }
    }

    static void showWaiting(
            Context context,
            String channelId,
            String channelName,
            NotificationTexts texts,
            int id,
            String fileName
    ) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) {
                return;
            }

            Notification.Builder builder = notificationBuilder(context, channelId, channelName);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(fileName)
                    .setContentText(texts.waitingForConnection())
                    .setOngoing(true)
                    .setProgress(100, 0, true);
            PendingIntent cancel = CancelReceiver.cancelPendingIntent(context, id);
            if (cancel != null) {
                builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        texts.cancelAction(),
                        cancel
                );
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException ignored) {
        }
    }

    static void showIndeterminate(
            Context context,
            String channelId,
            String channelName,
            NotificationTexts texts,
            int id,
            String fileName
    ) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) {
                return;
            }

            Notification.Builder builder = notificationBuilder(context, channelId, channelName);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(fileName)
                    .setOngoing(true)
                    .setProgress(100, 0, true);
            PendingIntent cancel = CancelReceiver.cancelPendingIntent(context, id);
            if (cancel != null) {
                builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        texts.cancelAction(),
                        cancel
                );
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException ignored) {
        }
    }

    public static void showIndeterminate(
            Context context,
            String channelId,
            String channelName,
            int id,
            String fileName
    ) {
        showIndeterminate(context, channelId, channelName, new EnglishNotificationTexts(), id, fileName);
    }

    static void updateNotification(
            Context context,
            String channelId,
            String channelName,
            NotificationTexts texts,
            int id,
            String fileName,
            int progress
    ) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) {
                return;
            }

            Notification.Builder builder = notificationBuilder(context, channelId, channelName);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(fileName)
                    .setOngoing(true)
                    .setProgress(100, progress, false);
            PendingIntent cancel = CancelReceiver.cancelPendingIntent(context, id);
            if (cancel != null) {
                builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        texts.cancelAction(),
                        cancel
                );
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException ignored) {
        }
    }

    static void completeNotification(
            Context context,
            String channelId,
            String channelName,
            NotificationTexts texts,
            int id,
            String fileName,
            @Nullable Uri uri,
            String mimeType
    ) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) {
                return;
            }

            int terminalId = newNotificationId();
            Notification.Builder builder = notificationBuilder(context, channelId, channelName);
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(fileName)
                    .setContentText(texts.downloadCompleted())
                    .setOngoing(false)
                    .setProgress(0, 0, false);
            Bitmap preview = previewOf(context, uri);
            if (preview != null) {
                builder.setLargeIcon(preview)
                        .setStyle(new Notification.BigPictureStyle().bigPicture(preview));
            }
            PendingIntent share = shareIntent(context, texts, uri, mimeType, fileName, terminalId);
            if (share != null) {
                builder.addAction(android.R.drawable.ic_menu_share, texts.shareAction(), share);
            }
            PendingIntent delete = DeleteReceiver.deletePendingIntent(context, uri, terminalId);
            if (delete != null) {
                builder.addAction(android.R.drawable.ic_menu_delete, texts.deleteAction(), delete);
            }
            manager.notify(terminalId, builder.build());
            cancelNotification(context, id);
        } catch (RuntimeException ignored) {
        }
    }

    /** Thumbnail of the saved file for the completed notice, or null when the provider has none. */
    @Nullable
    private static Bitmap previewOf(Context context, @Nullable Uri uri) {
        if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        try {
            return context.getContentResolver().loadThumbnail(uri, PREVIEW_SIZE, null);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    private static PendingIntent shareIntent(
            Context context,
            NotificationTexts texts,
            @Nullable Uri uri,
            String mimeType,
            String fileName,
            int id
    ) {
        if (uri == null) {
            return null;
        }
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mimeType != null && !mimeType.isEmpty() ? mimeType : "*/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(share, texts.shareChooserTitle(fileName));
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            return PendingIntent.getActivity(context, id, chooser, flags);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static void notifyFailure(
            Context context,
            String channelId,
            String channelName,
            NotificationTexts texts,
            int id,
            String fileName,
            FailureReason reason,
            boolean retriable,
            @Nullable DownloadRequest request
    ) {
        if (id <= 0) {
            return;
        }
        if (!notificationsEnabled(context, channelId)) {
            cancelNotification(context, id);
            return;
        }
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) {
                return;
            }

            int terminalId = newNotificationId();
            FailureResolution resolution = resolveFailure(reason, retriable, request != null);
            String failureText = selectFailureText(texts, resolution.textSelection());

            Notification.Builder builder = notificationBuilder(context, channelId, channelName);
            builder.setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(fileName)
                    .setContentText(failureText)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setProgress(0, 0, false);
            if (resolution.showRetry() && request != null) {
                PendingIntent retry = RequestExtras.retryPendingIntent(
                        context, request, terminalId, channelId, channelName);
                if (retry != null) {
                    builder.addAction(android.R.drawable.stat_sys_download, texts.retryAction(), retry);
                }
            }
            manager.notify(terminalId, builder.build());
            cancelNotification(context, id);
        } catch (RuntimeException ignored) {
        }
    }

    public static boolean notificationsEnabled(Context context, String channelId) {
        NotificationManager manager = notificationManager(context);
        if (manager == null) {
            return false;
        }
        try {
            if (!manager.areNotificationsEnabled()) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = manager.getNotificationChannel(channelId);
                if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static void cancelNotification(Context context, int id) {
        if (id <= 0) {
            return;
        }
        try {
            NotificationManager manager = notificationManager(context);
            if (manager != null) {
                manager.cancel(id);
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Nullable
    private static NotificationManager notificationManager(Context context) {
        if (context == null) {
            return null;
        }
        Object service = context.getSystemService(Context.NOTIFICATION_SERVICE);
        return service instanceof NotificationManager ? (NotificationManager) service : null;
    }

    private static Notification.Builder notificationBuilder(
            Context context,
            String channelId,
            String channelName
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context, channelId, channelName);
            return new Notification.Builder(context, channelId);
        }
        return new Notification.Builder(context);
    }

    private static void createNotificationChannel(
            Context context,
            String channelId,
            String channelName
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = notificationManager(context);
            if (manager == null) {
                return;
            }
            if (manager.getNotificationChannel(channelId) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName != null && !channelName.isEmpty() ? channelName : "Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }
    }
}
