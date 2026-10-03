#!/usr/bin/env bash
# Called by semantic-release (@semantic-release/exec prepareCmd) once the next version is known.
# Stamps the version, then builds, verifies and tests the artifacts that get released.
set -euo pipefail

VERSION="${1:?usage: release-prepare.sh <version>}"

sed -i.bak "s/^libraryVersion = .*/libraryVersion = ${VERSION}/" gradle.properties
rm gradle.properties.bak
grep -qx "libraryVersion = ${VERSION}" gradle.properties

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

./gradlew :extension-settings:assembleRelease --no-daemon
SETTINGS_AAR=extension-settings/build/outputs/aar/extension-settings-release.aar
unzip -p "$SETTINGS_AAR" classes.jar > "$RUNNER_TEMP/extension-settings-classes.jar"
unzip -l "$RUNNER_TEMP/extension-settings-classes.jar" | grep 'app/morphe/extension/crimera/settings/SettingsRegistry.class' >/dev/null
unzip -l "$RUNNER_TEMP/extension-settings-classes.jar" | grep 'app/morphe/extension/crimera/theme/PikoTheme.class' >/dev/null
echo "$SETTINGS_AAR: settings classes verified"

./gradlew :patches-settings:jar --no-daemon
SETTINGS_JAR="patches-settings/build/libs/patches-settings-${VERSION}.jar"
unzip -l "$SETTINGS_JAR" | grep 'app/crimera/patches/settings/SettingsContributionKt.class' >/dev/null
echo "$SETTINGS_JAR: patch-side settings classes verified"

./gradlew test --no-daemon

# The files attached to the GitHub release; the AARs get versioned names.
rm -rf release-assets
mkdir release-assets
cp "$JAR" "$SETTINGS_JAR" release-assets/
cp "$AAR" "release-assets/piko-extension-library-${VERSION}.aar"
cp "$SETTINGS_AAR" "release-assets/piko-extension-settings-${VERSION}.aar"
