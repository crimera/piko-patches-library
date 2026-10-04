package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.UnknownHostException;

import org.junit.Test;

import app.morphe.extension.crimera.downloader.events.FailureReason;

public final class FailureClassifierTest {
    @Test
    public void unknownHostIsNoConnection() {
        assertEquals(FailureReason.NO_CONNECTION, FailureClassifier.classify(new UnknownHostException("dns")));
        assertEquals(FailureReason.NO_CONNECTION,
                FailureClassifier.classify(new IOException("wrapped", new UnknownHostException("dns"))));
    }

    @Test
    public void lostDestinationWinsOverLostConnection() {
        Throwable both = new SecurityException("revoked", new UnknownHostException("dns"));

        assertEquals(FailureReason.DESTINATION_LOST, FailureClassifier.classify(both));
    }

    @Test
    public void everythingElseIsUnknown() {
        assertEquals(FailureReason.UNKNOWN, FailureClassifier.classify(new IOException("HTTP 503")));
        assertEquals(FailureReason.UNKNOWN, FailureClassifier.classify(null));
    }

    @Test
    public void cyclicCauseChainEndsWithoutLoop() {
        Throwable first = new IOException("cycle A");
        Throwable second = new IOException("cycle B");
        first.initCause(second);
        second.initCause(first);

        assertFalse(FailureClassifier.isNoConnection(first));
        assertTrue(FailureClassifier.isNoConnection(new UnknownHostException("dns")));
    }
}
