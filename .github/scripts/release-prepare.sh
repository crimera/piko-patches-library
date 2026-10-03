#!/usr/bin/env bash
# Called by semantic-release (@semantic-release/exec prepareCmd) once the next version is known.
# Builds, verifies and tests the artifacts that get released.
set -euo pipefail

VERSION="${1:?usage: release-prepare.sh <version>}"

# gradle-semantic-release-plugin has already stamped the version into gradle.properties.
grep -Eq "^version[[:space:]]*=[[:space:]]*${VERSION}[[:space:]]*$" gradle.properties

./gradlew jar --no-daemon
JAR="build/libs/piko-patches-library-${VERSION}.jar"
unzip -l "$JAR" | grep 'app/crimera/patches/common/ResolverCardinalityKt.class' >/dev/null
unzip -l "$JAR" | grep 'app/crimera/tools/lint/ResolverLinter.class' >/dev/null
unzip -l "$JAR" | grep 'META-INF/NOTICE' >/dev/null
echo "$JAR: classes and notices verified"

./gradlew :extension:assembleRelease --no-daemon
AAR=extension/build/outputs/aar/extension-release.aar
unzip -p "$AAR" classes.jar > "$RUNNER_TEMP/extension-classes.jar"
unzip -l "$RUNNER_TEMP/extension-classes.jar" | grep 'app/morphe/extension/crimera/logging/PikoLogger.class' >/dev/null
echo "$AAR: extension classes verified"

unzip -l "$RUNNER_TEMP/extension-classes.jar" | grep 'app/morphe/extension/crimera/settings/SettingsRegistry.class' >/dev/null
unzip -l "$RUNNER_TEMP/extension-classes.jar" | grep 'app/morphe/extension/crimera/theme/PikoTheme.class' >/dev/null
echo "$AAR: settings classes verified"

unzip -l "$JAR" | grep 'app/crimera/patches/settings/SettingsContributionKt.class' >/dev/null
echo "$JAR: patch-side settings classes verified"

./gradlew test --no-daemon

# The files attached to the GitHub release; the AARs get versioned names.
rm -rf release-assets
mkdir release-assets
cp "$JAR" release-assets/
cp "$AAR" "release-assets/piko-extension-library-${VERSION}.aar"
