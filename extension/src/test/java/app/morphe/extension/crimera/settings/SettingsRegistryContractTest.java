package app.morphe.extension.crimera.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Consumer patches inject bytecode into {@link SettingsRegistry#load()} and call its registration
 * methods by descriptor. Renaming or reshaping them compiles fine here and fails only on a device,
 * so the patch-facing surface is pinned.
 */
public final class SettingsRegistryContractTest {
    @Test
    public void patchFacingEntryPointsKeepTheirShape() throws Exception {
        Method load = SettingsRegistry.class.getDeclaredMethod("load");
        assertTrue(Modifier.isStatic(load.getModifiers()));
        assertTrue(Modifier.isPublic(load.getModifiers()));
        assertEquals(void.class, load.getReturnType());

        Method isLoaded = SettingsRegistry.class.getDeclaredMethod("isLoaded");
        assertEquals(boolean.class, isLoaded.getReturnType());

        SettingsRegistry.class.getDeclaredMethod(
                "registerCategory", String.class, String.class, String.class, String.class, int.class);
        SettingsRegistry.class.getDeclaredMethod(
                "registerGroup", String.class, String.class, String.class, String.class, String.class, int.class);
        SettingsRegistry.class.getDeclaredMethod("configureToggle", String.class, boolean.class, boolean.class);
        SettingsRegistry.class.getDeclaredMethod("getBooleanOrDefault", String.class, boolean.class);
        SettingsRegistry.class.getDeclaredMethod("getStringOrDefault", String.class, String.class);
    }
}
