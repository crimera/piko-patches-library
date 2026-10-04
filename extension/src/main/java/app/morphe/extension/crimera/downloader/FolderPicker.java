/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Public entry point to pick a download folder via the system document tree picker. */
public final class FolderPicker {
    static final String EXTRA_TOKEN = "app.morphe.extension.crimera.downloader.TOKEN";

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Map<Integer, Callback> CALLBACKS = new ConcurrentHashMap<>();

    private FolderPicker() {
    }

    public interface Callback {
        /** The user picked a folder. {@code persisted} is false when the provider refused a persistable grant but the folder is writable this session. */
        void onPicked(Uri tree, String displayPath, boolean persisted);

        void onCancelled();

        /** A folder was picked but nothing can be written to it. */
        void onUnwritable();
    }

    /** Launches the folder picker activity, storing the callback until the pick completes. */
    public static void launch(Context context, Callback callback) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(callback, "callback");

        int token = COUNTER.incrementAndGet();
        CALLBACKS.put(token, callback);

        Intent intent = new Intent(context, FolderPickerActivity.class);
        intent.putExtra(EXTRA_TOKEN, token);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (RuntimeException exception) {
            CALLBACKS.remove(token);
            throw exception;
        }
    }

    static boolean hasCallback(int token) {
        return CALLBACKS.containsKey(token);
    }

    @Nullable
    static Callback takeCallback(int token) {
        return CALLBACKS.remove(token);
    }
}
