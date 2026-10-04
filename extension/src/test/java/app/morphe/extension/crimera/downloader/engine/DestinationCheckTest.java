/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link DestinationCheck#displayPathFor(String)}.
 *
 * Note: the permission probes (hasPersistedWritePermission, hasLiveTreeAccess, sameAuthority)
 * and FolderPickerActivity / FolderPicker have no host unit tests because they require Android
 * platform classes (Context, Uri, ContentResolver, DocumentsContract) that cannot run in
 * unmocked JVM unit tests.
 */
public final class DestinationCheckTest {

    @Test
    public void primaryStorageWithSubpath() {
        assertEquals("/Download/Piko", DestinationCheck.displayPathFor("primary:Download/Piko"));
        assertEquals("/DCIM/Camera", DestinationCheck.displayPathFor("primary:DCIM/Camera"));
    }

    @Test
    public void primaryStorageWithoutSubpath() {
        assertEquals("/", DestinationCheck.displayPathFor("primary:"));
    }

    @Test
    public void secondaryStorageWithAndWithoutSubpath() {
        assertEquals("1234-5678/Media", DestinationCheck.displayPathFor("1234-5678:Media"));
        assertEquals("0000-0000/Download/Piko", DestinationCheck.displayPathFor("0000-0000:Download/Piko"));
        assertEquals("1234-5678/", DestinationCheck.displayPathFor("1234-5678:"));
    }

    @Test
    public void idWithoutColon() {
        assertEquals("raw_document_id", DestinationCheck.displayPathFor("raw_document_id"));
        assertEquals("singleidentifier", DestinationCheck.displayPathFor("singleidentifier"));
    }

    @Test
    public void emptyId() {
        assertEquals("", DestinationCheck.displayPathFor(""));
    }

    @Test
    public void trailingAndLeadingSeparators() {
        // Leading colon in id
        assertEquals(":folder", DestinationCheck.displayPathFor(":folder"));
        // Primary with leading slash after prefix
        assertEquals("//Download", DestinationCheck.displayPathFor("primary:/Download"));
        // Primary with trailing slash
        assertEquals("/Download/", DestinationCheck.displayPathFor("primary:Download/"));
        // Secondary with leading slash after colon
        assertEquals("1234-5678//Media", DestinationCheck.displayPathFor("1234-5678:/Media"));
        // Secondary with trailing slash
        assertEquals("1234-5678/Media/", DestinationCheck.displayPathFor("1234-5678:Media/"));
        // Document id starting with slash before colon
        assertEquals("/primary/Download", DestinationCheck.displayPathFor("/primary:Download"));
    }
}
