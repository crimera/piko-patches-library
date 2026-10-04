/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.model;

/** Final outcome of saving a reserved download to disk. */
public enum SaveState {
    SAVED,
    DESTINATION_LOST,
    FAILED,
    CANCELLED,
    NO_CONNECTION
}
