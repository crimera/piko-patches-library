/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import android.net.Uri;
import android.provider.DocumentsContract;

/** Storage Access Framework URI helpers shared by the destination code and the folder helpers. */
public final class SafPaths {
    private SafPaths() {
    }

    /** The tree's root document. Throws when the value is not a tree URI, as after a hand-edited or foreign restore. */
    public static Uri directoryUri(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    /** The children of a directory document. */
    public static Uri childDocumentsUri(Uri directoryDocument) {
        return DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryDocument,
                DocumentsContract.getDocumentId(directoryDocument)
        );
    }
}
