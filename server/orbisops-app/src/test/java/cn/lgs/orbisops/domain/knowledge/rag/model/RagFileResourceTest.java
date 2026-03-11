package cn.lgs.orbisops.domain.knowledge.rag.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagFileResourceTest {

    @Test
    void exposesFrameworkNeutralAliasesAndRepeatableReads() throws Exception {
        byte[] bytes = "knowledge".getBytes(StandardCharsets.UTF_8);
        RagFileResource resource = resource("files", "runbook.md", "text/markdown", bytes);

        assertEquals("files", resource.getName());
        assertEquals("runbook.md", resource.fileName());
        assertEquals("text/markdown", resource.getContentType());
        assertEquals(bytes.length, resource.getSize());
        assertFalse(resource.isEmpty());
        assertArrayEquals(bytes, resource.getBytes());
        assertArrayEquals(bytes, resource.getInputStream().readAllBytes());
        assertArrayEquals(bytes, resource.getInputStream().readAllBytes());
    }

    @Test
    void fallsBackToLogicalNameAndReportsEmptyResource() {
        RagFileResource resource = resource("files", "", null, new byte[0]);

        assertEquals("files", resource.fileName());
        assertEquals(null, resource.contentType());
        assertTrue(resource.empty());
    }

    private RagFileResource resource(String name, String originalFilename, String contentType, byte[] bytes) {
        byte[] snapshot = bytes == null ? new byte[0] : bytes.clone();
        return new RagFileResource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String originalFilename() {
                return originalFilename;
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public long size() {
                return snapshot.length;
            }

            @Override
            public ByteArrayInputStream openStream() {
                return new ByteArrayInputStream(snapshot);
            }
        };
    }
}
