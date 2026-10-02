package app.morphe.extension.crimera.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LogSanitizerTest {
    @Test
    public void standardKeysAndUrlsAreRedactedButContextSurvives() {
        String sanitized = LogSanitizer.standard().sanitize(
                "authorization=abc, password=hunter2 failed fetching "
                        + "https://example.com/media?id=1 for user"
        );

        assertFalse(sanitized, sanitized.contains("abc"));
        assertFalse(sanitized, sanitized.contains("hunter2"));
        assertFalse(sanitized, sanitized.contains("example.com"));
        assertTrue(sanitized, sanitized.contains("failed fetching [url redacted] for user"));
    }

    @Test
    public void extraKeysAreRedactedOnlyWhenTheAppRegistersThem() {
        String input = "bounceDeeplink=app://secret next";

        assertEquals(input, LogSanitizer.standard().sanitize(input));
        assertEquals(
                "bounceDeeplink=[redacted] next",
                LogSanitizer.withExtraKeys("bounce[_-]?deeplink").sanitize(input)
        );
    }
}
