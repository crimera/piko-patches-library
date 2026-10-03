# Piko Patches Library

Shared patch-side infrastructure and resolver safeguards for [piko](https://github.com/crimera/piko) Morphe patch bundles.

## Modules

| Package | Contents |
|---|---|
| `app.crimera.patches.common` | Cardinality helpers (`requireExactlyOne` / `requireAtMostOne`), instruction data-flow tracing, scoped fingerprint matching |
| `app.crimera.patches.common.semantic` | Model introspection and typed accessor/bridge emitters |
| `app.crimera.utils` | Patch-source helpers (`changeStringAt`, `methodExtractor`, descriptor utilities) |
| `app.crimera.tools.lint` | Source-level resolver linter and extension-descriptor gate |
| `app.crimera.patches.settings` | `SettingsPatchConfig`, the declaration DSL (`contributeSettings`, `settingsToggle`, `settingsSingleChoice`, …), setting definitions and read emitters (`injectRead`, `returnVoidIfEnabled`, …), `prepareSettingsRegistryLoad`, `insertSettingsStartupHook`, `SettingsRegistrationState.inject` |
| `app.morphe.patches.all.misc.resources` | Piko's `AddResourcesPatch` fork with its locale set |

The `piko-extension-library` artifact (the `:extension` module) is the in-app counterpart: Java that is dexed into the patched app.

| Package | Contents |
|---|---|
| `app.morphe.extension.crimera.logging` | `PikoLogger` (setting-gated logcat output plus a bounded capture buffer), `LogSanitizer` (credential/URL redaction), `LogExporter` (writes captured entries to Downloads) |

The same artifact carries an opt-in settings system, themeable and driven by the patch-side settings DSL above. Apps that only need logging simply never touch it.

| Package | Contents |
|---|---|
| `app.morphe.extension.crimera.settings` | `SettingsRegistry` (settings catalog and typed reads), `SettingsHost` (per-app configuration), `PikoSettingsActivity`/`PikoSettingsFragment` (screens, search, backup/restore), `SettingsUi` and `CustomScreenFragment` (building blocks for app-owned screens) |
| `app.morphe.extension.crimera.theme` | `SettingsTheme` (the interface apps implement), `SettingsColorScheme`/`SchemeSettingsTheme` (ready-made palette-based theme), `PikoTheme` (installed theme and shortcuts) |
| `app.morphe.extension.crimera.ui` | `DialogView`, `ButtonView`, `ChoiceRow`, `BottomSheetView` + `ListItem`/`IconView`: widgets that draw only with the installed theme |

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
    }
}
```

## Settings

The settings system ships in `app.crimera:piko-extension-library` (in-app code) and `app.crimera:piko-patches-library` (the patch-side DSL), so there is nothing extra to add: use the same dependency declarations as above, at the same version.

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

Declare an app's settings from its patches with the `app.crimera.patches.settings` DSL. The app defines one `SettingsPatchConfig` (its base settings patch, the allowed shape of IDs and string names, and an error label) and exposes thin wrappers so call sites stay short:

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

### Bottom sheets

`BottomSheetView` is the shared sheet container: edge-to-edge, swipe-down to dismiss, Compose Material 3 slide motion, a title/subtitle header, an optional scrollable body of `ListItem` rows and one or more `ButtonView` actions. It reads the same installed theme as every other widget:

- Sheet background `SURFACE_CONTAINER`, title `ON_SURFACE`, subtitle `ON_SURFACE_VARIANT`, dividers `OUTLINE`, pressed states `rippleColor`.
- `ListItem` badges sit on `SURFACE_VARIANT`; the caller passes `ACCENT_CONTAINER` (`PikoTheme.primaryContainer`) for selected rows.
- The drag handle is `SettingsTheme.dragHandleColor`, a 22% tint of `ON_SURFACE` over `SURFACE_CONTAINER` by default; a host whose design system ships a dedicated handle color (Instagram's creation-tools grey) overrides that method.

```java
BottomSheetView sheet = new BottomSheetView(activity);
sheet.setTitle("Download");

ListItem row = new ListItem(activity);
row.setTitle("Video 1");
row.setLeadingIcon(IconView.IconType.VIDEO, PikoTheme.primaryAccent(activity),
        PikoTheme.surfaceVariant(activity));

sheet.setScrollableBodyView(rows);
sheet.addButton(new ButtonView(activity, ButtonView.ButtonStyle.FILLED, "Download all"));
sheet.show();
```

Install the theme before the first sheet is built. piko-ig-lite's `DownloadSheet` and its `InstagramSheetTheme` are a worked example of an app theme that resolves the host's own theme attributes.

## Linters

`app.crimera.tools.lint.ResolverLinter` scans patch-time resolver sources for unsafe candidate selection. `app.crimera.tools.lint.ExtensionDescriptorLinter` validates every `Lapp/morphe/extension/...` descriptor against the built extension dex. Both have Gradle `JavaExec` entrypoints consumers wire into `verification` tasks.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
