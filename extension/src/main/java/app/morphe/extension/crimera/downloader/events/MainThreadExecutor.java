/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.events;

import android.os.Handler;
import android.os.Looper;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Dispatches tasks onto Android's main looper.
 */
public final class MainThreadExecutor implements Executor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        handler.post(command);
    }
}
