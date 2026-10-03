/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class DownloadRequestTest {
    private static final String URL = "https://example.com/media/test.mp4";
    private static final String FALLBACK_URL = "https://cdn.example.com/fallback.mp4";
    private static final String DIR_1 = "user_folder";
    private static final String DIR_2 = "video_folder";
    private static final String FILE_NAME = "sentinel_video.mp4";
    private static final String MIME_TYPE = "video/mp4";
    private static final ConflictPolicy CONFLICT = ConflictPolicy.RENAME;
    private static final String LABEL = "creator_sentinel";

    @Test
    public void validRequestRetainsDistinctFields() {
        DownloadRequest request = new DownloadRequest(
                URL,
                List.of(FALLBACK_URL),
                null,
                List.of(DIR_1, DIR_2),
                FILE_NAME,
                MIME_TYPE,
                CONFLICT,
                LABEL
        );

        assertEquals(URL, request.url());
        assertEquals(List.of(FALLBACK_URL), request.fallbackUrls());
        assertNull(request.destinationTree());
        assertEquals(List.of(DIR_1, DIR_2), request.subpath());
        assertEquals(FILE_NAME, request.fileName());
        assertEquals(MIME_TYPE, request.mimeType());
        assertEquals(CONFLICT, request.conflict());
        assertEquals(LABEL, request.label());
    }

    @Test
    public void nullFallbackUrlsDefaultsToEmptyList() {
        DownloadRequest request = new DownloadRequest(
                URL,
                null,
                null,
                List.of(DIR_1),
                FILE_NAME,
                MIME_TYPE,
                CONFLICT,
                null
        );

        assertTrue(request.fallbackUrls().isEmpty());
        assertNull(request.label());
    }

    @Test
    public void emptySubpathIsValid() {
        DownloadRequest request = new DownloadRequest(
                URL,
                List.of(FALLBACK_URL),
                null,
                Collections.emptyList(),
                FILE_NAME,
                MIME_TYPE,
                CONFLICT,
                LABEL
        );

        assertTrue(request.subpath().isEmpty());
    }

    @Test
    public void fallbackUrlsIsDefensivelyCopiedAndUnmodifiable() {
        List<String> callerList = new ArrayList<>(List.of(FALLBACK_URL));
        DownloadRequest request = new DownloadRequest(
                URL,
                callerList,
                null,
                List.of(DIR_1),
                FILE_NAME,
                MIME_TYPE,
                CONFLICT,
                LABEL
        );

        callerList.add("https://malicious.example.com/other.mp4");
        assertEquals(List.of(FALLBACK_URL), request.fallbackUrls());

        try {
            request.fallbackUrls().add("https://extra.example.com/other.mp4");
            org.junit.Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Success
        }
    }

    @Test
    public void nullFallbackUrlsReturnsUnmodifiableEmptyList() {
        DownloadRequest request = new DownloadRequest(
                URL,
                null,
                null,
                List.of(DIR_1),
                FILE_NAME,
                MIME_TYPE,
                CONFLICT,
                LABEL
        );

        try {
            request.fallbackUrls().add("https://extra.example.com");
            org.junit.Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Success
        }
    }

    @Test
    public void subpathIsDefensivelyCopiedAndUnmodifiable() {
        List<String> callerList = new ArrayList<>(List.of(DIR_1));
        DownloadRequest request = new DownloadRequest(
                URL,
                List.of(FALLBACK_URL),
                null,
                callerList,
                FILE_NAME,
                MIME_TYPE,
                CONFLICT,
                LABEL
        );

        callerList.add(DIR_2);
        assertEquals(List.of(DIR_1), request.subpath());

        try {
            request.subpath().add("extra_dir");
            org.junit.Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Success
        }
    }

    @Test(expected = NullPointerException.class)
    public void nullUrlThrowsNullPointerException() {
        new DownloadRequest(null, null, null, Collections.emptyList(), FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullFileNameThrowsNullPointerException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), null, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullMimeTypeThrowsNullPointerException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), FILE_NAME, null, CONFLICT, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullConflictThrowsNullPointerException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), FILE_NAME, MIME_TYPE, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullSubpathThrowsNullPointerException() {
        new DownloadRequest(URL, null, null, null, FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyUrlThrowsIllegalArgumentException() {
        new DownloadRequest("", null, null, Collections.emptyList(), FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyFileNameThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), "", MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyMimeTypeThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), FILE_NAME, "", CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fileNameWithSlashThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), "folder/file.mp4", MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fileNameWithBackslashThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), "folder\\file.mp4", MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fileNameDotThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), ".", MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fileNameDotDotThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, Collections.emptyList(), "..", MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void subpathWithNullSegmentThrowsIllegalArgumentException() {
        List<String> segments = new ArrayList<>();
        segments.add(null);
        new DownloadRequest(URL, null, null, segments, FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void subpathWithEmptySegmentThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, List.of(""), FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void subpathWithSlashThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, List.of("sub/dir"), FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void subpathWithBackslashThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, List.of("sub\\dir"), FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void subpathDotThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, List.of("."), FILE_NAME, MIME_TYPE, CONFLICT, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void subpathDotDotThrowsIllegalArgumentException() {
        new DownloadRequest(URL, null, null, List.of(".."), FILE_NAME, MIME_TYPE, CONFLICT, null);
    }
}
