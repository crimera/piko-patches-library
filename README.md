# Piko Patches Library

Shared patch-side infrastructure and resolver safeguards for [piko](https://github.com/crimera/piko) Morphe patch bundles.

## Modules

| Package | Contents |
|---|---|
| `app.crimera.patches.common` | Cardinality helpers (`requireExactlyOne` / `requireAtMostOne`), instruction data-flow tracing, scoped fingerprint matching |
| `app.crimera.patches.common.semantic` | Model introspection and typed accessor/bridge emitters |
| `app.crimera.utils` | Patch-source helpers (`changeStringAt`, `methodExtractor`, descriptor utilities) |
| `app.crimera.tools.lint` | Source-level resolver linter and extension-descriptor gate |
| `app.morphe.patches.all.misc.resources` | Piko's `AddResourcesPatch` fork with its locale set |

The `piko-extension-library` artifact (the `:extension` module) is the in-app counterpart: Java that is dexed into the patched app.

| Package | Contents |
|---|---|
| `app.morphe.extension.crimera.logging` | `PikoLogger` (setting-gated logcat output plus a bounded capture buffer), `LogSanitizer` (credential/URL redaction), `LogExporter` (writes captured entries to Downloads) |

The `piko-extension-settings` artifact (the `:extension-settings` module) is an opt-in settings system that depends on the logging artifact. Apps that only need logging do not take it.

| Package | Contents |
|---|---|
| `app.morphe.extension.crimera.settings` | `SettingsRegistry` (settings catalog and typed reads), `SettingsHost` (per-app configuration), `PikoSettingsActivity`/`PikoSettingsFragment` (screens, search, backup/restore), `SettingsUi` and `CustomScreenFragment` (building blocks for app-owned screens) |
| `app.morphe.extension.crimera.theme` | `SettingsTheme` (the interface apps implement), `SettingsColorScheme`/`SchemeSettingsTheme` (ready-made palette-based theme), `PikoTheme` (installed theme and shortcuts) |
| `app.morphe.extension.crimera.ui` | `DialogView`, `ButtonView`, `ChoiceRow`: widgets that draw only with the installed theme |

The `piko-patches-settings` artifact (the `:patches-settings` module) is the patch-side counterpart, kept separate from the root jar so a consumer takes only these classes.

| Package | Contents |
|---|---|
| `app.crimera.patches.settings` | `SettingsPatchConfig`, the declaration DSL (`contributeSettings`, `settingsToggle`, `settingsSingleChoice`, …), setting definitions and read emitters (`injectRead`, `returnVoidIfEnabled`, …), `prepareSettingsRegistryLoad`, `insertSettingsStartupHook`, `SettingsRegistrationState.inject` |

## Usage

```kotlin
dependencies {
    implementation("app.crimera:piko-patches-library:0.2.0")
}
```

Extension code is a separate artifact, taken by the module that is dexed into the app (Morphe's `extensions/shared`) and `compileOnly` by app extension modules:

```kotlin
// extensions/shared/library/build.gradle.kts
dependencies { api("app.crimera:piko-extension-library:0.2.0") }

// extensions/<app>/build.gradle.kts
dependencies { compileOnly("app.crimera:piko-extension-library:0.2.0") }
```

`PikoLogger` takes `BooleanSupplier` gates, so each app decides where its switches live and keeps a thin static facade over one instance.

The artifact is published to GitHub Packages (`maven.pkg.github.com/crimera/piko-patches-library`). Add the repository with credentials in the consumer's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/crimera/piko-patches-library")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR"))
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN"))
            }
        }
    }
}
```

For local development, substitute the published artifact with this checkout:

```kotlin
includeBuild("../piko-patches-library") {
    dependencySubstitution {
        substitute(module("app.crimera:piko-patches-library")).using(project(":"))
        substitute(module("app.crimera:piko-extension-library")).using(project(":extension"))
        substitute(module("app.crimera:piko-extension-settings")).using(project(":extension-settings"))
        substitute(module("app.crimera:piko-patches-settings")).using(project(":patches-settings"))
    }
}
```

## Settings

Add `app.crimera:piko-extension-settings` next to the logging artifact: same version, `api` in the module that is dexed into the app, `compileOnly` in app extension modules. The substitution snippet above covers local development.

Wiring an app takes four things, all from the app's own extension and patches:

1. **Strings.** Extension code ships no resources, so the app's patch adds the strings listed in `SettingsString` (for example `settings_title`, `settings_cancel`, `restart_title`). Names are the host's prefix plus the suffix, so with the prefix `myapp_` the title is `myapp_settings_title`. `SettingsRegistry` checks all of them when it freezes.
2. **Startup.** Before `SettingsRegistry.load()` runs (the app's init hook), install the theme and the host:

   ```java
   PikoTheme.install(new MyAppSettingsTheme());
   SettingsHost.install(SettingsHost.builder(MY_LOGGER, "myapp_")
           .backupFilePrefix("myapp_settings_")
           .icons("ic_arrow_back", "ic_search", "ic_close")   // optional drawables in the app
           .beforeBackup(MyStores::loadAll)                   // optional
           .contributor(MyBuiltInSettings::register)          // native registrations
           .build());
   ```

3. **Activity.** Declare a subclass of `PikoSettingsActivity` in the manifest so the component name stays in the app's package. Override `onUnhandledActivityResult` to receive results of app features launched from a settings screen.
4. **Settings.** Register categories, groups and items with the `SettingsRegistry.register*`/`configure*` methods, either natively from a `Contributor` or by injecting calls at the start of `SettingsRegistry.load()` from a patch. Read values anywhere with `getBooleanOrDefault`/`getStringOrDefault`/`getStringSetOrDefault`.

### Contributing settings from patches

Declare an app's settings from its patches with `piko-patches-settings`. The app defines one `SettingsPatchConfig` (its base settings patch, the allowed shape of IDs and string names, and an error label) and exposes thin wrappers so call sites stay short:

```kotlin
val MY_SETTINGS = SettingsPatchConfig(
    basePatch = myAppSettingsPatch,
    idPattern = Regex("myapp\\.[a-z0-9._-]+"),
    resourceNamePattern = Regex("piko_myapp_[a-z0-9_]+"),
    label = "MyApp",
)

val hideAdsPatch = bytecodePatch(name = "Hide ads") {
    val ads = settingsToggle(MY_SETTINGS, id = "myapp.ads.hide", category = Categories.FEED,
        strings = settingStrings("piko_myapp_hide_ads"), defaultValue = true)
    execute {
        // ... find the method, then read the setting in bytecode:
        ads.returnVoidIfDisabled(method, 0)
    }
}
```

Declaring a setting makes the patch depend on a contribution patch that injects the registration calls into `SettingsRegistry.load()`. The app's base patch supplies the app-specific parts: it adds its extension and resources, declares its `PikoSettingsActivity` subclass in the manifest, hooks an entry point that opens it, and calls `prepareSettingsRegistryLoad()` and `insertSettingsStartupHook(initMethod, "L…/MyAppSettingsHost;->install()V")` from its `execute` block. Mounted (root) installs cannot add a manifest activity, so they are not supported yet.

### Theming

The widgets never hard-code a color or font; they read the theme the app installs with `PikoTheme.install`. A theme answers three questions: is the UI dark, what is the color for each `SettingsColor` role, and which typeface goes with each `SettingsFont` role. It can also run code against the settings activity before it inflates (`applyHostTheme`), which is where an app applies its own resource styles.

For two fixed palettes, derive from the baselines and override what differs:

```java
PikoTheme.install(SchemeSettingsTheme.builder()
        .light(SettingsColorScheme.builder().from(SettingsColorScheme.baselineLight())
                .set(SettingsColor.ACCENT, 0xFFE91E63).build())
        .dark(SettingsColorScheme.baselineDark())
        .darkWhen(context -> MyApp.isDarkTheme())     // defaults to the system night mode
        .typefaces((context, font, fallback) -> MyFonts.apply(fallback))
        .build());
```

When colors depend on runtime state (the app's own theme chooser, dynamic system colors), implement `SettingsTheme` directly; `color` and `isDark` are called on every view build and draw, so resolve cheaply. An incomplete `SettingsColorScheme` fails at `build()`, and installing `null` restores the baseline.

## Linters

`app.crimera.tools.lint.ResolverLinter` scans patch-time resolver sources for unsafe candidate selection. `app.crimera.tools.lint.ExtensionDescriptorLinter` validates every `Lapp/morphe/extension/...` descriptor against the built extension dex. Both have Gradle `JavaExec` entrypoints consumers wire into `verification` tasks.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
