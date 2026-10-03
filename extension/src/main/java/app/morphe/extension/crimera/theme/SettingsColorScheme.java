package app.morphe.extension.crimera.theme;

import java.util.Arrays;

/**
 * An immutable value for every {@link SettingsColor} role, used for one brightness (light or dark).
 *
 * <p>Build one from scratch with {@link #builder()} (every role must be set) or derive it from
 * {@link #baselineLight()}/{@link #baselineDark()} with {@link Builder#from} and override only the
 * roles that differ. Colors are ARGB ints. The class makes no Android calls, so it is safe to build
 * in static initializers and unit tests.
 */
public final class SettingsColorScheme {
    private static final SettingsColorScheme BASELINE_LIGHT = builder()
            .set(SettingsColor.SURFACE, 0xFFFFFFFF)
            .set(SettingsColor.SURFACE_CONTAINER, 0xFFFFFFFF)
            .set(SettingsColor.SURFACE_CONTAINER_HIGH, 0xFFEEEEEE)
            .set(SettingsColor.SURFACE_VARIANT, 0xFFEEEEEE)
            .set(SettingsColor.ON_SURFACE, 0xFF0F1419)
            .set(SettingsColor.ON_SURFACE_VARIANT, 0xFF536471)
            .set(SettingsColor.ACCENT, 0xFF1A73E8)
            .set(SettingsColor.ON_ACCENT, 0xFFFFFFFF)
            .set(SettingsColor.ACCENT_CONTAINER, 0xFFD3E3FD)
            .set(SettingsColor.ON_ACCENT_CONTAINER, 0xFF041E49)
            .set(SettingsColor.OUTLINE, 0x1F000000)
            .set(SettingsColor.CHECKBOX_CHECKED, 0xFF0F1419)
            .build();

    private static final SettingsColorScheme BASELINE_DARK = builder()
            .set(SettingsColor.SURFACE, 0xFF121212)
            .set(SettingsColor.SURFACE_CONTAINER, 0xFF121212)
            .set(SettingsColor.SURFACE_CONTAINER_HIGH, 0xFF1E1E1E)
            .set(SettingsColor.SURFACE_VARIANT, 0xFF242424)
            .set(SettingsColor.ON_SURFACE, 0xFFE6E6E6)
            .set(SettingsColor.ON_SURFACE_VARIANT, 0xFF9AA0A6)
            .set(SettingsColor.ACCENT, 0xFF8AB4F8)
            .set(SettingsColor.ON_ACCENT, 0xFF0B1F3A)
            .set(SettingsColor.ACCENT_CONTAINER, 0xFF1F3A5F)
            .set(SettingsColor.ON_ACCENT_CONTAINER, 0xFFD3E3FD)
            .set(SettingsColor.OUTLINE, 0x26FFFFFF)
            .set(SettingsColor.CHECKBOX_CHECKED, 0xFFE6E6E6)
            .build();

    private final int[] colors;

    private SettingsColorScheme(int[] colors) {
        this.colors = colors;
    }

    public int get(SettingsColor role) {
        return colors[role.ordinal()];
    }

    /** Neutral Material-style light colors, used when an app installs no theme. */
    public static SettingsColorScheme baselineLight() {
        return BASELINE_LIGHT;
    }

    /** Neutral Material-style dark colors, used when an app installs no theme. */
    public static SettingsColorScheme baselineDark() {
        return BASELINE_DARK;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final int[] colors = new int[SettingsColor.values().length];
        private final boolean[] assigned = new boolean[colors.length];

        private Builder() {
        }

        /** Starts from every role of {@code base}; later {@link #set} calls override them. */
        public Builder from(SettingsColorScheme base) {
            for (SettingsColor role : SettingsColor.values()) {
                set(role, base.get(role));
            }
            return this;
        }

        public Builder set(SettingsColor role, int color) {
            colors[role.ordinal()] = color;
            assigned[role.ordinal()] = true;
            return this;
        }

        /** @throws IllegalStateException when a role has no color, so a gap fails at install time. */
        public SettingsColorScheme build() {
            for (SettingsColor role : SettingsColor.values()) {
                if (!assigned[role.ordinal()]) {
                    throw new IllegalStateException("Settings color scheme is missing " + role);
                }
            }
            return new SettingsColorScheme(Arrays.copyOf(colors, colors.length));
        }
    }
}
