/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

/**
 * Localized string provider for user-facing download notifications.
 */
public interface NotificationTexts {
    String waitingForConnection();

    String cancelAction();

    String shareAction();

    String retryAction();

    String downloadCompleted();

    String downloadFailed();

    String noConnection();

    String folderLost();

    String shareChooserTitle(String fileName);
}
