package cn.lgs.orbisops.domain.knowledge.rag.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagDocumentTest {

    @Test
    void preservesRawTextAndCopiesMetadataWithoutFrameworkTypes() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledge", "demo-ops");
        metadata.put("nullable", null);

        RagDocument document = new RagDocument(" chunk-1 ", "  raw text\n", metadata);
        metadata.put("knowledge", "changed");

        assertEquals("chunk-1", document.id());
        assertEquals("  raw text\n", document.text());
        assertEquals("demo-ops", document.metadata().get("knowledge"));
        assertEquals(null, document.metadata().get("nullable"));
        assertThrows(UnsupportedOperationException.class,
                () -> document.metadata().put("new", "value"));
    }

    @Test
    void rejectsBlankIdentityAndNormalizesNullValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new RagDocument(" ", "text", Map.of()));
        RagDocument document = new RagDocument("chunk-1", null, null);
        assertEquals("", document.text());
        assertEquals(Map.of(), document.metadata());
    }
}
