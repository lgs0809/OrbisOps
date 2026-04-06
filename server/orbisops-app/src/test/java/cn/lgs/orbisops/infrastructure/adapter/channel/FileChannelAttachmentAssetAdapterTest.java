package cn.lgs.orbisops.infrastructure.adapter.channel;

import cn.lgs.orbisops.application.channel.ChannelAttachmentAssetPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileChannelAttachmentAssetAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void storesOpaqueObjectRefHashAndCanReadContentWithoutLeakingFilesystemPath() {
        FileChannelAttachmentAssetAdapter adapter = new FileChannelAttachmentAssetAdapter(tempDir.toString());
        byte[] content = "incident-log".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        var stored = adapter.store(new ChannelAttachmentAssetPort.StoreAttachment(
                "project-1", "channel-1", "file-1", "incident.log", "text/plain", content));

        assertTrue(stored.contentRef().startsWith("object://channel/"));
        assertFalse(stored.contentRef().contains(tempDir.toString()));
        assertEquals(64, stored.contentHash().length());
        assertEquals(content.length, stored.sizeBytes());
        assertArrayEquals(content, adapter.read(stored.contentRef()));
    }

    @Test
    void rejectsForeignOrMalformedObjectReferences() {
        FileChannelAttachmentAssetAdapter adapter = new FileChannelAttachmentAssetAdapter(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> adapter.read("https://files.example.test/private"));
        assertThrows(IllegalArgumentException.class, () -> adapter.read("object://channel/../../etc/passwd"));
    }
}
