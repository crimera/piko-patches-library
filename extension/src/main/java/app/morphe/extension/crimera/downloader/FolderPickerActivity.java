/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.downloader.engine.DestinationCheck;

/** Trampoline activity hosting the system Storage Access Framework tree picker. */
public final class FolderPickerActivity extends Activity {
    private static final int PICK_TREE_REQUEST = 43;

    private int token;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        token = getIntent() != null ? getIntent().getIntExtra(FolderPicker.EXTRA_TOKEN, 0) : 0;
        if (!FolderPicker.hasCallback(token)) {
            finish();
            return;
        }

        if (savedInstanceState == null) {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try {
                startActivityForResult(pick, PICK_TREE_REQUEST);
            } catch (RuntimeException exception) {
                FolderPicker.Callback callback = FolderPicker.takeCallback(token);
                if (callback != null) {
                    callback.onCancelled();
                }
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_TREE_REQUEST) return;

        FolderPicker.Callback callback = FolderPicker.takeCallback(token);
        if (callback == null) {
            finish();
            return;
        }

        Uri treeUri = data == null ? null : data.getData();
        if (resultCode != RESULT_OK || treeUri == null) {
            callback.onCancelled();
            finish();
            return;
        }

        boolean persisted = true;
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (RuntimeException exception) {
            persisted = false;
        }

        if (!persisted && !DestinationCheck.hasLiveTreeAccess(getContentResolver(), treeUri)) {
            callback.onUnwritable();
            finish();
            return;
        }

        String displayPath = DestinationCheck.displayPathFor(treeUri);
        callback.onPicked(treeUri, displayPath, persisted);
        finish();
    }
}
