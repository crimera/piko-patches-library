# Public in-app API of piko-extension-library. Apps call these from extension code that the patcher
# wires in after R8 has run, so R8 cannot see the references and must not shrink them away.
-keep class app.morphe.extension.crimera.logging.** { *; }

# Shared widgets and the theme contract. Apps reference them from extension code and install a
# SettingsTheme at runtime; R8 cannot see references that the patcher injects after shrinking.
#
# The settings screens, registry and renderer in app.morphe.extension.crimera.settings are NOT kept
# here: an app that opens them adds its own keep rule, and apps that only use the widgets (such as
# the bottom sheet) must not pay for them.
-keep class app.morphe.extension.crimera.ui.** { *; }
-keep class app.morphe.extension.crimera.theme.** { *; }
