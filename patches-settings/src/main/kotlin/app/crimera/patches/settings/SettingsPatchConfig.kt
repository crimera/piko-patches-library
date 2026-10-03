package app.crimera.patches.settings

import app.morphe.patcher.patch.Patch

/** The shared settings registry class every app's contributions register into. */
const val SETTINGS_REGISTRY_DESCRIPTOR = "Lapp/morphe/extension/crimera/settings/SettingsRegistry;"

/**
 * Number of registers `SettingsRegistry.load()` must have for contribution payloads. The method
 * reserves them with `reserveInjectionRegisters`, and [prepareSettingsRegistryLoad] widens it when
 * a build of the registry has fewer.
 */
const val SETTINGS_REGISTRATION_REGISTER_COUNT = 6

/**
 * What differs between apps that contribute settings to the shared registry.
 *
 * @property basePatch The app's own settings patch (extension, resources, entry point, startup
 * hook). Every contribution depends on it, so the registry is prepared before anything registers.
 * @property idPattern Allowed shape of setting and group IDs, for example `newx\.[a-z0-9._-]+`.
 * @property resourceNamePattern Allowed shape of title and summary string resource names.
 * @property label App name used in error messages, for example `NewX`.
 */
class SettingsPatchConfig(
    val basePatch: Patch<*>,
    val idPattern: Regex,
    val resourceNamePattern: Regex,
    val label: String,
)
