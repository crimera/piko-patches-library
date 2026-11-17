/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.downloader

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.xml.ResourceXml
import app.morphe.patcher.xml.XmlElement

private data class ManifestComponent(
    val tagName: String,
    val className: String,
)

private val DOWNLOADER_COMPONENTS = listOf(
    ManifestComponent("activity", "app.morphe.extension.crimera.downloader.FolderPickerActivity"),
    ManifestComponent("receiver", "app.morphe.extension.crimera.downloader.RetryReceiver"),
    ManifestComponent("receiver", "app.morphe.extension.crimera.downloader.CancelReceiver"),
    ManifestComponent("receiver", "app.morphe.extension.crimera.downloader.DeleteReceiver"),
)

/**
 * Adds the downloader activity and broadcast receivers to the manifest under `<application>`.
 *
 * Safe to run multiple times: existing components with matching name are preserved as-is.
 */
internal fun addDownloaderComponents(manifest: ResourceXml) {
    val application = manifest.elements("application").firstOrNull()
        ?: throw PatchException("AndroidManifest.xml does not contain an <application> element")

    for (component in DOWNLOADER_COMPONENTS) {
        if (!application.hasComponent(component.className)) {
            application.appendChild(component.tagName) {
                setAttribute("android:name", component.className)
                setAttribute("android:exported", "false")
            }
        }
    }
}

private fun XmlElement.hasComponent(name: String): Boolean =
    children.any { child -> child.attribute("android:name") == name }

val downloaderManifestPatch =
    patch(
        description = "Adds the shared downloader components to the Android manifest.",
    ) {
        finalize {
            resourceXml("AndroidManifest.xml").use { manifest ->
                addDownloaderComponents(manifest)
            }
        }
    }
