package app.morphe.extension.crimera.downloader.events;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public final class ProgressThrottleTest {
    @Test
    public void firstCallEmits() {
        ProgressThrottle throttle = new ProgressThrottle(() -> 1000L);
        assertTrue(throttle.shouldEmit(1, 0, 1000));
    }

    @Test
    public void timeWindowRule250ms() {
        AtomicLong clock = new AtomicLong(1000L);
        ProgressThrottle throttle = new ProgressThrottle(clock::get);

        assertTrue(throttle.shouldEmit(1, 0, 1000));

        clock.set(1100L); // 100ms passed (< 250ms)
        assertFalse(throttle.shouldEmit(1, 0, 1000));

        clock.set(1249L); // 249ms passed (< 250ms)
        assertFalse(throttle.shouldEmit(1, 0, 1000));

        clock.set(1250L); // 250ms passed (>= 250ms)
        assertTrue(throttle.shouldEmit(1, 0, 1000));

        clock.set(1300L); // 50ms since last emission
        assertFalse(throttle.shouldEmit(1, 0, 1000));
    }

    @Test
    public void wholePercentAdvanceRule() {
        AtomicLong clock = new AtomicLong(1000L);
        ProgressThrottle throttle = new ProgressThrottle(clock::get);

        assertTrue(throttle.shouldEmit(1, 0, 1000)); // 0%

        // Clock does not advance; emissions depend on percent advance
        assertFalse(throttle.shouldEmit(1, 5, 1000)); // 0%
        assertFalse(throttle.shouldEmit(1, 9, 1000)); // 0%
        assertTrue(throttle.shouldEmit(1, 10, 1000)); // 1% (advanced by 1)
        assertFalse(throttle.shouldEmit(1, 15, 1000)); // 1%
        assertTrue(throttle.shouldEmit(1, 30, 1000)); // 3% (advanced by 2 >= 1)
        assertFalse(throttle.shouldEmit(1, 30, 1000)); // 3%
    }

    @Test
    public void percentAdvanceIgnoredWhenTotalBytesUnknown() {
        AtomicLong clock = new AtomicLong(1000L);
        ProgressThrottle throttle = new ProgressThrottle(clock::get);

        assertTrue(throttle.shouldEmit(1, 100, -1)); // first call
        assertFalse(throttle.shouldEmit(1, 200, -1)); // totalBytes is unknown (-1), percent cannot advance

        clock.set(1250L); // 250ms passed
        assertTrue(throttle.shouldEmit(1, 300, -1)); // emits via time window rule
    }

    @Test
    public void completionRuleAlwaysEmits() {
        AtomicLong clock = new AtomicLong(1000L);
        ProgressThrottle throttle = new ProgressThrottle(clock::get);

        // First call at 100% emits
        assertTrue(throttle.shouldEmit(1, 100, 100));

        // In the same millisecond and same percent, only the completion rule can emit
        assertTrue(throttle.shouldEmit(1, 100, 100));
    }

    @Test
    public void forgetDropsState() {
        AtomicLong clock = new AtomicLong(1000L);
        ProgressThrottle throttle = new ProgressThrottle(clock::get);

        assertTrue(throttle.shouldEmit(1, 0, 1000));
        assertFalse(throttle.shouldEmit(1, 0, 1000));

        throttle.forget(1);

        // Next call after forget is treated as first call
        assertTrue(throttle.shouldEmit(1, 0, 1000));
    }

    @Test
    public void distinctDownloadIdsAreTrackedSeparately() {
        AtomicLong clock = new AtomicLong(1000L);
        ProgressThrottle throttle = new ProgressThrottle(clock::get);

        assertTrue(throttle.shouldEmit(1, 0, 1000));
        assertTrue(throttle.shouldEmit(2, 0, 1000));

        assertFalse(throttle.shouldEmit(1, 0, 1000));
        assertFalse(throttle.shouldEmit(2, 0, 1000));
    }
}
