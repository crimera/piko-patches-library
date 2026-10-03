package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Test;

public final class DestinationLossTest {
    @Test
    public void unusableDestinationsAreLoss() {
        assertTrue(DestinationLoss.matches(new SecurityException("Permission Denial")));
        assertTrue(DestinationLoss.matches(new FileNotFoundException("primary:Download/Piko")));
        assertTrue(DestinationLoss.matches(new IOException(
                "Stored download folder is not a usable tree", new IllegalArgumentException("Unsupported Uri"))));
    }

    @Test
    public void transferAndInputFailuresAreNotLoss() {
        assertFalse(DestinationLoss.matches(new IOException("HTTP 503 for https://example.com/media.jpg")));
        assertFalse(DestinationLoss.matches(new IOException("Could not find an unused name for a_1.jpg")));
        assertFalse(DestinationLoss.matches(null));
        assertFalse(DestinationLoss.matches(new IllegalArgumentException("bad filename")));
    }

    @Test
    public void cyclicCauseChainEndsWithoutLoop() {
        Throwable first = new IOException("cycle A");
        Throwable second = new IOException("cycle B");
        first.initCause(second);
        second.initCause(first);

        assertFalse(DestinationLoss.matches(first));
    }

    @Test
    public void lossBeyondTheDepthBoundIsNotSeen() {
        Throwable chain = new SecurityException("deep");
        for (int i = 0; i < DestinationLoss.MAX_CAUSE_DEPTH; i++) {
            chain = new IOException("wrapper " + i, chain);
        }

        assertFalse(DestinationLoss.matches(chain));
    }
}
