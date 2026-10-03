/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.messages;

/** Message identifiers required by the download batch summary formatter. */
public enum DownloadText {
    DOWNLOAD_STARTED,
    DOWNLOADS_STARTED,
    ALREADY_DOWNLOADED,
    MEDIA_ALREADY_DOWNLOADED,
    FOLDER_LOST,
    COULD_NOT_START,
    PART_QUEUED,
    PART_SKIPPED,
    PART_FAILED,
    PART_LOST
}
