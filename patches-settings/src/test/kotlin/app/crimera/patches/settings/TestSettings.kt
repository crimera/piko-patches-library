package app.crimera.patches.settings

import app.morphe.patcher.patch.bytecodePatch

/** Fixture app: the config and categories the settings DSL tests contribute against. */
internal val TEST_CONFIG =
    SettingsPatchConfig(
        basePatch = bytecodePatch(name = "Test settings base", default = false) {},
        idPattern = Regex("app\\.[a-z0-9._-]+"),
        resourceNamePattern = Regex("piko_app_[a-z0-9_]+"),
        label = "Test",
    )

internal object Categories {
    val TIMELINE =
        SettingsCategory(
            id = "app.timeline",
            titleResourceName = "piko_app_category_timeline_title",
            summaryResourceName = null,
            iconResourceName = null,
            order = 100,
        )

    val CONTENT =
        SettingsCategory(
            id = "app.content",
            titleResourceName = "piko_app_category_content_title",
            summaryResourceName = null,
            iconResourceName = null,
            order = 200,
        )
}
