/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.messages;

import java.util.Objects;

/** Default English strings for batch download summary messages. */
public final class EnglishDownloadTexts implements DownloadTexts {
    @Override
    public String get(DownloadText id, int count) {
        Objects.requireNonNull(id, "id");
        switch (id) {
            case DOWNLOAD_STARTED:
                return "Download started";
            case DOWNLOADS_STARTED:
                return count == 1 ? "1 download started" : count + " downloads started";
            case ALREADY_DOWNLOADED:
                return "Already downloaded";
            case MEDIA_ALREADY_DOWNLOADED:
                return count + " media already downloaded";
            case FOLDER_LOST:
                return "Download folder is no longer available \u2014 tap download again to choose a new one";
            case COULD_NOT_START:
                return "Could not start download";
            case PART_QUEUED:
                return count == 1 ? "1 download started" : count + " downloads started";
            case PART_SKIPPED:
                return count == 1 ? "1 already downloaded" : count + " already downloaded";
            case PART_FAILED:
                return count == 1 ? "1 failed" : count + " failed";
            case PART_LOST:
                return count == 1 ? "1 needs a new folder" : count + " need a new folder";
            default:
                throw new IllegalArgumentException("Unknown text id: " + id);
        }
    }
}
