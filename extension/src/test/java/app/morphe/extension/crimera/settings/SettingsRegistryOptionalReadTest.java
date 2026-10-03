package app.morphe.extension.crimera.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

public final class SettingsRegistryOptionalReadTest {
    @Test
    public void missingContributionsUseNeutralDefaultsBeforeRegistryLoad() {
        Set<String> defaultValues = Collections.singleton("default");

        assertFalse(SettingsRegistry.getBooleanOrDefault("settings.test.missing_boolean"));
        assertTrue(SettingsRegistry.getBooleanOrDefault(
                "settings.test.missing_boolean",
                true
        ));
        assertFalse(SettingsRegistry.getBooleanOrDefault(
                "settings.test.missing_boolean",
                false
        ));
        assertEquals("", SettingsRegistry.getStringOrDefault("settings.test.missing_string"));
        assertEquals(
                "fallback",
                SettingsRegistry.getStringOrDefault("settings.test.missing_string", "fallback")
        );
        assertTrue(SettingsRegistry.getStringSetOrDefault("settings.test.missing_string_set").isEmpty());
        assertSame(
                defaultValues,
                SettingsRegistry.getStringSetOrDefault(
                        "settings.test.missing_string_set",
                        defaultValues
                )
        );
    }
}
