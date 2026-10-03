package app.morphe.extension.crimera.settings;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.logging.PikoLogger;
import app.morphe.extension.shared.Logger;

/**
 * Routes the settings system's diagnostics through the host's logger. Registration can fail before
 * a host is installed, so this falls back to the shared logger rather than dropping the report.
 */
final class SettingsLog {
    private SettingsLog() {
    }

    static void exception(Logger.LogMessage message) {
        PikoLogger logger = logger();
        if (logger != null) {
            logger.printException(message);
        } else {
            Logger.printException(message);
        }
    }

    static void exception(Logger.LogMessage message, Throwable throwable) {
        PikoLogger logger = logger();
        if (logger != null) {
            logger.printException(message, throwable);
        } else {
            Logger.printException(message, throwable);
        }
    }

    static void info(Logger.LogMessage message) {
        PikoLogger logger = logger();
        if (logger != null) {
            logger.printInfo(message);
        } else {
            Logger.printInfo(message);
        }
    }

    static void info(Logger.LogMessage message, Exception exception) {
        PikoLogger logger = logger();
        if (logger != null) {
            logger.printInfo(message, exception);
        } else {
            Logger.printInfo(message, exception);
        }
    }

    @Nullable
    private static PikoLogger logger() {
        SettingsHost host = SettingsHost.current();
        return host == null ? null : host.logger;
    }
}
