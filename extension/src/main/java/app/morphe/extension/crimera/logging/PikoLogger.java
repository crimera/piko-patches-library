/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.logging;

import android.util.Log;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import app.morphe.extension.shared.Logger;

/**
 * Setting-gated diagnostics for one patched app.
 *
 * <p>Morphe's {@link Logger} info and exception methods are unconditional, so every app routes
 * its diagnostics through an instance of this class instead. It owns the two switches every app
 * needs: logcat output, and an in-memory capture buffer for entries a user can export from a bug
 * report. Where the switches come from (a settings registry, shared preferences, a constant) is
 * the caller's business: pass a {@link BooleanSupplier} that is cheap enough for hot paths and
 * safe to call before the app's settings are initialised.
 */
public final class PikoLogger {
    private static final int MAX_THROWABLE_TEXT_CHARS = 4 * 1024;
    private static final int MAX_STACK_TRACE_CHARS = 12 * 1024;

    private final BooleanSupplier loggingEnabled;
    private final BooleanSupplier captureEnabled;
    private final LogSanitizer sanitizer;
    private final LogBuffer buffer = new LogBuffer();

    /**
     * @param loggingEnabled gates logcat output
     * @param captureEnabled gates {@link #capture}, which keeps sanitized entries for export
     * @param sanitizer      redacts captured entries
     */
    public PikoLogger(
            BooleanSupplier loggingEnabled,
            BooleanSupplier captureEnabled,
            LogSanitizer sanitizer
    ) {
        this.loggingEnabled = loggingEnabled;
        this.captureEnabled = captureEnabled;
        this.sanitizer = sanitizer;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled.getAsBoolean();
    }

    public boolean isCaptureEnabled() {
        return captureEnabled.getAsBoolean();
    }

    public void printInfo(Logger.LogMessage message) {
        if (!isLoggingEnabled()) return;
        Logger.printInfo(message);
    }

    public void printInfo(Logger.LogMessage message, Exception exception) {
        if (!isLoggingEnabled()) return;
        Logger.printInfo(message, exception);
    }

    public void printException(Logger.LogMessage message) {
        if (!isLoggingEnabled()) return;
        Logger.printException(message);
    }

    public void printException(Logger.LogMessage message, Throwable throwable) {
        if (!isLoggingEnabled()) return;
        Logger.printException(message, throwable);
    }

    /** Logs an arbitrary value; an {@link Exception} is logged with its stack trace. */
    public void log(Object value) {
        if (!isLoggingEnabled()) return;
        if (value instanceof Exception exception) {
            Logger.printInfo(() -> String.valueOf(value), exception);
            return;
        }
        Logger.printInfo(() -> String.valueOf(value));
    }

    /**
     * Records a sanitized, bounded entry for later export. Diagnostics never affect app
     * behavior, so any failure while capturing is swallowed.
     *
     * @param event     short event name, for example {@code server_error}
     * @param operation optional detail rendered after the event; sanitized
     * @param throwable optional cause; its message and stack trace are sanitized and bounded
     */
    public void capture(String event, @Nullable String operation, @Nullable Throwable throwable) {
        try {
            if (!isCaptureEnabled()) return;
            String entry = formatEntry(event, operation, throwable);
            buffer.add(entry);
            if (isLoggingEnabled()) {
                Logger.printInfo(() -> entry);
            }
        } catch (Throwable ignored) {
            // Diagnostics must never turn a handled failure into an app crash.
        }
    }

    public List<String> snapshotCaptured() {
        return buffer.snapshot();
    }

    private String formatEntry(String event, @Nullable String operation, @Nullable Throwable throwable) {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());
        String operationSuffix = operation == null
                ? ""
                : " operation=" + sanitizer.sanitize(operation);
        String throwableText = throwable == null
                ? "none"
                : boundText(sanitizer.sanitize(throwable.toString()), MAX_THROWABLE_TEXT_CHARS);
        String stackTrace = throwable == null
                ? "none"
                : boundText(
                        sanitizer.sanitize(Log.getStackTraceString(throwable)),
                        MAX_STACK_TRACE_CHARS
                );
        return "[" + timestamp + "] event=" + event + operationSuffix
                + " thread=" + Thread.currentThread().getName()
                + " type=" + (throwable == null ? "none" : throwable.getClass().getName()) + "\n"
                + "error=" + throwableText + "\n"
                + "stacktrace=\n" + stackTrace;
    }

    private static String boundText(String value, int maxChars) {
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "\n[text truncated]";
    }
}
