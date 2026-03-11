package cn.lgs.orbisops.domain.knowledge.rag.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagIngestionJobTest {

    @Test
    void enforcesPendingRunningTerminalLifecycle() {
        RagIngestionJob pending = RagIngestionJob.pending(
                "rag_job_1", "Ops", "sop", List.of("runbook.md"), 128L,
                "2026-07-19 08:00:00");
        RagIngestionJob running = pending.start("2026-07-19 08:00:01");
        RagIngestionJob succeeded = running.succeed("2026-07-19 08:00:02");

        assertEquals("PENDING", pending.status().name());
        assertEquals("RUNNING", running.status().name());
        assertEquals("SUCCEEDED", succeeded.status().name());
        assertThrows(IllegalStateException.class, () -> pending.succeed("2026-07-19 08:00:02"));
        assertThrows(IllegalStateException.class, () -> succeeded.fail("late failure", "2026-07-19 08:00:03"));
    }

    @Test
    void allowsSchedulingFailureFromPending() {
        RagIngestionJob pending = RagIngestionJob.pending(
                "rag_job_2", "Ops", "sop", List.of("runbook.md"), 128L,
                "2026-07-19 08:00:00");

        RagIngestionJob failed = pending.fail("executor rejected", "2026-07-19 08:00:01");

        assertEquals("FAILED", failed.status().name());
        assertEquals("executor rejected", failed.errorMessage());
    }
}
