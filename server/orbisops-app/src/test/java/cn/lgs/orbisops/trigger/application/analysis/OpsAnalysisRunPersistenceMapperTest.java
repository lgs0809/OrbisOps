package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisRunPersistenceMapperTest {

    @Test
    void mapperRoundTripsRequestResponseAndStorageIdentity() {
        OpsAnalysisRunPersistenceMapper mapper = new OpsAnalysisRunPersistenceMapper();
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .projectId(" project-1 ")
                .triggerSource(" ADMIN ")
                .question("why slow")
                .build();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("analysis-1")
                .markdownReport("report")
                .build();
        OpsAgentRunRecordDTO record = OpsAgentRunRecordDTO.builder()
                .runId("run-1")
                .status("SUCCEEDED")
                .request(request)
                .response(response)
                .createdAt("created")
                .updatedAt("updated")
                .durationMs(120L)
                .build();

        AnalysisRunSnapshot snapshot = mapper.snapshot(record);
        OpsAgentRunRecordDTO restored = mapper.record(snapshot);

        assertEquals("project-1", snapshot.projectId());
        assertEquals("ADMIN", snapshot.triggerSource());
        assertTrue(snapshot.requestJson().contains("why slow"));
        assertTrue(snapshot.responseJson().contains("analysis-1"));
        assertEquals("run-1", restored.getRunId());
        assertEquals("why slow", restored.getRequest().getQuestion());
        assertEquals("analysis-1", restored.getResponse().getAnalysisId());
        assertEquals(120L, restored.getDurationMs());
    }

    @Test
    void unreadableSnapshotsKeepRunMetadataAndDropOnlyInvalidPayload() {
        OpsAnalysisRunPersistenceMapper mapper = new OpsAnalysisRunPersistenceMapper();
        AnalysisRunSnapshot snapshot = new AnalysisRunSnapshot(
                "run-1",
                "project-1",
                "ADMIN",
                "FAILED",
                "invalid-request",
                "invalid-response",
                "boom",
                "created",
                "updated",
                10L);

        OpsAgentRunRecordDTO restored = mapper.record(snapshot);

        assertEquals("run-1", restored.getRunId());
        assertEquals("FAILED", restored.getStatus());
        assertEquals("boom", restored.getErrorMessage());
        assertNull(restored.getRequest());
        assertNull(restored.getResponse());
    }
}
