package cn.lgs.orbisops.trigger.application.rag;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MultipartRagFileResourceTest {

    @Test
    void adaptsMultipartFileWithoutChangingWebBoundarySemantics() throws Exception {
        byte[] bytes = "# Runbook".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile multipart = new MockMultipartFile(
                "files", "runbook.md", "text/markdown", bytes);
        MultipartRagFileResource resource = new MultipartRagFileResource(multipart);

        assertEquals("files", resource.name());
        assertEquals("runbook.md", resource.originalFilename());
        assertEquals("text/markdown", resource.contentType());
        assertEquals(bytes.length, resource.size());
        assertFalse(resource.empty());
        assertArrayEquals(bytes, resource.readAllBytes());
        assertArrayEquals(bytes, resource.openStream().readAllBytes());
    }
}
