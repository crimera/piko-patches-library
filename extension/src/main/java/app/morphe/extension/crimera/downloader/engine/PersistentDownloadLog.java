/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

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
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Appends downloader diagnostics to {@code Downloads/Piko-Downloader.txt} off the transfer thread. */
public final class PersistentDownloadLog implements DownloadLog {
    public static final String FILE_NAME = "Piko-Downloader.txt";

    private static final String MIME_TYPE = "text/plain";
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://[^\\s]+");
    private static final Object FILE_LOCK = new Object();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "downloader-file-log");
        thread.setDaemon(true);
        return thread;
    });

    private final Context context;
    private final DownloadLog delegate;

    public PersistentDownloadLog(Context context, DownloadLog delegate) {
        Objects.requireNonNull(context, "context");
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext != null ? applicationContext : context;
        this.delegate = delegate != null ? delegate : DownloadLog.NOOP;
    }

    @Override
    public void info(Supplier<String> message) {
        String text = resolve(message);
        delegate.info(() -> text);
        enqueue("INFO", text, null);
    }

    @Override
    public void error(Supplier<String> message, Throwable cause) {
        String text = resolve(message);
        delegate.error(() -> text, cause);
        enqueue("ERROR", text, cause);
    }

    private void enqueue(String level, String message, Throwable cause) {
        String line = formatLine(level, message, cause, System.currentTimeMillis());
        try {
            WRITER.execute(() -> {
                try {
                    append(context, line);
                } catch (IOException | RuntimeException exception) {
                    delegate.error(() -> "Could not append downloader diagnostics to Downloads/"
                            + FILE_NAME, exception);
                }
            });
        } catch (RuntimeException exception) {
            delegate.error(() -> "Could not queue downloader diagnostics", exception);
        }
    }

    private static String resolve(Supplier<String> message) {
        if (message == null) return "";
        try {
            return message.get();
        } catch (RuntimeException exception) {
            return "Could not format diagnostic message: " + exception.getClass().getSimpleName();
        }
    }

    static String formatLine(String level, String message, Throwable cause, long timestampMs) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date(timestampMs));
        StringBuilder line = new StringBuilder(timestamp)
                .append(' ').append(level).append(' ').append(sanitize(message));
        if (cause != null) {
            line.append(" | ").append(cause.getClass().getSimpleName());
            String causeMessage = cause.getMessage();
            if (causeMessage != null && !causeMessage.isEmpty()) {
                line.append(": ").append(sanitize(causeMessage));
            }
        }
        return line.toString();
    }

    private static String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";
        String singleLine = text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return URL_PATTERN.matcher(singleLine).replaceAll("[URL redacted]");
    }

    private static void append(Context context, String line) throws IOException {
        synchronized (FILE_LOCK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appendWithMediaStore(context, line);
            } else {
                appendLegacy(context, line);
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static void appendWithMediaStore(Context context, String line) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri destination = findExisting(resolver, collection);
        if (destination != null) {
            try (OutputStream output = resolver.openOutputStream(destination, "wa")) {
                writeLine(output, line);
            }
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        destination = resolver.insert(collection, values);
        if (destination == null) {
            throw new IOException("Could not create downloader diagnostics file");
        }

        try {
            try (OutputStream output = resolver.openOutputStream(destination, "w")) {
                writeLine(output, line);
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(destination, completed, null, null) != 1) {
                throw new IOException("Could not finalize downloader diagnostics file");
            }
        } catch (IOException | RuntimeException exception) {
            resolver.delete(destination, null, null);
            throw exception;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static Uri findExisting(ContentResolver resolver, Uri collection) {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] arguments = {FILE_NAME, Environment.DIRECTORY_DOWNLOADS + "/"};
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

    private static void appendLegacy(Context context, String line) throws IOException {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloads.isDirectory() && !downloads.mkdirs()) {
            throw new IOException("Could not create the Downloads directory");
        }

        File destination = new File(downloads, FILE_NAME);
        boolean created = !destination.exists();
        try (OutputStream output = new FileOutputStream(destination, true)) {
            writeLine(output, line);
        }
        if (created) {
            MediaScannerConnection.scanFile(
                    context,
                    new String[]{destination.getPath()},
                    new String[]{MIME_TYPE},
                    null
            );
        }
    }

    private static void writeLine(OutputStream output, String line) throws IOException {
        if (output == null) throw new IOException("Could not open downloader diagnostics file");
        output.write(line.getBytes(StandardCharsets.UTF_8));
        output.write('\n');
    }
}
