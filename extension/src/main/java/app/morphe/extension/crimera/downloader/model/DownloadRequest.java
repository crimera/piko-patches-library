/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.model;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable request describing a file transfer, its target location, and conflict policy. */
public final class DownloadRequest {
    private final String url;
    private final List<String> fallbackUrls;
    // Stored as given without validation; android.net.Uri cannot be instantiated in JVM unit tests.
    private final Uri destinationTree;
    private final List<String> subpath;
    private final String fileName;
    private final String mimeType;
    private final ConflictPolicy conflict;
    private final String label;

    public DownloadRequest(
            String url,
            @Nullable List<String> fallbackUrls,
            Uri destinationTree,
            List<String> subpath,
            String fileName,
            String mimeType,
            ConflictPolicy conflict,
            @Nullable String label
    ) {
        Objects.requireNonNull(url, "url");
        if (url.isEmpty()) {
            throw new IllegalArgumentException("url must be non-empty");
        }
        Objects.requireNonNull(subpath, "subpath");
        Objects.requireNonNull(fileName, "fileName");
        validatePathSegment(fileName, "fileName");
        Objects.requireNonNull(mimeType, "mimeType");
        if (mimeType.isEmpty()) {
            throw new IllegalArgumentException("mimeType must be non-empty");
        }
        Objects.requireNonNull(conflict, "conflict");

        this.url = url;
        this.fallbackUrls = fallbackUrls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(fallbackUrls));
        this.destinationTree = destinationTree;

        List<String> subpathCopy = new ArrayList<>(subpath.size());
        for (String segment : subpath) {
            validatePathSegment(segment, "subpath segment");
            subpathCopy.add(segment);
        }
        this.subpath = Collections.unmodifiableList(subpathCopy);

        this.fileName = fileName;
        this.mimeType = mimeType;
        this.conflict = conflict;
        this.label = label;
    }

    private static void validatePathSegment(String segment, String name) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException(name + " must be non-empty");
        }
        if (segment.contains("/") || segment.contains("\\")) {
            throw new IllegalArgumentException(name + " must not contain '/' or '\\'");
        }
        if (".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException(name + " must not be '.' or '..'");
        }
    }

    public String url() {
        return url;
    }

    public List<String> fallbackUrls() {
        return fallbackUrls;
    }

    public Uri destinationTree() {
        return destinationTree;
    }

    public List<String> subpath() {
        return subpath;
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }

    public ConflictPolicy conflict() {
        return conflict;
    }

    @Nullable
    public String label() {
        return label;
    }
}
