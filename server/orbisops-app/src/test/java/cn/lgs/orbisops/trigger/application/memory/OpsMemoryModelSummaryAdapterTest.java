package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsMemoryModelSummaryAdapterTest {

    private final OpsMemoryModelSummaryAdapter adapter = new OpsMemoryModelSummaryAdapter();

    @Test
    void transcriptPreservesHistoricalTimeRoleAndContentFormat() {
        String transcript = adapter.transcript(List.of(
                message("user", "问题", "2026-07-21 10:00:00"),
                message("assistant", "结论", "2026-07-21 10:01:00")));

        assertEquals("\n2026-07-21 10:00:00 user: 问题"
                + "\n2026-07-21 10:01:00 assistant: 结论", transcript);
    }

    @Test
    void nullMessagesAreIgnoredAndEmptyInputReturnsEmptyTranscript() {
        assertEquals("", adapter.transcript(null));
        assertEquals("", adapter.transcript(List.of()));
    }

    @Test
    void unavailableModelReturnsEmptySummary() {
        assertEquals("", adapter.summarize(
                List.of(message("user", "问题", "time")),
                6000));
    }

    private ColdMemoryMessageSnapshot message(String role, String content, String createdAt) {
        return new ColdMemoryMessageSnapshot(
                "session-1",
                "user-1",
                role,
                content,
                createdAt,
                Map.of());
    }
}
