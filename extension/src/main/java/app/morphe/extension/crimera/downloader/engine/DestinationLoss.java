/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.Locale;

/** Tells a destination that stopped working apart from every other failure. */
public final class DestinationLoss {
    // Cause chains can cycle, so the walk is bounded.
    static final int MAX_CAUSE_DEPTH = 16;

    private DestinationLoss() {
    }

    /**
     * True when the destination itself is gone: revoked access, a deleted folder or a rejected tree URI.
     * Network and HTTP errors never count, and a generic IllegalArgumentException (a bad file name, a bad MIME
     * type) never counts.
     */
    public static boolean matches(@Nullable Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SecurityException || current instanceof FileNotFoundException) {
                return true;
            }
            if (current instanceof IllegalArgumentException && isTreeUriError((IllegalArgumentException) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTreeUriError(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("uri") || lower.contains("tree") || lower.contains("document");
    }
}
