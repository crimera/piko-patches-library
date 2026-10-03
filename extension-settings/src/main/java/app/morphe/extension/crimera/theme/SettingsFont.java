package app.morphe.extension.crimera.theme;

/**
 * Typography roles a {@link SettingsTheme} may restyle. The fallback passed to
 * {@link SettingsTheme#typeface} is what the widget would use without a theme.
 */
public enum SettingsFont {
    /** Anything without a dedicated role: dialog text, buttons, inputs, toolbar. */
    GENERIC,
    /** Preference row titles (medium weight in the default layout). */
    ROW_TITLE,
    /** Preference row summaries (regular weight in the default layout). */
    ROW_SUMMARY,
}
