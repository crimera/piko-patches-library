/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.downloader

import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloaderManifestPatchTest {

    private fun parseManifest(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.trimIndent().toByteArray()))
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }

    @Test
    fun `three components are added to empty application with exported false`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
            """
        )

        addDownloaderComponents(doc)

        val application = doc.getElementsByTagName("application").item(0) as Element
        val children = application.childElements()
        assertEquals(3, children.size)

        val activity = children.single { it.nodeName == "activity" }
        assertEquals("app.morphe.extension.crimera.downloader.FolderPickerActivity", activity.getAttribute("android:name"))
        assertEquals("false", activity.getAttribute("android:exported"))

        val receivers = children.filter { it.nodeName == "receiver" }
        assertEquals(2, receivers.size)

        val retryReceiver = receivers.single { it.getAttribute("android:name") == "app.morphe.extension.crimera.downloader.RetryReceiver" }
        assertEquals("false", retryReceiver.getAttribute("android:exported"))

        val cancelReceiver = receivers.single { it.getAttribute("android:name") == "app.morphe.extension.crimera.downloader.CancelReceiver" }
        assertEquals("false", cancelReceiver.getAttribute("android:exported"))
    }

    @Test
    fun `second call on the same document is idempotent and adds nothing`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
            """
        )

        addDownloaderComponents(doc)
        val application = doc.getElementsByTagName("application").item(0) as Element
        assertEquals(3, application.childElements().size)

        addDownloaderComponents(doc)
        assertEquals(3, application.childElements().size)
    }

    @Test
    fun `existing components are not duplicated`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name="app.morphe.extension.crimera.downloader.FolderPickerActivity" android:exported="false" />
                </application>
            </manifest>
            """
        )

        addDownloaderComponents(doc)

        val application = doc.getElementsByTagName("application").item(0) as Element
        val children = application.childElements()
        assertEquals(3, children.size)

        val activities = children.filter { it.nodeName == "activity" }
        assertEquals(1, activities.size)
        assertEquals("app.morphe.extension.crimera.downloader.FolderPickerActivity", activities.single().getAttribute("android:name"))

        val receivers = children.filter { it.nodeName == "receiver" }
        assertEquals(2, receivers.size)
    }

    @Test
    fun `existing components are not modified even if they differ`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity
                        android:name="app.morphe.extension.crimera.downloader.FolderPickerActivity"
                        android:exported="true"
                        android:theme="@android:style/Theme.NoTitleBar" />
                    <receiver
                        android:name="app.morphe.extension.crimera.downloader.RetryReceiver"
                        android:exported="true" />
                </application>
            </manifest>
            """
        )

        addDownloaderComponents(doc)

        val application = doc.getElementsByTagName("application").item(0) as Element
        val children = application.childElements()
        assertEquals(3, children.size)

        val activity = children.single { it.nodeName == "activity" }
        assertEquals("app.morphe.extension.crimera.downloader.FolderPickerActivity", activity.getAttribute("android:name"))
        assertEquals("true", activity.getAttribute("android:exported"))
        assertEquals("@android:style/Theme.NoTitleBar", activity.getAttribute("android:theme"))

        val retryReceiver = children.single {
            it.nodeName == "receiver" && it.getAttribute("android:name") == "app.morphe.extension.crimera.downloader.RetryReceiver"
        }
        assertEquals("true", retryReceiver.getAttribute("android:exported"))

        val cancelReceiver = children.single {
            it.nodeName == "receiver" && it.getAttribute("android:name") == "app.morphe.extension.crimera.downloader.CancelReceiver"
        }
        assertEquals("false", cancelReceiver.getAttribute("android:exported"))
    }

    @Test
    fun `pre-existing component with different element name counts and is not duplicated`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <service android:name="app.morphe.extension.crimera.downloader.FolderPickerActivity" />
                </application>
            </manifest>
            """
        )

        addDownloaderComponents(doc)

        val application = doc.getElementsByTagName("application").item(0) as Element
        val children = application.childElements()
        assertEquals(3, children.size)

        val matching = children.filter {
            it.getAttribute("android:name") == "app.morphe.extension.crimera.downloader.FolderPickerActivity"
        }
        assertEquals(1, matching.size)
        assertEquals("service", matching.single().nodeName)

        val receivers = children.filter { it.nodeName == "receiver" }
        assertEquals(2, receivers.size)
    }

    @Test
    fun `fails closed when document has no application element`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
            </manifest>
            """
        )

        val exception = assertFailsWith<PatchException> {
            addDownloaderComponents(doc)
        }
        assertTrue(exception.message!!.contains("<application>"), "Expected message to mention <application>: ${exception.message}")
    }

    @Test
    fun `adds nothing else beyond the three components and their two attributes`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
            """
        )

        addDownloaderComponents(doc)

        // Manifest root only has application element (no added permissions, etc.)
        val manifestChildren = (doc.documentElement as Element).childElements()
        assertEquals(1, manifestChildren.size)
        assertEquals("application", manifestChildren.single().nodeName)

        val application = doc.getElementsByTagName("application").item(0) as Element
        val children = application.childElements()
        assertEquals(3, children.size)

        for (child in children) {
            val attrs = child.attributes
            assertEquals(2, attrs.length, "Component ${child.nodeName} has unexpected attributes")
            assertNotNull(attrs.getNamedItem("android:name"))
            assertNotNull(attrs.getNamedItem("android:exported"))
        }
    }
}
