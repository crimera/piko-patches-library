package app.morphe.extension.crimera.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Test;

/**
 * Guards the contract apps rely on when they supply their own look: the installed theme is what the
 * widgets read, brightness picks the right palette, and a palette with a gap fails when it is built
 * instead of rendering a role as transparent black.
 */
public final class PikoThemeTest {
    @After
    public void restoreBaselineTheme() {
        PikoTheme.install(null);
    }

    @Test
    public void installedThemeSuppliesEveryRoleForTheCurrentBrightness() {
        SettingsColorScheme light = SettingsColorScheme.builder()
                .from(SettingsColorScheme.baselineLight())
                .set(SettingsColor.ACCENT, 0xFFE91E63)
                .build();
        SettingsColorScheme dark = SettingsColorScheme.builder()
                .from(SettingsColorScheme.baselineDark())
                .set(SettingsColor.ACCENT, 0xFFF48FB1)
                .build();
        boolean[] isDark = {false};
        PikoTheme.install(SchemeSettingsTheme.builder()
                .light(light)
                .dark(dark)
                .darkWhen(context -> isDark[0])
                .build());

        assertEquals(0xFFE91E63, PikoTheme.primaryAccent(null));
        assertFalse(PikoTheme.isDark(null));
        isDark[0] = true;
        assertEquals(0xFFF48FB1, PikoTheme.primaryAccent(null));
        assertTrue(PikoTheme.isDark(null));
        // Roles the app did not override keep the baseline it derived from.
        assertEquals(
                SettingsColorScheme.baselineDark().get(SettingsColor.SURFACE_CONTAINER_HIGH),
                PikoTheme.surfaceContainerHigh(null)
        );
    }

    @Test
    public void installingNullRestoresTheBaselineTheme() {
        PikoTheme.install(SchemeSettingsTheme.builder()
                .light(SettingsColorScheme.builder().from(SettingsColorScheme.baselineLight())
                        .set(SettingsColor.ACCENT, 0xFF123456).build())
                .build());
        assertEquals(0xFF123456, PikoTheme.primaryAccent(null));

        PikoTheme.install(null);

        assertEquals(
                SettingsColorScheme.baselineLight().get(SettingsColor.ACCENT),
                PikoTheme.primaryAccent(null)
        );
    }

    @Test
    public void blendInterpolatesEveryArgbChannel() {
        // Midpoints: ff/ff -> ff, 10/40 -> 28, 20/50 -> 38, 30/60 -> 48.
        assertEquals(0xFF283848, PikoTheme.blend(0xFF102030, 0xFF405060, 0.5f));
        assertEquals(0x7F7F7F7F, PikoTheme.blend(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFF102030, PikoTheme.blend(0xFF102030, 0xFF405060, 0f));
        assertEquals(0xFF405060, PikoTheme.blend(0xFF102030, 0xFF405060, 1f));
    }

    @Test
    public void dragHandleDefaultsToTheTextTintOverTheSheetSurface() {
        PikoTheme.install(SchemeSettingsTheme.builder()
                .light(SettingsColorScheme.baselineLight())
                .build());

        // White surface over #0F1419 text at 0.22: 0.78 * 255 + 0.22 * channel.
        assertEquals(0xFFCACBCC, PikoTheme.dragHandleColor(null));
    }

    @Test
    public void themeCanOverrideTheDragHandleColor() {
        PikoTheme.install(new SettingsTheme() {
            @Override
            public boolean isDark(Context context) {
                return false;
            }

            @Override
            public int color(Context context, SettingsColor role) {
                return 0;
            }

            @Override
            public int dragHandleColor(Context context) {
                return 0xFF555555;
            }
        });

        assertEquals(0xFF555555, PikoTheme.dragHandleColor(null));
    }

    @Test
    public void schemeWithAMissingRoleFailsAtBuildTime() {
        SettingsColorScheme.Builder partial = SettingsColorScheme.builder()
                .set(SettingsColor.SURFACE, 0xFF000000);

        IllegalStateException failure = assertThrows(IllegalStateException.class, partial::build);

        assertTrue(failure.getMessage(), failure.getMessage().contains("SURFACE_CONTAINER"));
    }

    private static void assertFalse(boolean value) {
        org.junit.Assert.assertFalse(value);
    }
}
