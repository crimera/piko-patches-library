package app.morphe.extension.crimera.theme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;

import androidx.annotation.Nullable;

/**
 * Static access to the installed {@link SettingsTheme}, used by every settings widget. Apps call
 * {@link #install} once during startup, before any settings UI is built; their own screens can read
 * the same palette through the shortcuts below to stay visually consistent.
 */
public final class PikoTheme {
    private static final SettingsTheme DEFAULT = SchemeSettingsTheme.builder().build();
    private static volatile SettingsTheme installed = DEFAULT;

    private PikoTheme() {
    }

    /** Replaces the active theme. {@code null} restores the baseline theme. */
    public static void install(@Nullable SettingsTheme theme) {
        installed = theme == null ? DEFAULT : theme;
    }

    public static SettingsTheme current() {
        return installed;
    }

    public static boolean isDark(Context context) {
        return installed.isDark(context);
    }

    public static int color(Context context, SettingsColor role) {
        return installed.color(context, role);
    }

    public static int surface(Context context) {
        return color(context, SettingsColor.SURFACE);
    }

    public static int surfaceContainer(Context context) {
        return color(context, SettingsColor.SURFACE_CONTAINER);
    }

    public static int surfaceContainerHigh(Context context) {
        return color(context, SettingsColor.SURFACE_CONTAINER_HIGH);
    }

    public static int surfaceVariant(Context context) {
        return color(context, SettingsColor.SURFACE_VARIANT);
    }

    public static int primaryText(Context context) {
        return color(context, SettingsColor.ON_SURFACE);
    }

    public static int secondaryText(Context context) {
        return color(context, SettingsColor.ON_SURFACE_VARIANT);
    }

    public static int primaryAccent(Context context) {
        return color(context, SettingsColor.ACCENT);
    }

    public static int onPrimaryAccent(Context context) {
        return color(context, SettingsColor.ON_ACCENT);
    }

    public static int primaryContainer(Context context) {
        return color(context, SettingsColor.ACCENT_CONTAINER);
    }

    public static int onPrimaryContainer(Context context) {
        return color(context, SettingsColor.ON_ACCENT_CONTAINER);
    }

    public static int dividerColor(Context context) {
        return color(context, SettingsColor.OUTLINE);
    }

    public static int checkboxChecked(Context context) {
        return color(context, SettingsColor.CHECKBOX_CHECKED);
    }

    /** Pressed-state ripple: primary text at low alpha, so it follows the installed palette. */
    public static int rippleColor(Context context) {
        int alpha = isDark(context) ? 40 : 32;
        int text = primaryText(context);
        return Color.argb(alpha, Color.red(text), Color.green(text), Color.blue(text));
    }

    public static Typeface typeface(Context context, SettingsFont font, Typeface fallback) {
        Typeface themed = installed.typeface(context, font, fallback);
        return themed == null ? fallback : themed;
    }

    public static Typeface typeface(Context context, Typeface fallback) {
        return typeface(context, SettingsFont.GENERIC, fallback);
    }

    /** Lets the app style the settings activity before it inflates. */
    public static void applyHostTheme(Activity activity) {
        installed.applyHostTheme(activity);
    }

    public static int dpToPx(@Nullable Context context, float dp) {
        if (context == null) return Math.round(dp);
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static int spToPx(@Nullable Context context, float sp) {
        if (context == null) return Math.round(sp);
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static int blend(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        float r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio;
        float g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio;
        float b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio;
        float a = Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }
}
