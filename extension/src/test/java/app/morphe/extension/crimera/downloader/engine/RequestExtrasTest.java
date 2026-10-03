/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;

public final class RequestExtrasTest {
    private static final String VALID_URL = "https://example.com/media/file.mp4";
    private static final String VALID_FILE_NAME = "sentinel_file.mp4";
    private static final String VALID_MIME_TYPE = "video/mp4";
    private static final ConflictPolicy VALID_CONFLICT = ConflictPolicy.RENAME;
    private static final String VALID_LABEL = "creator_sentinel";

    @Test
    public void encodeDecodeEmptyList() {
        assertEquals("", RequestExtras.encodeList(Collections.emptyList()));
        assertEquals("", RequestExtras.encodeList(null));
        assertTrue(RequestExtras.decodeList("").isEmpty());
        assertTrue(RequestExtras.decodeList(null).isEmpty());
    }

    @Test
    public void encodeDecodeOneItem() {
        String encoded = RequestExtras.encodeList(List.of("alpha"));
        assertEquals("alpha", encoded);
        assertEquals(List.of("alpha"), RequestExtras.decodeList(encoded));
    }

    @Test
    public void encodeDecodeItemsContainingSeparatorCharacter() {
        List<String> items = List.of("part1|part2", "item3", "pipe|at|end|", "|start|pipe");
        String encoded = RequestExtras.encodeList(items);
        assertTrue(encoded.contains("\\|"));
        assertEquals(items, RequestExtras.decodeList(encoded));
    }

    @Test
    public void encodeDecodeItemsContainingEscapeCharacter() {
        List<String> items = List.of("back\\slash", "slash\\and\\pipe|together", "\\leading");
        String encoded = RequestExtras.encodeList(items);
        assertEquals(items, RequestExtras.decodeList(encoded));
    }

    @Test
    public void listRoundTrip() {
        List<String> original = List.of("first", "second segment", "third-segment", "a=b&c=d", "emoji_🚀");
        String encoded = RequestExtras.encodeList(original);
        assertEquals(original, RequestExtras.decodeList(encoded));
    }

    @Test
    public void conflictPolicyEncodeDecode() {
        assertEquals("OVERWRITE", RequestExtras.encodeConflict(ConflictPolicy.OVERWRITE));
        assertEquals("RENAME", RequestExtras.encodeConflict(ConflictPolicy.RENAME));
        assertEquals("SKIP", RequestExtras.encodeConflict(ConflictPolicy.SKIP));
        assertNull(RequestExtras.encodeConflict(null));

        assertEquals(ConflictPolicy.OVERWRITE, RequestExtras.decodeConflict("OVERWRITE"));
        assertEquals(ConflictPolicy.RENAME, RequestExtras.decodeConflict("RENAME"));
        assertEquals(ConflictPolicy.SKIP, RequestExtras.decodeConflict("SKIP"));
        assertNull(RequestExtras.decodeConflict("UNKNOWN_RULE"));
        assertNull(RequestExtras.decodeConflict(""));
        assertNull(RequestExtras.decodeConflict(null));
    }

    @Test
    public void isValidUrlRequiresHttpOrHttps() {
        assertTrue(RequestExtras.isValidUrl("http://example.com/item"));
        assertTrue(RequestExtras.isValidUrl("https://example.com/item.mp4"));
        assertFalse(RequestExtras.isValidUrl("ftp://example.com/item"));
        assertFalse(RequestExtras.isValidUrl("file:///local/path"));
        assertFalse(RequestExtras.isValidUrl(""));
        assertFalse(RequestExtras.isValidUrl(null));
        assertFalse(RequestExtras.isValidUrl("httpx://invalid"));
    }

    @Test
    public void rejectedNonHttpUrl() {
        assertNull(RequestExtras.decodeRequest(
                "ftp://example.com/media.mp4",
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
        assertNull(RequestExtras.decodeRequest(
                "",
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
        assertNull(RequestExtras.decodeRequest(
                null,
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
    }

    @Test
    public void rejectedEmptyOrNullFileName() {
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                "",
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                null,
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
    }

    @Test
    public void rejectedUnknownConflictRule() {
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                "NOT_A_POLICY",
                VALID_LABEL
        ));
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                "",
                VALID_LABEL
        ));
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                null,
                VALID_LABEL
        ));
    }

    @Test
    public void rejectedMissingFields() {
        // Missing MIME type
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                null,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                VALID_FILE_NAME,
                "",
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
    }

    @Test
    public void rejectedInvalidPathSegments() {
        // Path traverse or slash in file name
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                "dir/file.mp4",
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                "",
                "..",
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
        // Invalid subpath segment
        assertNull(RequestExtras.decodeRequest(
                VALID_URL,
                "",
                (String) null,
                RequestExtras.encodeList(List.of("sub/dir")),
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                VALID_CONFLICT.name(),
                VALID_LABEL
        ));
    }

    @Test
    public void requestDecodeRoundTrip() {
        List<String> fallbacks = List.of("https://fallback1.example.com", "https://fallback2.example.com/pipe|in|url");
        List<String> subpath = List.of("folder_one", "sub_folder|two");

        DownloadRequest original = new DownloadRequest(
                VALID_URL,
                fallbacks,
                null,
                subpath,
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                VALID_CONFLICT,
                VALID_LABEL
        );

        String encodedFallbacks = RequestExtras.encodeList(original.fallbackUrls());
        String encodedSubpath = RequestExtras.encodeList(original.subpath());
        String encodedConflict = RequestExtras.encodeConflict(original.conflict());

        DownloadRequest decoded = RequestExtras.decodeRequest(
                original.url(),
                encodedFallbacks,
                (String) null,
                encodedSubpath,
                original.fileName(),
                original.mimeType(),
                encodedConflict,
                original.label()
        );

        assertNotNull(decoded);
        assertEquals(original.url(), decoded.url());
        assertEquals(original.fallbackUrls(), decoded.fallbackUrls());
        assertNull(decoded.destinationTree());
        assertEquals(original.subpath(), decoded.subpath());
        assertEquals(original.fileName(), decoded.fileName());
        assertEquals(original.mimeType(), decoded.mimeType());
        assertEquals(original.conflict(), decoded.conflict());
        assertEquals(original.label(), decoded.label());
    }

    @Test
    public void canRetryValidation() {
        DownloadRequest valid = new DownloadRequest(
                VALID_URL,
                null,
                null,
                Collections.emptyList(),
                VALID_FILE_NAME,
                VALID_MIME_TYPE,
                VALID_CONFLICT,
                null
        );
        assertTrue(RequestExtras.canRetry(valid));
        assertFalse(RequestExtras.canRetry(null));
    }
}
