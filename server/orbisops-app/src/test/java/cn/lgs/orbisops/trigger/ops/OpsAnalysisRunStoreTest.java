package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAnalysisRunStoreTest {

    @Test
    void shouldUseBoundedMemoryFallbackAndKeepNewestRecords() {
        IAnalysisRunRepository repository = mock(IAnalysisRunRepository.class);
        when(repository.available()).thenReturn(false);
        OpsAnalysisRunStore store = new OpsAnalysisRunStore(
                repository,
                new OpsAnalysisRunPersistenceMapper(),
                new OpsAnalysisRunSettings(2, true, true));

        store.save(run("run-1", "project-a", OpsAnalysisRunService.STATUS_RUNNING, "2026-07-28 10:00:00"));
        store.save(run("run-2", "project-a", OpsAnalysisRunService.STATUS_PENDING, "2026-07-28 10:01:00"));
        store.save(run("run-3", "project-b", OpsAnalysisRunService.STATUS_SUCCEEDED, "2026-07-28 10:02:00"));

        List<OpsAgentRunRecordDTO> recent = store.list(100);
        assertAll(
                () -> assertEquals(List.of("run-3", "run-2"),
                        recent.stream().map(OpsAgentRunRecordDTO::getRunId).toList()),
                () -> assertFalse(store.get("run-1").isPresent()),
                () -> assertTrue(store.get("run-2").isPresent()),
                () -> assertEquals(1, store.activeCountByProject(" project-a ")),
                () -> assertEquals(0, store.activeCountByProject("project-b")));
    }

    @Test
    void shouldFailClosedWhenRepositoryUnavailableAndFallbackDisabled() {
        IAnalysisRunRepository repository = mock(IAnalysisRunRepository.class);
        when(repository.available()).thenReturn(false);
        OpsAnalysisRunStore store = new OpsAnalysisRunStore(
                repository,
                new OpsAnalysisRunPersistenceMapper(),
                new OpsAnalysisRunSettings(10, false, true));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> store.save(run("run-1", "project-a", OpsAnalysisRunService.STATUS_PENDING, "now")));

        assertTrue(error.getMessage().contains("ANALYSIS_TASK_STORE_UNAVAILABLE"));
    }

    private OpsAgentRunRecordDTO run(String runId, String projectId, String status, String updatedAt) {
        return OpsAgentRunRecordDTO.builder()
                .runId(runId)
                .status(status)
                .request(OpsAgentRunRequestDTO.builder().projectId(projectId).build())
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .build();
    }
}
