package app.morphe.extension.crimera.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A {@link SettingsTheme} backed by one {@link SettingsColorScheme} per brightness.
 *
 * <pre>{@code
 * PikoTheme.install(SchemeSettingsTheme.builder()
 *         .light(SettingsColorScheme.builder().from(SettingsColorScheme.baselineLight())
 *                 .set(SettingsColor.ACCENT, 0xFFE91E63).build())
 *         .dark(SettingsColorScheme.baselineDark())
 *         .darkWhen(context -> MyApp.isDarkTheme())
 *         .build());
 * }</pre>
 */
public final class SchemeSettingsTheme implements SettingsTheme {
    /** Supplies fonts for {@link SettingsFont} roles. */
    public interface TypefaceProvider {
        Typeface typeface(Context context, SettingsFont font, Typeface fallback);
    }

    private final SettingsColorScheme light;
    private final SettingsColorScheme dark;
    private final Predicate<Context> darkWhen;
    @Nullable private final TypefaceProvider typefaces;
    @Nullable private final Consumer<Activity> hostTheme;

    private SchemeSettingsTheme(Builder builder) {
        this.light = builder.light;
        this.dark = builder.dark;
        this.darkWhen = builder.darkWhen;
        this.typefaces = builder.typefaces;
        this.hostTheme = builder.hostTheme;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isDark(Context context) {
        return darkWhen.test(context);
    }

    @Override
    public int color(Context context, SettingsColor role) {
        return (isDark(context) ? dark : light).get(role);
    }

    @Override
    public Typeface typeface(Context context, SettingsFont font, Typeface fallback) {
        return typefaces == null ? fallback : typefaces.typeface(context, font, fallback);
    }

    @Override
    public void applyHostTheme(Activity activity) {
        if (hostTheme != null) hostTheme.accept(activity);
    }

    /** Whether the system is in night mode; the default brightness source. */
    public static boolean isSystemDark(@Nullable Context context) {
        if (context == null) return false;
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static final class Builder {
        private SettingsColorScheme light = SettingsColorScheme.baselineLight();
        private SettingsColorScheme dark = SettingsColorScheme.baselineDark();
        private Predicate<Context> darkWhen = SchemeSettingsTheme::isSystemDark;
        @Nullable private TypefaceProvider typefaces;
        @Nullable private Consumer<Activity> hostTheme;

        private Builder() {
        }

        public Builder light(SettingsColorScheme scheme) {
            this.light = Objects.requireNonNull(scheme);
            return this;
        }

        public Builder dark(SettingsColorScheme scheme) {
            this.dark = Objects.requireNonNull(scheme);
            return this;
        }

        /** Chooses the brightness; defaults to the system night mode. */
        public Builder darkWhen(Predicate<Context> darkWhen) {
            this.darkWhen = Objects.requireNonNull(darkWhen);
            return this;
        }

        public Builder typefaces(@Nullable TypefaceProvider typefaces) {
            this.typefaces = typefaces;
            return this;
        }

        /** Runs before the settings activity inflates, to apply the app's own resource styles. */
        public Builder hostTheme(@Nullable Consumer<Activity> hostTheme) {
            this.hostTheme = hostTheme;
            return this;
        }

        public SchemeSettingsTheme build() {
            return new SchemeSettingsTheme(this);
        }
    }
}
