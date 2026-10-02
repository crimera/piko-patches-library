package app.morphe.extension.crimera.theme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;

/**
 * Everything the settings screens and widgets ask the host app about its look. An app installs one
 * with {@link PikoTheme#install}; until then {@link SchemeSettingsTheme} baseline colors apply.
 *
 * <p>Implement this directly when colors depend on runtime state (the app's own theme preference,
 * dynamic system colors). Use {@link SchemeSettingsTheme} when two fixed palettes are enough.
 * Methods are called on the UI thread, once per view build or draw, so they should be cheap.
 */
public interface SettingsTheme {
    /** How far {@link #dragHandleColor} leans towards {@link SettingsColor#ON_SURFACE}. */
    float DRAG_HANDLE_TINT = 0.22f;

    /** Whether the settings UI is currently dark. Drives system bar icons and the dialog base. */
    boolean isDark(Context context);

    /** The color for {@code role} in the current brightness. */
    int color(Context context, SettingsColor role);

    /**
     * Drag handle of a bottom sheet. The default is a neutral tint of the primary text over the
     * sheet surface; an app whose design system ships a dedicated handle color (Instagram's
     * creation-tools grey, for example) overrides this instead of restyling the surface roles.
     */
    default int dragHandleColor(Context context) {
        return PikoTheme.blend(
                color(context, SettingsColor.SURFACE_CONTAINER),
                color(context, SettingsColor.ON_SURFACE),
                DRAG_HANDLE_TINT
        );
    }

    /**
     * Restyles text. {@code fallback} is the typeface the widget would use unchanged; return it
     * when the app has no custom font.
     */
    default Typeface typeface(Context context, SettingsFont font, Typeface fallback) {
        return fallback;
    }

    /**
     * Called before the settings activity inflates, so the app can apply its own resource styles to
     * the activity's theme. The default does nothing.
     */
    default void applyHostTheme(Activity activity) {
    }
}
