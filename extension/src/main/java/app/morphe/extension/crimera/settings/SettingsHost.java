package app.morphe.extension.crimera.settings;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import app.morphe.extension.crimera.logging.PikoLogger;
import app.morphe.extension.shared.StringRef;

/**
 * Everything app-specific about the settings system, installed once at startup before
 * {@link SettingsRegistry#load()} runs.
 *
 * <p>Look and feel is configured separately through {@code PikoTheme.install}; the host carries the
 * rest: where diagnostics go, which resources the UI reads, what the app registers itself, and what
 * must be loaded before a backup.
 *
 * <pre>{@code
 * SettingsHost.install(SettingsHost.builder(MY_LOGGER, "myapp_")
 *         .backupFilePrefix("myapp_settings_")
 *         .contributor(MyBuiltInSettings::register)
 *         .build());
 * }</pre>
 */
public final class SettingsHost {
    /** Registers settings the app owns natively, in addition to patch-injected contributions. */
    public interface Contributor {
        void register();
    }

    private static volatile SettingsHost installed;

    public final PikoLogger logger;
    public final String backupFilePrefix;
    private final String stringPrefix;
    @Nullable private final String backIconResourceName;
    @Nullable private final String searchIconResourceName;
    @Nullable private final String clearIconResourceName;
    @Nullable private final Runnable beforeBackup;
    private final List<Contributor> contributors;

    private SettingsHost(Builder builder) {
        this.logger = builder.logger;
        this.stringPrefix = builder.stringPrefix;
        this.backupFilePrefix = builder.backupFilePrefix;
        this.backIconResourceName = builder.backIconResourceName;
        this.searchIconResourceName = builder.searchIconResourceName;
        this.clearIconResourceName = builder.clearIconResourceName;
        this.beforeBackup = builder.beforeBackup;
        this.contributors = Collections.unmodifiableList(new ArrayList<>(builder.contributors));
    }

    public static void install(SettingsHost host) {
        installed = Objects.requireNonNull(host);
    }

    @Nullable
    public static SettingsHost current() {
        return installed;
    }

    /** @throws IllegalStateException when the app has not installed a host yet. */
    public static SettingsHost require() {
        SettingsHost host = installed;
        if (host == null) {
            throw new IllegalStateException(
                    "SettingsHost is not installed; call SettingsHost.install() before the settings are used"
            );
        }
        return host;
    }

    public static Builder builder(PikoLogger logger, String stringPrefix) {
        return new Builder(logger, stringPrefix);
    }

    public String resourceName(SettingsString string) {
        return stringPrefix + string.suffix;
    }

    public String string(SettingsString string, Object... args) {
        String name = resourceName(string);
        return args.length == 0 ? StringRef.str(name) : StringRef.str(name, args);
    }

    @Nullable
    String backIconResourceName() {
        return backIconResourceName;
    }

    @Nullable
    String searchIconResourceName() {
        return searchIconResourceName;
    }

    @Nullable
    String clearIconResourceName() {
        return clearIconResourceName;
    }

    List<Contributor> contributors() {
        return contributors;
    }

    void prepareBackup() {
        if (beforeBackup != null) beforeBackup.run();
    }

    public static final class Builder {
        private final PikoLogger logger;
        private final String stringPrefix;
        private String backupFilePrefix = "piko_settings_";
        @Nullable private String backIconResourceName;
        @Nullable private String searchIconResourceName;
        @Nullable private String clearIconResourceName;
        @Nullable private Runnable beforeBackup;
        private final List<Contributor> contributors = new ArrayList<>();

        private Builder(PikoLogger logger, String stringPrefix) {
            this.logger = Objects.requireNonNull(logger);
            this.stringPrefix = Objects.requireNonNull(stringPrefix);
        }

        /** Prefix of exported backup files; a timestamp and {@code .json} are appended. */
        public Builder backupFilePrefix(String prefix) {
            this.backupFilePrefix = Objects.requireNonNull(prefix);
            return this;
        }

        /**
         * Drawable resource names in the app's package. The toolbar back arrow falls back to a drawn
         * arrow, the search icon is hidden, and the clear icon falls back to the platform's.
         */
        public Builder icons(
                @Nullable String backIcon,
                @Nullable String searchIcon,
                @Nullable String clearIcon
        ) {
            this.backIconResourceName = backIcon;
            this.searchIconResourceName = searchIcon;
            this.clearIconResourceName = clearIcon;
            return this;
        }

        /**
         * Runs before a backup is exported. Settings only exist once their owners have loaded them,
         * so touch any lazily created store here.
         */
        public Builder beforeBackup(@Nullable Runnable beforeBackup) {
            this.beforeBackup = beforeBackup;
            return this;
        }

        /** Adds native registrations that run inside {@link SettingsRegistry#load()}. */
        public Builder contributor(Contributor contributor) {
            this.contributors.add(Objects.requireNonNull(contributor));
            return this;
        }

        public SettingsHost build() {
            return new SettingsHost(this);
        }
    }
}
