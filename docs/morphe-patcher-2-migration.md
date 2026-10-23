# Patcher 2 migration: shared-library implementation notes

Status: standalone validation passed; reviewer approval pending. The library version remains
`0.9.1`; no publication or consumer version bump is authorized by this work package.

## Dependency and runtime boundaries

- Patch-side Kotlin compiler: `2.4.21`.
- Patcher API: `2.0.0`.
- Morphe patch utilities: `1.6.0`.
- Extension compile/test API: `morphe-extensions-library:1.6.0`.
- The patch-side artifact remains JVM 11. The extension remains Java 17 source/target with
  Android minSdk 28 and its existing `NewApi` lint gate. AGP built-in Kotlin remains disabled
  because the extension is Java-only; the root Kotlin plugin is shared on the Gradle classpath.

Published-artifact-only validation is pending. A consumer composite build may substitute this
checkout, but the existing published `0.9.1` artifact is not changed by these source updates.

## Shared scoped-matching API

`app.crimera.patches.common.ShapeFingerprint` is now public with the consumer's existing
constructor arguments and defaults. It records exact declared return-type and parameter-arity
metadata at construction, without reading Patcher's internal fingerprint properties.

`scopedMatchAllOrNull` and `scopedMatchAll` keep their public context-receiver signatures and
all-match behavior. Ordinary fingerprints delegate to Patcher 2's native all-match search;
explicit `ShapeFingerprint` instances use the measured retained return-type/arity index. The
shape index stores original candidates only. It reuses a method reference only while the identical
owner remains immutable; mutable owners, including a mutable owner already present when the index
is built, and replaced owners are resolved by current method signature for each search. It does
not reuse owner-scope caches or cache instruction positions across mutations. A missing nested
class fingerprint still produces `null` from the nullable probe;
the required facade still throws via the fingerprint's patch exception.

The index is intentionally retained. The 2026-10-23 benchmark found native searches clearly
faster for the three reply-sort and inline-icon cases, but retained settings/Intent/optional
renderer cases and could not attribute Patcher's lazy shared index-build cost. The full-bundle
median alone did not authorize retiring the global shape table.

## Semantic helper comparison

The shared `app.crimera.patches.common.semantic` implementation was compared with the NewX
`SemanticIntrospection.kt` copy. The field/accessor resolution, cardinality, control-flow and
register-flow algorithms, typed bridges, current-method lookup, and branch policy are otherwise
the same. The NewX copy differs in package/imports, marks its declarations `internal`, and adds
`NewX ` to diagnostic labels/messages. The shared API remains public and app-neutral; consumers
that need NewX-prefixed diagnostics must preserve those labels at their call sites rather than
adding an app-specific prefix to this shared library.

## Lazy resource reads

`replaceXmlResources` now asks the resource context for the exact target path before checking
existence. `replaceStringsInFile` likewise retrieves the exact source file. This lets Patcher's
on-request decoder materialize an individual resource; it does not decode all resources. The
separate `AddResourcesPatch` continues to retrieve destination files by full path and keeps its
existing merge/replace-existing behavior.

## Validation record

Commands run from the library checkout:

| Command | Result |
| --- | --- |
| `./gradlew test :extension:test --no-daemon` | Passed; patch-side and extension unit tests. |
| `./gradlew :extension:lintDebug --no-daemon` | Passed, including the configured `NewApi` check. |
| `./gradlew build --no-daemon` | Passed; root jar/sources jar, extension debug/release AARs, tests and lint. |

The patch-side jar compiles with the JVM 11 target. The extension remains Java 17 source/target
and its API-minimum lint passes at the configured Android minSdk 28; no extension runtime code
or Android API usage changed in this migration.

Residual build warnings: AGP 9.1 deprecates `android.builtInKotlin=false` (removal is stated for
AGP 10), so the existing setting is retained for the Java-only extension's dependency boundary;
SDK XML processing reports a command-line tools version mismatch. Kotlin also reports pre-existing
warnings in untouched `TypeAssignability.kt`, `ExtensionDescriptorLinter.kt`, and
`AddResourcesPatch.kt`. The Gradle build reports existing features incompatible with Gradle 10.

No published-artifact-only build or publication was performed. The first consumer validation must
use the local composite substitution until a release is explicitly authorized and available.
