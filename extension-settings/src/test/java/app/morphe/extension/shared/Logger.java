package app.morphe.extension.shared;

/**
 * Test-source stand-in for {@code app.morphe.extension.shared.Logger} from the {@code compileOnly}
 * morphe-extensions-library artifact, which is absent from the unit-test runtime classpath and
 * needs {@code android.util.Log}. Counts calls so gating tests can assert nothing was emitted.
 */
public final class Logger {
    public interface LogMessage {
        String buildMessageString();
    }

    public static int emitted;

    private Logger() {
    }

    public static void printInfo(LogMessage message) {
        emitted++;
    }

    public static void printInfo(LogMessage message, Exception exception) {
        emitted++;
    }

    public static void printException(LogMessage message) {
        emitted++;
    }

    public static void printException(LogMessage message, Throwable throwable) {
        emitted++;
    }
}
