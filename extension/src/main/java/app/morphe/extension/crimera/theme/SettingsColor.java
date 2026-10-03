package app.morphe.extension.crimera.theme;

/**
 * The color roles every settings widget draws with. An app supplies one value per role through a
 * {@link SettingsTheme}; the widgets never hard-code a color.
 *
 * <p>The roles follow Material 3 naming. "Accent" is the app's primary brand color.
 */
public enum SettingsColor {
    /** Window background of the settings screens and the base of dividers/ripples. */
    SURFACE,
    /** Toolbar, status bar and list background. Usually the same as {@link #SURFACE}. */
    SURFACE_CONTAINER,
    /** Raised surfaces: dialogs and the search field. */
    SURFACE_CONTAINER_HIGH,
    /** Focused/secondary raised surface, such as the focused search field. */
    SURFACE_VARIANT,

    /** Primary text and icons drawn on the surfaces. */
    ON_SURFACE,
    /** Secondary text (summaries, hints) and unchecked indicators. */
    ON_SURFACE_VARIANT,

    /** Switch track, filled/text buttons, search highlights. */
    ACCENT,
    /** Content drawn on {@link #ACCENT} (filled button label). */
    ON_ACCENT,
    /** Tonal button background. */
    ACCENT_CONTAINER,
    /** Content drawn on {@link #ACCENT_CONTAINER}. */
    ON_ACCENT_CONTAINER,

    /** Dividers and outlines. May carry an alpha channel. */
    OUTLINE,
    /** Checked multi-choice indicator fill. */
    CHECKBOX_CHECKED,
}
