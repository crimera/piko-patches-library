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

## Usage

```kotlin
dependencies {
    implementation("app.crimera:piko-patches-library:0.1.0")
}
```

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
includeBuild("../piko-patches-library")
```

## Linters

`app.crimera.tools.lint.ResolverLinter` scans patch-time resolver sources for unsafe candidate selection. `app.crimera.tools.lint.ExtensionDescriptorLinter` validates every `Lapp/morphe/extension/...` descriptor against the built extension dex. Both have Gradle `JavaExec` entrypoints consumers wire into `verification` tasks.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
