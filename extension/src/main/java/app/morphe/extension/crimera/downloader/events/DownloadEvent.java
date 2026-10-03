/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.events;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Base class for immutable download lifecycle events.
 */
public abstract class DownloadEvent {
    private final int id;
    @Nullable private final String label;

    DownloadEvent(int id, @Nullable String label) {
        this.id = id;
        this.label = label;
    }

    public int id() {
        return id;
    }

    @Nullable
    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return false;
    }

    public static final class Queued extends DownloadEvent {
        public Queued(int id, @Nullable String label) {
            super(id, label);
        }
    }

    public static final class Started extends DownloadEvent {
        public Started(int id, @Nullable String label) {
            super(id, label);
        }
    }

    public static final class Progress extends DownloadEvent {
        private final long bytesDone;
        private final long totalBytes;

        public Progress(int id, @Nullable String label, long bytesDone, long totalBytes) {
            super(id, label);
            this.bytesDone = bytesDone;
            this.totalBytes = totalBytes;
        }

        public long bytesDone() {
            return bytesDone;
        }

        public long totalBytes() {
            return totalBytes;
        }
    }

    public static final class Completed extends DownloadEvent {
        @Nullable private final Uri uri;
        private final String fileName;

        public Completed(int id, @Nullable String label, @Nullable Uri uri, String fileName) {
            super(id, label);
            this.uri = uri;
            this.fileName = Objects.requireNonNull(fileName, "fileName");
        }

        @Nullable
        public Uri uri() {
            return uri;
        }

        public String fileName() {
            return fileName;
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    }

    public static final class Failed extends DownloadEvent {
        private final FailureReason reason;
        private final boolean retriable;

        public Failed(int id, @Nullable String label, FailureReason reason, boolean retriable) {
            super(id, label);
            this.reason = Objects.requireNonNull(reason, "reason");
            this.retriable = retriable;
        }

        public FailureReason reason() {
            return reason;
        }

        public boolean retriable() {
            return retriable;
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    }

    public static final class Cancelled extends DownloadEvent {
        public Cancelled(int id, @Nullable String label) {
            super(id, label);
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    }
}
