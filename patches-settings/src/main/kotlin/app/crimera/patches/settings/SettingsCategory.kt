package app.crimera.patches.settings

/** A top-level settings category, declared once by the app and shared by its contributions. */
data class SettingsCategory(
    val id: String,
    val titleResourceName: String,
    val summaryResourceName: String?,
    val iconResourceName: String?,
    val order: Int,
)

/** Metadata of a group that several patches contribute items to. */
data class SettingsGroupMetadata(
    val id: String,
    val titleResourceName: String,
    val summaryResourceName: String?,
    val iconResourceName: String?,
    val order: Int,
)
