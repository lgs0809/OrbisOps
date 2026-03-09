package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryExtractionDraft;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMemoryModelExtractionAdapterTest {

    private final OpsMemoryModelExtractionAdapter adapter = new OpsMemoryModelExtractionAdapter();

    @Test
    void decodesFencedModelJsonIntoRawDrafts() {
        String longContent = "偏好".repeat(170);
        String response = "```json\n{\"memories\":["
                + "{\"memoryType\":\"USER_PREFERENCE\",\"content\":\"" + longContent + "\","
                + "\"confidence\":0.9,\"tags\":[\"user\",\"user\",\"style\"],\"scopeType\":\"USER\"},"
                + "{\"type\":\"PROJECT_CONTEXT\",\"content\":\"项目背景\",\"importance\":0.6},"
                + "{\"memoryType\":\"USER_WORKFLOW\",\"content\":\"\"}]}\n```";

        List<MemoryExtractionDraft> drafts = adapter.decode(response);

        assertEquals(2, drafts.size());
        assertEquals("USER_PREFERENCE", drafts.get(0).memoryType());
        assertEquals(BigDecimal.valueOf(0.9D), drafts.get(0).importance());
        assertEquals("[\"user\",\"style\"]", drafts.get(0).tagsJson());
        assertTrue(drafts.get(0).content().endsWith("..."));
        assertEquals(323, drafts.get(0).content().length());
        assertEquals("PROJECT_CONTEXT", drafts.get(1).memoryType());
        assertEquals(BigDecimal.valueOf(0.6D), drafts.get(1).importance());
        assertEquals("[\"llm\"]", drafts.get(1).tagsJson());
    }

    @Test
    void malformedOrEmptyModelContentProducesNoDrafts() {
        assertEquals(List.of(), adapter.decode(null));
        assertEquals(List.of(), adapter.decode("plain text"));
        assertEquals(List.of(), adapter.decode("{\"memories\":[]}"));
    }

    @Test
    void unavailableModelReturnsEmptyWithoutInvocation() {
        List<MemoryExtractionDraft> drafts = adapter.extract(new ColdMemoryMessageSnapshot(
                "session-1",
                "user-1",
                "user",
                "以后优先给结论",
                "",
                Map.of()), 4000);

        assertTrue(drafts.isEmpty());
    }
}
