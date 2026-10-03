package app.crimera.patches.settings

private val OPTION_ID_PATTERN = Regex("[a-zA-Z0-9._-]+")
private val DRAWABLE_RESOURCE_NAME_PATTERN = Regex("[a-z][a-z0-9_]+")
private val HANDLER_DESCRIPTOR_PATTERN = Regex("L[a-zA-Z0-9_$/]+;")

// ── Settings model definitions ───────────────────────────────────────────

sealed interface SettingsNodeDefinition {
    val id: String
    val titleResourceName: String
    val summaryResourceName: String?
    val order: Int
}

data class SettingsGroupDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    val iconResourceName: String?,
    override val order: Int,
    val children: List<SettingsNodeDefinition>,
) : SettingsNodeDefinition

sealed interface SettingItemDefinition : SettingsNodeDefinition {
    val visible: Boolean
        get() = true
}

sealed interface ValueSettingDefinition<T> : SettingItemDefinition {
    val defaultValue: T
    val rebootApp: Boolean
}

data class ToggleSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: Boolean,
    override val rebootApp: Boolean = false,
    override val visible: Boolean = true,
) : ValueSettingDefinition<Boolean>

enum class InputKind {
    TEXT,
    MULTILINE,
}

data class TextInputSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: String,
    override val rebootApp: Boolean = false,
    val inputKind: InputKind = InputKind.TEXT,
    val validatorClassDescriptor: String? = null,
    override val visible: Boolean = true,
) : ValueSettingDefinition<String>

data class ChoiceOption(
    val id: String,
    val titleResourceName: String,
)

data class SingleChoiceSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: String,
    override val rebootApp: Boolean = false,
    val options: List<ChoiceOption>,
    override val visible: Boolean = true,
) : ValueSettingDefinition<String>

data class MultiChoiceSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: Set<String>,
    override val rebootApp: Boolean = false,
    val options: List<ChoiceOption>,
    override val visible: Boolean = true,
) : ValueSettingDefinition<Set<String>>

data class ActionSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    val handlerClassDescriptor: String,
    override val visible: Boolean = true,
) : SettingItemDefinition

data class CustomScreenSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    val fragmentClassDescriptor: String,
    val iconResourceName: String? = null,
) : SettingItemDefinition

data class SettingsContributionCatalog(
    val categories: List<SettingsGroupDefinition>,
)

// ── Builder ──────────────────────────────────────────────────────────────

class SettingsContributionBuilder(
    private val config: SettingsPatchConfig,
) {
    private val categoryBuilders = linkedMapOf<SettingsCategory, SettingsGroupBuilder>()

    fun <T> category(
        category: SettingsCategory,
        block: SettingsGroupBuilder.() -> T,
    ): T {
        val builder =
            categoryBuilders.getOrPut(category) {
                SettingsGroupBuilder(
                    id = category.id,
                    titleResourceName = category.titleResourceName,
                    summaryResourceName = category.summaryResourceName,
                    iconResourceName = category.iconResourceName,
                    order = category.order,
                )
            }
        return builder.block()
    }

    fun build(): SettingsContributionCatalog {
        val catalog =
            SettingsContributionCatalog(
                categories =
                    categoryBuilders
                        .values
                        .map(SettingsGroupBuilder::build)
                        .sortedWith(nodeComparator),
            )
        validateSettingsContribution(catalog, config)
        return catalog
    }
}

class SettingsGroupBuilder(
    private val id: String,
    private val titleResourceName: String,
    private val summaryResourceName: String?,
    private val iconResourceName: String?,
    private val order: Int,
) {
    private val children = mutableListOf<SettingsNodeDefinition>()

    fun <T : SettingItemDefinition> add(definition: T): T {
        children += definition
        return definition
    }

    fun <T> group(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        iconResourceName: String? = null,
        order: Int = 0,
        block: SettingsGroupBuilder.() -> T,
    ): T {
        val builder =
            SettingsGroupBuilder(
                id,
                titleResourceName,
                summaryResourceName,
                iconResourceName,
                order,
            )
        val result = builder.block()
        children += builder.build()
        return result
    }

    fun toggle(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: Boolean,
        rebootApp: Boolean = false,
        visible: Boolean = true,
    ): ToggleSettingDefinition =
        add(
            ToggleSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
                visible,
            ),
        )

    fun input(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: String,
        rebootApp: Boolean = false,
        inputKind: InputKind = InputKind.TEXT,
        validatorClassDescriptor: String? = null,
        visible: Boolean = true,
    ): TextInputSettingDefinition =
        add(
            TextInputSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
                inputKind,
                validatorClassDescriptor,
                visible,
            ),
        )

    fun singleChoice(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: String,
        rebootApp: Boolean = false,
        options: List<ChoiceOption>,
        visible: Boolean = true,
    ): SingleChoiceSettingDefinition =
        add(
            SingleChoiceSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
                options,
                visible,
            ),
        )

    fun multiChoice(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: Set<String>,
        rebootApp: Boolean = false,
        options: List<ChoiceOption>,
        visible: Boolean = true,
    ): MultiChoiceSettingDefinition =
        add(
            MultiChoiceSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
                options,
                visible,
            ),
        )

    fun action(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        handlerClassDescriptor: String,
        visible: Boolean = true,
    ): ActionSettingDefinition =
        add(
            ActionSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                handlerClassDescriptor,
                visible,
            ),
        )

    fun customScreen(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        fragmentClassDescriptor: String,
        iconResourceName: String? = null,
    ): CustomScreenSettingDefinition =
        add(
            CustomScreenSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                fragmentClassDescriptor,
                iconResourceName,
            ),
        )

    internal fun build() =
        SettingsGroupDefinition(
            id = id,
            titleResourceName = titleResourceName,
            summaryResourceName = summaryResourceName,
            iconResourceName = iconResourceName,
            order = order,
            children = children.sortedWith(nodeComparator),
        )
}

private val nodeComparator =
    compareBy<SettingsNodeDefinition>(SettingsNodeDefinition::order, SettingsNodeDefinition::id)

// ── Validation ───────────────────────────────────────────────────────────

fun validateSettingsContribution(
    catalog: SettingsContributionCatalog,
    config: SettingsPatchConfig,
) {
    require(catalog.categories.isNotEmpty()) { "A ${config.label} settings contribution cannot be empty" }

    val groupIds = mutableSetOf<String>()
    val settingIds = mutableSetOf<String>()

    fun validateNode(node: SettingsNodeDefinition) {
        validateCommonMetadata(node, config)
        when (node) {
            is SettingsGroupDefinition -> {
                require(groupIds.add(node.id)) { "Duplicate ${config.label} settings group ID: ${node.id}" }
                node.iconResourceName?.let { iconResourceName ->
                    require(DRAWABLE_RESOURCE_NAME_PATTERN.matches(iconResourceName)) {
                        "Invalid ${config.label} group icon resource: $iconResourceName"
                    }
                }
                require(node.children.isNotEmpty()) { "${config.label} settings group is empty: ${node.id}" }
                node.children.forEach(::validateNode)
            }

            is SettingItemDefinition -> {
                require(settingIds.add(node.id)) { "Duplicate ${config.label} setting ID: ${node.id}" }
                validateSetting(node, config)
            }
        }
    }

    catalog.categories.forEach(::validateNode)
}

private fun validateCommonMetadata(
    node: SettingsNodeDefinition,
    config: SettingsPatchConfig,
) {
    require(config.idPattern.matches(node.id)) { "Invalid ${config.label} settings ID: ${node.id}" }
    require(config.resourceNamePattern.matches(node.titleResourceName)) {
        "Invalid ${config.label} title resource: ${node.titleResourceName}"
    }
    node.summaryResourceName?.let { summary ->
        require(config.resourceNamePattern.matches(summary)) {
            "Invalid ${config.label} summary resource: $summary"
        }
    }
    require(node.order >= 0) { "${config.label} settings order cannot be negative: ${node.id}" }
}

private fun validateSetting(
    setting: SettingItemDefinition,
    config: SettingsPatchConfig,
) {
    when (setting) {
        is SingleChoiceSettingDefinition -> {
            require(setting.options.isNotEmpty()) { "Single-choice setting has no options: ${setting.id}" }
            val optionIds = mutableSetOf<String>()
            setting.options.forEach { option ->
                require(OPTION_ID_PATTERN.matches(option.id)) {
                    "Invalid choice option ID for ${setting.id}: ${option.id}"
                }
                require(optionIds.add(option.id)) {
                    "Duplicate choice option ID for ${setting.id}: ${option.id}"
                }
                require(config.resourceNamePattern.matches(option.titleResourceName)) {
                    "Invalid choice title resource for ${setting.id}: ${option.titleResourceName}"
                }
            }
            require(setting.defaultValue in optionIds) {
                "Unknown default choice for ${setting.id}: ${setting.defaultValue}"
            }
        }

        is MultiChoiceSettingDefinition -> {
            require(setting.options.isNotEmpty()) { "Multi-choice setting has no options: ${setting.id}" }
            val optionIds = mutableSetOf<String>()
            setting.options.forEach { option ->
                require(OPTION_ID_PATTERN.matches(option.id)) {
                    "Invalid choice option ID for ${setting.id}: ${option.id}"
                }
                require(optionIds.add(option.id)) {
                    "Duplicate choice option ID for ${setting.id}: ${option.id}"
                }
                require(config.resourceNamePattern.matches(option.titleResourceName)) {
                    "Invalid choice title resource for ${setting.id}: ${option.titleResourceName}"
                }
            }
            require(optionIds.containsAll(setting.defaultValue)) {
                "Unknown default choice for ${setting.id}: ${setting.defaultValue - optionIds}"
            }
        }

        is ActionSettingDefinition ->
            require(HANDLER_DESCRIPTOR_PATTERN.matches(setting.handlerClassDescriptor)) {
                "Invalid action handler descriptor for ${setting.id}: ${setting.handlerClassDescriptor}"
            }

        is CustomScreenSettingDefinition -> {
            setting.iconResourceName?.let { iconResourceName ->
                require(DRAWABLE_RESOURCE_NAME_PATTERN.matches(iconResourceName)) {
                    "Invalid custom screen icon resource for ${setting.id}: $iconResourceName"
                }
            }
            require(HANDLER_DESCRIPTOR_PATTERN.matches(setting.fragmentClassDescriptor)) {
                "Invalid custom screen fragment descriptor for ${setting.id}: ${setting.fragmentClassDescriptor}"
            }
        }

        is TextInputSettingDefinition -> {
            setting.validatorClassDescriptor?.let { descriptor ->
                require(HANDLER_DESCRIPTOR_PATTERN.matches(descriptor)) {
                    "Invalid text input validator descriptor for ${setting.id}: $descriptor"
                }
            }
        }

        is ToggleSettingDefinition -> Unit
    }
}
