package app.morphe.extension.crimera.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class LogBufferTest {
    @Test
    public void snapshotPreservesInsertionOrder() {
        LogBuffer buffer = new LogBuffer();

        buffer.add("first");
        buffer.add("second");

        assertEquals(List.of("first", "second"), buffer.snapshot());
    }

    @Test
    public void oldestEntriesAreEvictedAtCapacity() {
        LogBuffer buffer = new LogBuffer();
        for (int index = 0; index < LogBuffer.MAX_ENTRIES + 1; index++) {
            buffer.add("entry-" + index);
        }

        List<String> snapshot = buffer.snapshot();
        assertEquals(LogBuffer.MAX_ENTRIES, snapshot.size());
        assertFalse(snapshot.contains("entry-0"));
        assertTrue(snapshot.contains("entry-" + LogBuffer.MAX_ENTRIES));
    }

    @Test
    public void totalCharacterLimitEvictsOldEntries() {
        LogBuffer buffer = new LogBuffer();
        String entry = "x".repeat(LogBuffer.MAX_ENTRY_CHARS);
        int entryCount = LogBuffer.MAX_TOTAL_CHARS / LogBuffer.MAX_ENTRY_CHARS + 1;

        for (int index = 0; index < entryCount; index++) {
            buffer.add(entry);
        }

        List<String> snapshot = buffer.snapshot();
        int totalChars = snapshot.stream().mapToInt(String::length).sum();
        assertTrue(totalChars <= LogBuffer.MAX_TOTAL_CHARS);
        assertEquals(LogBuffer.MAX_TOTAL_CHARS / LogBuffer.MAX_ENTRY_CHARS, snapshot.size());
    }

    @Test
    public void oversizedEntriesAreTruncated() {
        LogBuffer buffer = new LogBuffer();
        String oversized = "x".repeat(LogBuffer.MAX_ENTRY_CHARS + 100);

        buffer.add(oversized);

        String stored = buffer.snapshot().get(0);
        assertEquals(LogBuffer.MAX_ENTRY_CHARS, stored.length());
        assertTrue(stored.endsWith("[entry truncated]"));
    }
}
