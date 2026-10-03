package app.morphe.extension.crimera.downloader.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SafDestinationTest {
    @Test
    public void appendSuffixWithExtension() {
        assertEquals("video_1.mp4", SafDestination.appendSuffix("video.mp4", 1));
        assertEquals("image_2.jpg", SafDestination.appendSuffix("image.jpg", 2));
    }

    @Test
    public void appendSuffixWithoutExtension() {
        assertEquals("README_1", SafDestination.appendSuffix("README", 1));
        assertEquals("LICENSE_3", SafDestination.appendSuffix("LICENSE", 3));
    }

    @Test
    public void appendSuffixWithSeveralDots() {
        assertEquals("archive.tar_1.gz", SafDestination.appendSuffix("archive.tar.gz", 1));
        assertEquals("my.backup.file_2.zip", SafDestination.appendSuffix("my.backup.file.zip", 2));
    }

    @Test
    public void appendSuffixWithLeadingDot() {
        assertEquals(".nomedia_1", SafDestination.appendSuffix(".nomedia", 1));
        assertEquals(".hidden_2", SafDestination.appendSuffix(".hidden", 2));
    }

    @Test
    public void appendSuffixWithLargeSuffixNumbers() {
        assertEquals("photo_999999.png", SafDestination.appendSuffix("photo.png", 999999));
        assertEquals("data_1000000", SafDestination.appendSuffix("data", 1000000));
    }
}
