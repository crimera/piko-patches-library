/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

/** Storage Access Framework writer for downloads. */
public final class SafDestination implements DestinationWriter {
    private static final int MAX_NAME_ATTEMPTS = 32;

    private final DownloadLog logger;

    public SafDestination() {
        this(DownloadLog.NOOP);
    }

    public SafDestination(DownloadLog logger) {
        this.logger = logger != null ? logger : DownloadLog.NOOP;
    }

    @Override
    @Nullable
    public Reservation reserve(Context context, DownloadRequest request) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");

        Uri destinationTree = request.destinationTree();
        if (destinationTree == null) {
            throw new IOException("No destination tree in the download request");
        }

        ContentResolver resolver = context.getContentResolver();
        final Uri rootDirectory;
        try {
            rootDirectory = SafPaths.directoryUri(destinationTree);
        } catch (RuntimeException exception) {
            throw new IOException("Download folder is not a usable tree", exception);
        }

        Uri directory = rootDirectory;
        for (String segment : request.subpath()) {
            Uri existing = findChildDocument(resolver, directory, segment);
            if (existing != null) {
                if (!isDirectory(resolver, existing)) {
                    throw new IOException("Subpath segment is not a directory: " + segment);
                }
                directory = existing;
            } else {
                Uri created = DocumentsContract.createDocument(
                        resolver, directory, DocumentsContract.Document.MIME_TYPE_DIR, segment);
                if (created == null) {
                    throw new IOException("Could not create directory " + segment);
                }
                directory = created;
            }
        }

        ConflictPolicy policy = request.conflict();
        String requested = request.fileName();
        String mimeType = request.mimeType();

        String candidate = requested;
        int suffix = 0;
        try {
            for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
                Uri existing = findDocumentByPath(context, directory, candidate);
                if (existing != null) {
                    if (policy == ConflictPolicy.SKIP) return null;
                    if (policy == ConflictPolicy.RENAME) {
                        candidate = appendSuffix(requested, ++suffix);
                        continue;
                    }
                    // OVERWRITE reuses the occupant; save() truncates it at transfer time.
                    return new Reservation(existing, candidate, mimeType);
                }

                Uri created = DocumentsContract.createDocument(resolver, directory, mimeType, candidate);
                if (created == null) {
                    throw new IOException("Could not create download file " + candidate);
                }

                String actualName = displayNameOf(resolver, created);
                if (actualName == null) {
                    DocumentsContract.deleteDocument(resolver, created);
                    throw new IOException("Could not read the created download name for " + candidate);
                }

                if (candidate.equals(actualName)) {
                    return new Reservation(created, actualName, mimeType);
                }

                // Provider renamed on create: name was taken but probe missed (opaque ids).
                DocumentsContract.deleteDocument(resolver, created);
                if (policy == ConflictPolicy.SKIP) return null;
                if (policy == ConflictPolicy.OVERWRITE) {
                    Uri occupant = findChildDocument(resolver, directory, candidate);
                    if (occupant != null) {
                        if (!DocumentsContract.deleteDocument(resolver, occupant)) {
                            throw new IOException("Could not replace existing file " + candidate);
                        }
                        continue;
                    }
                }

                candidate = appendSuffix(requested, ++suffix);
            }
        } catch (SecurityException | FileNotFoundException | IllegalArgumentException exception) {
            logger.error(() -> "Failed to reserve destination document for " + requested, exception);
            throw exception;
        }

        throw new IOException("Could not find an unused name for " + requested);
    }

    @Override
    public void discard(Context context, Reservation reservation) {
        if (context == null || reservation == null || reservation.documentUri() == null) {
            return;
        }
        try {
            DocumentsContract.deleteDocument(context.getContentResolver(), reservation.documentUri());
        } catch (Exception exception) {
            logger.error(() -> "Failed to discard " + reservation.fileName(), exception);
        }
    }

    @Nullable
    private Uri findChildDocument(ContentResolver resolver, Uri directory, String displayName) {
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        };
        try (Cursor cursor = resolver.query(SafPaths.childDocumentsUri(directory), projection, null, null, null)) {
            if (cursor == null) return null;

            while (cursor.moveToNext()) {
                if (!displayName.equals(cursor.getString(1))) continue;
                return DocumentsContract.buildDocumentUriUsingTree(directory, cursor.getString(0));
            }
        } catch (RuntimeException exception) {
            logger.error(() -> "Failed to query download folder", exception);
        }
        return null;
    }

    @Nullable
    private static Uri findDocumentByPath(Context context, Uri directory, String displayName) {
        final String parentId;
        try {
            parentId = DocumentsContract.getDocumentId(directory);
        } catch (RuntimeException exception) {
            return null;
        }
        if (parentId == null || parentId.isEmpty()) return null;

        final Uri child;
        try {
            child = DocumentsContract.buildDocumentUriUsingTree(directory, parentId + "/" + displayName);
        } catch (RuntimeException exception) {
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(
                child,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                null,
                null,
                null
        )) {
            return cursor != null && cursor.moveToFirst() ? child : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    private String displayNameOf(ContentResolver resolver, Uri documentUri) {
        try (Cursor cursor = resolver.query(
                documentUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            return cursor.getString(0);
        } catch (RuntimeException exception) {
            logger.error(() -> "Failed to read created document name", exception);
            return null;
        }
    }

    private boolean isDirectory(ContentResolver resolver, Uri documentUri) {
        String[] projection = {DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = resolver.query(documentUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return false;
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(0));
        } catch (RuntimeException exception) {
            logger.error(() -> "Failed to read document mime type", exception);
            return false;
        }
    }

    static String appendSuffix(String fileName, int suffix) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return fileName + "_" + suffix;
        return fileName.substring(0, dot) + "_" + suffix + fileName.substring(dot);
    }
}
