package app.morphe.extension.crimera.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import app.morphe.extension.shared.Logger;

/** A disabled switch must neither print nor retain anything: diagnostics are opt-in. */
public final class PikoLoggerGatingTest {
    @Before
    public void resetEmitted() {
        Logger.emitted = 0;
    }

    @Test
    public void disabledSwitchesEmitAndCaptureNothing() {
        PikoLogger logger = new PikoLogger(() -> false, () -> false, LogSanitizer.standard());

        logger.printInfo(() -> "info");
        logger.printException(() -> "error", new RuntimeException("boom"));
        logger.log("value");
        logger.capture("server_error", "op", new RuntimeException("boom"));

        assertEquals(0, Logger.emitted);
        assertTrue(logger.snapshotCaptured().isEmpty());
    }

    @Test
    public void enabledLoggingPrintsWithoutCapturing() {
        PikoLogger logger = new PikoLogger(() -> true, () -> false, LogSanitizer.standard());

        logger.printInfo(() -> "info");
        logger.log("value");

        assertEquals(2, Logger.emitted);
        assertTrue(logger.snapshotCaptured().isEmpty());
    }

    @Test
    public void failingGateIsSwallowedWhenCapturing() {
        PikoLogger logger = new PikoLogger(
                () -> false,
                () -> {
                    throw new IllegalStateException("settings not ready");
                },
                LogSanitizer.standard()
        );

        logger.capture("event", null, null);

        assertTrue(logger.snapshotCaptured().isEmpty());
    }
}
