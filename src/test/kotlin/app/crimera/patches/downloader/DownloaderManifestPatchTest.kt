/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.downloader

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.readXml
import app.morphe.patcher.xml.ResourceXml
import app.morphe.patcher.xml.XmlElement
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloaderManifestPatchTest {

    private fun parseManifest(xml: String): ResourceXml =
        readXml(ByteArrayInputStream(xml.trimIndent().toByteArray()))

    private fun XmlElement.childElements(): List<XmlElement> = children

    private fun ResourceXml.application(): XmlElement = elements("application").first()

    @Test
    fun `four components are added to empty application with exported false`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
            """
        )

        addDownloaderComponents(doc)

        val application = doc.application()
        val children = application.childElements()
        assertEquals(4, children.size)

        val activity = children.single { it.tag == "activity" }
        assertEquals("app.morphe.extension.crimera.downloader.FolderPickerActivity", activity.attribute("android:name"))
        assertEquals("false", activity.attribute("android:exported"))

        val receivers = children.filter { it.tag == "receiver" }
        assertEquals(3, receivers.size)

        val retryReceiver = receivers.single { it.attribute("android:name") == "app.morphe.extension.crimera.downloader.RetryReceiver" }
        assertEquals("false", retryReceiver.attribute("android:exported"))

        val cancelReceiver = receivers.single { it.attribute("android:name") == "app.morphe.extension.crimera.downloader.CancelReceiver" }
        assertEquals("false", cancelReceiver.attribute("android:exported"))

        val deleteReceiver = receivers.single { it.attribute("android:name") == "app.morphe.extension.crimera.downloader.DeleteReceiver" }
        assertEquals("false", deleteReceiver.attribute("android:exported"))
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
        val application = doc.application()
        assertEquals(4, application.childElements().size)

        addDownloaderComponents(doc)
        assertEquals(4, application.childElements().size)
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

        val application = doc.application()
        val children = application.childElements()
        assertEquals(4, children.size)

        val activities = children.filter { it.tag == "activity" }
        assertEquals(1, activities.size)
        assertEquals("app.morphe.extension.crimera.downloader.FolderPickerActivity", activities.single().attribute("android:name"))

        val receivers = children.filter { it.tag == "receiver" }
        assertEquals(3, receivers.size)
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

        val application = doc.application()
        val children = application.childElements()
        assertEquals(4, children.size)

        val activity = children.single { it.tag == "activity" }
        assertEquals("app.morphe.extension.crimera.downloader.FolderPickerActivity", activity.attribute("android:name"))
        assertEquals("true", activity.attribute("android:exported"))
        assertEquals("@android:style/Theme.NoTitleBar", activity.attribute("android:theme"))

        val retryReceiver = children.single {
            it.tag == "receiver" && it.attribute("android:name") == "app.morphe.extension.crimera.downloader.RetryReceiver"
        }
        assertEquals("true", retryReceiver.attribute("android:exported"))

        val cancelReceiver = children.single {
            it.tag == "receiver" && it.attribute("android:name") == "app.morphe.extension.crimera.downloader.CancelReceiver"
        }
        assertEquals("false", cancelReceiver.attribute("android:exported"))
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

        val application = doc.application()
        val children = application.childElements()
        assertEquals(4, children.size)

        val matching = children.filter {
            it.attribute("android:name") == "app.morphe.extension.crimera.downloader.FolderPickerActivity"
        }
        assertEquals(1, matching.size)
        assertEquals("service", matching.single().tag)

        val receivers = children.filter { it.tag == "receiver" }
        assertEquals(3, receivers.size)
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
    fun `adds nothing else beyond the four components and their two attributes`() {
        val doc = parseManifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
            """
        )

        addDownloaderComponents(doc)

        // Manifest root only has application element (no added permissions, etc.)
        val manifestChildren = doc.root.childElements()
        assertEquals(1, manifestChildren.size)
        assertEquals("application", manifestChildren.single().tag)

        val application = doc.application()
        val children = application.childElements()
        assertEquals(4, children.size)

        for (child in children) {
            assertEquals(2, child.attributeNames.size, "Component ${child.tag} has unexpected attributes")
            assertNotNull(child.attribute("android:name"))
            assertNotNull(child.attribute("android:exported"))
        }
    }
}
