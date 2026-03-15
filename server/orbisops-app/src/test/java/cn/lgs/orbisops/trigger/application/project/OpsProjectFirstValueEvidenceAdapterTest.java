package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.project.ProjectFirstValueEvidencePort;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunSnapshot;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import cn.lgs.orbisops.trigger.ops.OpsStructuredReportService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsProjectFirstValueEvidenceAdapterTest {

    @Test
    void successfulReactRunWithBoundEvidenceProvesFirstValue() {
        IAnalysisRunRepository runs = mock(IAnalysisRunRepository.class);
        OpsAnalysisRunPersistenceMapper mapper = mock(OpsAnalysisRunPersistenceMapper.class);
        OpsStructuredReportService reports = mock(OpsStructuredReportService.class);
        AnalysisRunSnapshot run = new AnalysisRunSnapshot(
                "run-1", "project-a", "CHAT", "SUCCEEDED", "{}", "{}", "", "", "", 100L);
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder().executionStyle("REACT").build();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder().agentRuntime("STATE_GRAPH").build();
        OpsAgentRunRecordDTO record = OpsAgentRunRecordDTO.builder().request(request).response(response).build();
        when(runs.available()).thenReturn(true);
        when(runs.findRecent(500)).thenReturn(List.of(run));
        when(mapper.record(run)).thenReturn(record);
        when(reports.compose(response)).thenReturn(Map.of("diagnosis", Map.of(
                "facts", List.of(Map.of(
                        "factId", "fact-1",
                        "statement", "error rate high",
                        "evidenceRefs", List.of(Map.of(
                                "evidenceRef", "prometheus",
                                "resultId", "result-1",
                                "outputHash", "hash-1")))))));
        OpsProjectFirstValueEvidenceAdapter adapter = new OpsProjectFirstValueEvidenceAdapter(runs, mapper, reports);

        ProjectFirstValueEvidencePort.ProjectFirstValueEvidence result = adapter.verification("project-a");

        assertTrue(result.verified());
    }

    @Test
    void workflowOrUnboundFactDoesNotProveFirstValue() {
        IAnalysisRunRepository runs = mock(IAnalysisRunRepository.class);
        OpsAnalysisRunPersistenceMapper mapper = mock(OpsAnalysisRunPersistenceMapper.class);
        OpsStructuredReportService reports = mock(OpsStructuredReportService.class);
        AnalysisRunSnapshot run = new AnalysisRunSnapshot(
                "run-1", "project-a", "CHAT", "SUCCEEDED", "{}", "{}", "", "", "", 100L);
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder().executionStyle("WORKFLOW").build();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder().agentRuntime("STATE_GRAPH").build();
        OpsAgentRunRecordDTO record = OpsAgentRunRecordDTO.builder().request(request).response(response).build();
        when(runs.available()).thenReturn(true);
        when(runs.findRecent(500)).thenReturn(List.of(run));
        when(mapper.record(run)).thenReturn(record);
        OpsProjectFirstValueEvidenceAdapter adapter = new OpsProjectFirstValueEvidenceAdapter(runs, mapper, reports);

        assertFalse(adapter.verification("project-a").verified());
    }
}
