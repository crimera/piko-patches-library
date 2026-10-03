/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.messages;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import app.morphe.extension.crimera.downloader.model.BatchResult;

/** Formatter for batch download completion summaries. */
public final class DownloadMessages {
    private DownloadMessages() {
    }

    public static String summary(BatchResult result, DownloadTexts texts, @Nullable String label) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(texts, "texts");

        int queued = result.queued();
        int skipped = result.skipped();
        int failed = result.failed();
        int lost = result.lost();

        String message;
        if (failed == 0 && skipped == 0 && lost == 0) {
            if (queued == 1) {
                message = texts.get(DownloadText.DOWNLOAD_STARTED, 0);
            } else if (queued > 1) {
                message = texts.get(DownloadText.DOWNLOADS_STARTED, queued);
            } else {
                message = texts.get(DownloadText.COULD_NOT_START, 0);
            }
        } else if (queued == 0) {
            if (failed == 0 && lost == 0 && skipped > 0) {
                message = skipped == 1
                        ? texts.get(DownloadText.ALREADY_DOWNLOADED, 0)
                        : texts.get(DownloadText.MEDIA_ALREADY_DOWNLOADED, skipped);
            } else if (lost > 0) {
                message = texts.get(DownloadText.FOLDER_LOST, 0);
            } else {
                message = texts.get(DownloadText.COULD_NOT_START, 0);
            }
        } else {
            List<String> parts = new ArrayList<>(4);
            parts.add(texts.get(DownloadText.PART_QUEUED, queued));
            if (skipped > 0) {
                parts.add(texts.get(DownloadText.PART_SKIPPED, skipped));
            }
            if (failed > 0) {
                parts.add(texts.get(DownloadText.PART_FAILED, failed));
            }
            if (lost > 0) {
                parts.add(texts.get(DownloadText.PART_LOST, lost));
            }
            message = String.join(", ", parts);
        }

        return formatWithLabel(message, label);
    }

    private static String formatWithLabel(String message, @Nullable String label) {
        if (label == null) {
            return message;
        }
        String normalized = label.trim();
        if (normalized.isEmpty()) {
            return message;
        }
        if (normalized.charAt(0) == '@') {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return message;
        }
        return message + " \u2014 @" + normalized;
    }
}
