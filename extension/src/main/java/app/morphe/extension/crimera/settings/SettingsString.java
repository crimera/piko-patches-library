package app.morphe.extension.crimera.settings;

/**
 * The string resources the settings UI needs from the host app.
 *
 * <p>Extension code ships no resources, so each app's patch adds these to its APK. The resource name
 * is the app's {@link SettingsHost} prefix plus {@link #suffix}; for a prefix of {@code piko_app_},
 * {@link #CANCEL} is {@code piko_app_settings_cancel}. {@link SettingsRegistry} verifies they all
 * exist when it freezes, so a missing one fails at startup instead of on first use.
 */
public enum SettingsString {
    /** Toolbar title of the root settings screen. */
    SETTINGS_TITLE("settings_title"),
    /** Footer on the root screen; takes the patches release version as {@code %1$s}. */
    PATCH_VERSION("patch_version"),
    SEARCH_HINT("settings_search_hint"),
    SEARCH_CLEAR("settings_search_clear"),
    /** Takes the trimmed query as {@code %1$s}. */
    SEARCH_NO_RESULTS("settings_search_no_results"),
    CANCEL("settings_cancel"),
    OK("settings_ok"),
    RESTART_TITLE("restart_title"),
    RESTART_SUMMARY("restart_summary"),
    RESTART_NOW("restart_now"),
    VALIDATION_FAILED("setting_validation_failed"),
    ACTION_FAILED("action_failed"),
    BACKUP_NO_FILE("backup_restore_no_file"),
    BACKUP_SUCCESS("backup_success"),
    BACKUP_FAILED("backup_failed"),
    RESTORE_FAILED("restore_failed");

    public final String suffix;

    SettingsString(String suffix) {
        this.suffix = suffix;
    }
}
