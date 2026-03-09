package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryMutationCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsContextMemoryCommandMapperTest {

    private final OpsContextMemoryCommandMapper mapper = new OpsContextMemoryCommandMapper();

    @Test
    void mapsPresentFieldsAndStructuredKeywordsToTypedCommand() {
        ContextMemoryMutationCommand command = mapper.command(Map.ofEntries(
                Map.entry("memoryId", " ctx-1 "),
                Map.entry("scopeType", " project "),
                Map.entry("scopeId", " demo-project "),
                Map.entry("memoryType", " project_context "),
                Map.entry("title", " DDD migration "),
                Map.entry("summary", " summary "),
                Map.entry("content", " content "),
                Map.entry("keywords", List.of("ddd", "memory")),
                Map.entry("status", " active "),
                Map.entry("confidence", "0.75"),
                Map.entry("sourceType", " manual "),
                Map.entry("sourceId", " session-1 "),
                Map.entry("sourceMessageHash", " hash "),
                Map.entry("createdBy", " user-1 ")));

        assertEquals("ctx-1", command.memoryId());
        assertEquals("project", command.scopeType());
        assertEquals("demo-project", command.scopeId());
        assertEquals("project_context", command.memoryType());
        assertEquals("DDD migration", command.title());
        assertTrue(command.keywords().contains("ddd"));
        assertTrue(command.confidencePresent());
        assertEquals(BigDecimal.valueOf(0.75D), command.confidence());
        assertEquals("manual", command.sourceType());
    }

    @Test
    void preservesAbsentVersusExplicitBlankFields() {
        ContextMemoryMutationCommand absent = mapper.command(Map.of("summary", "updated"));
        ContextMemoryMutationCommand blank = mapper.command(Map.of(
                "title", " ",
                "keywords", "",
                "confidence", " "));

        assertNull(absent.title());
        assertNull(absent.keywords());
        assertFalse(absent.confidencePresent());
        assertNull(absent.confidence());
        assertEquals("", blank.title());
        assertEquals("", blank.keywords());
        assertTrue(blank.confidencePresent());
        assertNull(blank.confidence());
    }

    @Test
    void invalidConfidenceRemainsPresentAndMapsToNullValue() {
        ContextMemoryMutationCommand command = mapper.command(Map.of("confidence", "not-a-number"));

        assertTrue(command.confidencePresent());
        assertNull(command.confidence());
    }

    @Test
    void nullRequestProducesFullyAbsentPatch() {
        ContextMemoryMutationCommand command = mapper.command(null);

        assertNull(command.memoryId());
        assertNull(command.scopeType());
        assertNull(command.content());
        assertFalse(command.confidencePresent());
    }
}
