/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.content.ContentResolver;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

/** Storage Access Framework destination checks and path helpers. */
public final class DestinationCheck {
    private DestinationCheck() {
    }

    /** True when this tree URI has a persisted write grant from the system. */
    public static boolean hasPersistedWritePermission(ContentResolver resolver, Uri tree) {
        String treeDocumentId = treeDocumentIdOf(tree);
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (!permission.isWritePermission()) continue;

            Uri granted = permission.getUri();
            if (granted.equals(tree)) return true;

            // Providers may normalize the uri, so compare document ids too. Authority must
            // match so a grant from another device with the same id cannot validate this tree.
            if (!sameAuthority(granted, tree)) continue;
            String grantedDocumentId = treeDocumentIdOf(granted);
            if (treeDocumentId != null && treeDocumentId.equals(grantedDocumentId)) return true;
        }
        return false;
    }

    /** True when both URIs share the same authority. */
    public static boolean sameAuthority(Uri left, Uri right) {
        String authority = left.getAuthority();
        return authority != null && authority.equals(right.getAuthority());
    }

    /**
     * Whether the tree answers right now. Not gated on advertised flags: some writable
     * providers omit the create flag, and honoring it would leave no pickable folder.
     */
    public static boolean hasLiveTreeAccess(ContentResolver resolver, Uri tree) {
        try {
            String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID};
            try (Cursor cursor = resolver.query(SafPaths.directoryUri(tree), projection, null, null, null)) {
                return cursor != null && cursor.moveToFirst();
            }
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Human-readable path for a tree URI, falling back to the raw document id or URI. */
    public static String displayPathFor(Uri treeUri) {
        try {
            return displayPathFor(DocumentsContract.getTreeDocumentId(treeUri));
        } catch (RuntimeException exception) {
            return treeUri.toString();
        }
    }

    static String displayPathFor(String documentId) {
        if (documentId.startsWith("primary:")) {
            return "/" + documentId.substring("primary:".length());
        }
        int colon = documentId.indexOf(':');
        return colon > 0 ? documentId.substring(0, colon) + "/" + documentId.substring(colon + 1)
                : documentId;
    }

    @Nullable
    static String treeDocumentIdOf(Uri uri) {
        if (uri == null) return null;
        try {
            return DocumentsContract.getTreeDocumentId(uri);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
