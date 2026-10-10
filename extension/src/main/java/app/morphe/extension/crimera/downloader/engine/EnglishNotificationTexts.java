/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

/** Default English strings for download notifications. */
public final class EnglishNotificationTexts implements NotificationTexts {
    @Override
    public String waitingForConnection() {
        return "Waiting for connection";
    }

    @Override
    public String cancelAction() {
        return "Cancel";
    }

    @Override
    public String shareAction() {
        return "Share";
    }

    @Override
    public String deleteAction() {
        return "Delete";
    }

    @Override
    public String retryAction() {
        return "Retry";
    }

    @Override
    public String downloadCompleted() {
        return "Download Completed";
    }

    @Override
    public String downloadFailed() {
        return "Download failed";
    }

    @Override
    public String noConnection() {
        return "No connection \u2014 tap Retry when online";
    }

    @Override
    public String folderLost() {
        return "Download folder is no longer available";
    }

    @Override
    public String shareChooserTitle(String fileName) {
        return fileName != null && !fileName.isEmpty() ? "Share " + fileName : "Share";
    }
}
