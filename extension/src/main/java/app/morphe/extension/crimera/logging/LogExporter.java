/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.logging;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Utils;

/** Writes captured diagnostics to a single text file in the public Downloads folder. */
public final class LogExporter {
    private static final String MIME_TYPE = "text/plain";

    private final String title;
    private final String fileName;
    private final String emptyMessage;

    /**
     * @param title        first line of the exported file
     * @param fileName     file created in Downloads; an existing file with this name is overwritten
     * @param emptyMessage body written when there are no entries
     */
    public LogExporter(String title, String fileName, String emptyMessage) {
        this.title = title;
        this.fileName = fileName;
        this.emptyMessage = emptyMessage;
    }

    /** Blocking; call from a background thread. */
    public void write(Context context, List<String> entries) throws IOException {
        String content = formatContent(context, entries);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context, content);
            return;
        }
        writeLegacy(context, content);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void writeWithMediaStore(Context context, String content) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri existing = findExisting(resolver, collection);
        if (existing != null) {
            try (OutputStream output = resolver.openOutputStream(existing, "wt")) {
                writeContent(output, content);
            }
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, downloadRelativePath());
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri destination = resolver.insert(collection, values);
        if (destination == null) {
            throw new IOException("Could not create MediaStore download");
        }

        try {
            try (OutputStream output = resolver.openOutputStream(destination, "w")) {
                writeContent(output, content);
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(destination, completed, null, null) != 1) {
                throw new IOException("Could not finalize MediaStore download");
            }
        } catch (IOException | RuntimeException exception) {
            resolver.delete(destination, null, null);
            throw exception;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri findExisting(ContentResolver resolver, Uri collection) {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] arguments = {fileName, downloadRelativePath()};
        try (Cursor cursor = resolver.query(
                collection,
                new String[]{MediaStore.MediaColumns._ID},
                selection,
                arguments,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
            return ContentUris.withAppendedId(collection, id);
        }
    }

    private void writeLegacy(Context context, String content) throws IOException {
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
        );
        if (!downloads.isDirectory() && !downloads.mkdirs()) {
            throw new IOException("Could not create the Downloads directory");
        }

        File destination = new File(downloads, fileName);
        try (OutputStream output = new FileOutputStream(destination, false)) {
            writeContent(output, content);
        }
        MediaScannerConnection.scanFile(
                context,
                new String[]{destination.getPath()},
                new String[]{MIME_TYPE},
                null
        );
    }

    private static void writeContent(OutputStream output, String content) throws IOException {
        if (output == null) throw new IOException("Could not open server log destination");
        output.write(content.getBytes(StandardCharsets.UTF_8));
    }

    private String formatContent(Context context, List<String> entries) {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());
        StringBuilder content = new StringBuilder();
        content.append(title).append("\n");
        content.append("Generated: ").append(timestamp).append("\n");
        content.append("Package: ").append(context.getPackageName()).append("\n");
        content.append("Patch version: ").append(Utils.getPatchesReleaseVersion()).append("\n");
        content.append("Entries: ").append(entries.size()).append("\n\n");
        if (entries.isEmpty()) {
            content.append(emptyMessage).append("\n");
            return content.toString();
        }

        for (String entry : entries) {
            content.append(entry).append("\n\n");
        }
        return content.toString();
    }

    private static String downloadRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/";
    }
}
