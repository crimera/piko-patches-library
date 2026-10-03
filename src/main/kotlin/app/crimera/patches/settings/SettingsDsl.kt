package app.crimera.patches.settings

import app.morphe.patcher.patch.BytecodePatchBuilder

data class SettingStrings(
    val titleResourceName: String,
    val summaryResourceName: String?,
)

fun settingStrings(
    baseName: String,
    summary: Boolean = true,
) =
    SettingStrings(
        titleResourceName = "${baseName}_title",
        summaryResourceName = if (summary) "${baseName}_summary" else null,
    )

fun settingStrings(
    titleResourceName: String,
    summaryResourceName: String?,
) = SettingStrings(titleResourceName, summaryResourceName)

fun choice(
    id: String,
    titleResourceName: String,
) = ChoiceOption(id, titleResourceName)

fun <T> SettingsGroupBuilder.group(
    id: String,
    strings: SettingStrings,
    iconResourceName: String? = null,
    order: Int = 0,
    block: SettingsGroupBuilder.() -> T,
): T =
    group(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        iconResourceName = iconResourceName,
        order = order,
        block = block,
    )

fun <T> SettingsGroupBuilder.group(
    group: SettingsGroupMetadata,
    block: SettingsGroupBuilder.() -> T,
): T =
    group(
        id = group.id,
        titleResourceName = group.titleResourceName,
        summaryResourceName = group.summaryResourceName,
        iconResourceName = group.iconResourceName,
        order = group.order,
        block = block,
    )

fun SettingsGroupBuilder.toggle(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Boolean,
    rebootApp: Boolean = false,
    visible: Boolean = true,
): ToggleSettingDefinition =
    toggle(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        visible = visible,
    )

fun SettingsGroupBuilder.input(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    inputKind: InputKind = InputKind.TEXT,
    validatorClassDescriptor: String? = null,
): TextInputSettingDefinition =
    input(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        visible = visible,
        inputKind = inputKind,
        validatorClassDescriptor = validatorClassDescriptor,
    )

fun SettingsGroupBuilder.singleChoice(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    options: List<ChoiceOption>,
): SingleChoiceSettingDefinition =
    singleChoice(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        visible = visible,
        options = options,
    )

fun SettingsGroupBuilder.multiChoice(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Set<String>,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    options: List<ChoiceOption>,
): MultiChoiceSettingDefinition =
    multiChoice(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        visible = visible,
        options = options,
    )

fun SettingsGroupBuilder.action(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    handlerClassDescriptor: String,
    visible: Boolean = true,
): ActionSettingDefinition =
    action(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        handlerClassDescriptor = handlerClassDescriptor,
        visible = visible,
    )

fun SettingsGroupBuilder.customScreen(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    fragmentClassDescriptor: String,
    iconResourceName: String? = null,
): CustomScreenSettingDefinition =
    customScreen(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        fragmentClassDescriptor = fragmentClassDescriptor,
        iconResourceName = iconResourceName,
    )

/**
 * Declares settings from a patch and makes the patch depend on the contribution that registers them.
 * The returned definitions are what the patch uses to read the settings in bytecode.
 */
fun <T> BytecodePatchBuilder.contributeSettings(
    config: SettingsPatchConfig,
    block: SettingsContributionBuilder.() -> T,
): T {
    val builder = SettingsContributionBuilder(config)
    val result = builder.block()
    val catalog = builder.build()
    SettingsContributionIndex.register(catalog)
    dependsOn(settingsContributionPatch(config.basePatch, catalog))
    return result
}

fun BytecodePatchBuilder.settingsToggle(
    config: SettingsPatchConfig,
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Boolean,
    rebootApp: Boolean = false,
    visible: Boolean = true,
): ToggleSettingDefinition =
    contributeSettings(config) {
        category(category) {
            toggle(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                visible = visible,
            )
        }
    }

fun BytecodePatchBuilder.settingsTextInput(
    config: SettingsPatchConfig,
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    inputKind: InputKind = InputKind.TEXT,
    validatorClassDescriptor: String? = null,
): TextInputSettingDefinition =
    contributeSettings(config) {
        category(category) {
            input(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                visible = visible,
                inputKind = inputKind,
                validatorClassDescriptor = validatorClassDescriptor,
            )
        }
    }

fun BytecodePatchBuilder.settingsSingleChoice(
    config: SettingsPatchConfig,
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    options: List<ChoiceOption>,
): SingleChoiceSettingDefinition =
    contributeSettings(config) {
        category(category) {
            singleChoice(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                visible = visible,
                options = options,
            )
        }
    }

fun BytecodePatchBuilder.settingsMultiChoice(
    config: SettingsPatchConfig,
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Set<String>,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    options: List<ChoiceOption>,
): MultiChoiceSettingDefinition =
    contributeSettings(config) {
        category(category) {
            multiChoice(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                visible = visible,
                options = options,
            )
        }
    }

fun BytecodePatchBuilder.settingsCustomScreen(
    config: SettingsPatchConfig,
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    fragmentClassDescriptor: String,
    iconResourceName: String? = null,
): CustomScreenSettingDefinition =
    contributeSettings(config) {
        category(category) {
            customScreen(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                fragmentClassDescriptor = fragmentClassDescriptor,
                iconResourceName = iconResourceName,
            )
        }
    }

fun BytecodePatchBuilder.settingsAction(
    config: SettingsPatchConfig,
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    handlerClassDescriptor: String,
    visible: Boolean = true,
): ActionSettingDefinition =
    contributeSettings(config) {
        category(category) {
            action(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                handlerClassDescriptor = handlerClassDescriptor,
                visible = visible,
            )
        }
    }
