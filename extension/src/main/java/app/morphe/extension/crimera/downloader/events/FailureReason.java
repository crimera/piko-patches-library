/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.events;

/**
 * Classification of why a download could not be completed.
 */
public enum FailureReason {
    NO_CONNECTION,
    DESTINATION_LOST,
    UNKNOWN
}
