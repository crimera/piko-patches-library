/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.crimera.downloader.RetryReceiver;
import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/**
 * Encodes and decodes download requests into Intent extras for notification retry actions.
 */
public final class RequestExtras {
    public static final char SEPARATOR = '|';
    public static final char ESCAPE = '\\';

    public static final String EXTRA_ID =
            "app.morphe.extension.crimera.downloader.extra.ID";
    public static final String EXTRA_URL =
            "app.morphe.extension.crimera.downloader.extra.URL";
    public static final String EXTRA_FALLBACK_URLS =
            "app.morphe.extension.crimera.downloader.extra.FALLBACK_URLS";
    public static final String EXTRA_DESTINATION_TREE =
            "app.morphe.extension.crimera.downloader.extra.DESTINATION_TREE";
    public static final String EXTRA_SUBPATH =
            "app.morphe.extension.crimera.downloader.extra.SUBPATH";
    public static final String EXTRA_FILE_NAME =
            "app.morphe.extension.crimera.downloader.extra.FILE_NAME";
    public static final String EXTRA_MIME_TYPE =
            "app.morphe.extension.crimera.downloader.extra.MIME_TYPE";
    public static final String EXTRA_CONFLICT =
            "app.morphe.extension.crimera.downloader.extra.CONFLICT";
    public static final String EXTRA_LABEL =
            "app.morphe.extension.crimera.downloader.extra.LABEL";
    public static final String EXTRA_CHANNEL_ID =
            "app.morphe.extension.crimera.downloader.extra.CHANNEL_ID";
    public static final String EXTRA_CHANNEL_NAME =
            "app.morphe.extension.crimera.downloader.extra.CHANNEL_NAME";

    private RequestExtras() {
    }

    public static boolean isValidUrl(@Nullable String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static String encodeList(@Nullable List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                builder.append(SEPARATOR);
            }
            String item = list.get(i);
            if (item != null) {
                for (int j = 0; j < item.length(); j++) {
                    char c = item.charAt(j);
                    if (c == ESCAPE || c == SEPARATOR) {
                        builder.append(ESCAPE);
                    }
                    builder.append(c);
                }
            }
        }
        return builder.toString();
    }

    public static List<String> decodeList(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (escaping) {
                current.append(c);
                escaping = false;
            } else if (c == ESCAPE) {
                escaping = true;
            } else if (c == SEPARATOR) {
                items.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (escaping) {
            current.append(ESCAPE);
        }
        items.add(current.toString());
        return Collections.unmodifiableList(items);
    }

    @Nullable
    public static String encodeConflict(@Nullable ConflictPolicy policy) {
        return policy != null ? policy.name() : null;
    }

    @Nullable
    public static ConflictPolicy decodeConflict(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return ConflictPolicy.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    public static String encodeUri(@Nullable Uri uri) {
        return uri != null ? uri.toString() : null;
    }

    @Nullable
    public static Uri decodeUri(@Nullable String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            return null;
        }
        return Uri.parse(uriString);
    }

    public static boolean canRetry(@Nullable DownloadRequest request) {
        return request != null
                && isValidUrl(request.url())
                && request.fileName() != null
                && !request.fileName().isEmpty()
                && request.conflict() != null;
    }

    @Nullable
    public static DownloadRequest decodeRequest(
            @Nullable String url,
            @Nullable String fallbackUrlsEncoded,
            @Nullable String destinationTreeString,
            @Nullable String subpathEncoded,
            @Nullable String fileName,
            @Nullable String mimeType,
            @Nullable String conflictName,
            @Nullable String label
    ) {
        if (!isValidUrl(url)) {
            return null;
        }
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
        }
        ConflictPolicy conflict = decodeConflict(conflictName);
        if (conflict == null) {
            return null;
        }
        Uri destinationTree = null;
        try {
            destinationTree = decodeUri(destinationTreeString);
        } catch (RuntimeException ignored) {
        }
        List<String> fallbacks = decodeList(fallbackUrlsEncoded);
        List<String> subpath = decodeList(subpathEncoded);
        try {
            return new DownloadRequest(
                    url,
                    fallbacks,
                    destinationTree,
                    subpath,
                    fileName,
                    mimeType,
                    conflict,
                    label
            );
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    public static void writeToIntent(Intent intent, int id, DownloadRequest request) {
        if (intent == null || request == null) {
            return;
        }
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_URL, request.url());
        if (!request.fallbackUrls().isEmpty()) {
            intent.putExtra(EXTRA_FALLBACK_URLS, encodeList(request.fallbackUrls()));
        }
        if (request.destinationTree() != null) {
            intent.putExtra(EXTRA_DESTINATION_TREE, encodeUri(request.destinationTree()));
        }
        if (!request.subpath().isEmpty()) {
            intent.putExtra(EXTRA_SUBPATH, encodeList(request.subpath()));
        }
        intent.putExtra(EXTRA_FILE_NAME, request.fileName());
        intent.putExtra(EXTRA_MIME_TYPE, request.mimeType());
        intent.putExtra(EXTRA_CONFLICT, encodeConflict(request.conflict()));
        if (request.label() != null) {
            intent.putExtra(EXTRA_LABEL, request.label());
        }
    }

    @Nullable
    public static DownloadRequest fromIntent(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        return decodeRequest(
                intent.getStringExtra(EXTRA_URL),
                intent.getStringExtra(EXTRA_FALLBACK_URLS),
                intent.getStringExtra(EXTRA_DESTINATION_TREE),
                intent.getStringExtra(EXTRA_SUBPATH),
                intent.getStringExtra(EXTRA_FILE_NAME),
                intent.getStringExtra(EXTRA_MIME_TYPE),
                intent.getStringExtra(EXTRA_CONFLICT),
                intent.getStringExtra(EXTRA_LABEL)
        );
    }

    public static int getId(@Nullable Intent intent) {
        return intent != null ? intent.getIntExtra(EXTRA_ID, 0) : 0;
    }

    public static void writeChannel(Intent intent, @Nullable String channelId, @Nullable String channelName) {
        if (intent == null) {
            return;
        }
        if (channelId != null) {
            intent.putExtra(EXTRA_CHANNEL_ID, channelId);
        }
        if (channelName != null) {
            intent.putExtra(EXTRA_CHANNEL_NAME, channelName);
        }
    }

    public static String getChannelId(@Nullable Intent intent, String defaultChannelId) {
        if (intent == null) {
            return defaultChannelId;
        }
        String id = intent.getStringExtra(EXTRA_CHANNEL_ID);
        return id != null && !id.isEmpty() ? id : defaultChannelId;
    }

    public static String getChannelName(@Nullable Intent intent, String defaultChannelName) {
        if (intent == null) {
            return defaultChannelName;
        }
        String name = intent.getStringExtra(EXTRA_CHANNEL_NAME);
        return name != null && !name.isEmpty() ? name : defaultChannelName;
    }

    @Nullable
    public static PendingIntent retryPendingIntent(
            Context context,
            DownloadRequest request,
            int notificationId,
            @Nullable String channelId,
            @Nullable String channelName
    ) {
        if (context == null || notificationId <= 0 || !canRetry(request)) {
            return null;
        }
        try {
            Intent intent = new Intent(context, RetryReceiver.class);
            intent.setAction(RetryReceiver.ACTION_RETRY);
            writeToIntent(intent, notificationId, request);
            writeChannel(intent, channelId, channelName);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            return PendingIntent.getBroadcast(context, notificationId, intent, flags);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
