/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.downloader

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private data class ManifestComponent(
    val tagName: String,
    val className: String,
)

private val DOWNLOADER_COMPONENTS = listOf(
    ManifestComponent("activity", "app.morphe.extension.crimera.downloader.FolderPickerActivity"),
    ManifestComponent("receiver", "app.morphe.extension.crimera.downloader.RetryReceiver"),
    ManifestComponent("receiver", "app.morphe.extension.crimera.downloader.CancelReceiver"),
)

/**
 * Adds the downloader activity and broadcast receivers to the manifest under `<application>`.
 *
 * Safe to run multiple times: existing components with matching name are preserved as-is.
 */
internal fun addDownloaderComponents(document: Document) {
    val applications = document.getElementsByTagName("application")
    if (applications.length == 0) {
        throw PatchException("AndroidManifest.xml does not contain an <application> element")
    }
    val application = applications.item(0) as Element

    for (component in DOWNLOADER_COMPONENTS) {
        if (!application.hasComponent(component.className)) {
            val element = document.createElement(component.tagName)
            element.setAttribute("android:name", component.className)
            element.setAttribute("android:exported", "false")
            application.appendChild(element)
        }
    }
}

private fun Element.hasComponent(name: String): Boolean {
    val children = childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val componentName = element.getAttribute("android:name").ifEmpty {
                element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            }
            if (componentName == name) {
                return true
            }
        }
    }
    return false
}

val downloaderManifestPatch =
    resourcePatch(
        description = "Adds the shared downloader components to the Android manifest.",
    ) {
        finalize {
            document("AndroidManifest.xml").use { document ->
                addDownloaderComponents(document)
            }
        }
    }
