package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillToolProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAnalysisRuntimeStateBoundariesTest {

    @Test
    void skillReadinessMustReportUnavailableProviderForAllRequiredSkills() {
        OpsAnalysisSkillReadinessInspector inspector =
                new OpsAnalysisSkillReadinessInspector(() -> null);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-a")
                .skills(List.of("skill-a"))
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("node-a")
                        .skills(List.of("skill-b"))
                        .build()))
                .build();

        List<String> notes = inspector.inspect(definition);

        assertEquals(1, notes.size());
        assertTrue(notes.get(0).contains("SkillToolProvider 未初始化"));
        assertTrue(notes.get(0).contains("skill-a,skill-b"));
    }

    @Test
    void skillReadinessMustReportOnlyMissingSkills() {
        OpsSkillToolProvider provider = mock(OpsSkillToolProvider.class);
        when(provider.listSkillSummaries()).thenReturn(List.of(
                new OpsSkillToolProvider.SkillSummary(
                        "skill-a", "loaded", "/skills/a")));
        OpsAnalysisSkillReadinessInspector inspector =
                new OpsAnalysisSkillReadinessInspector(() -> provider);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-a")
                .skills(List.of("skill-a", "skill-b"))
                .build();

        List<String> notes = inspector.inspect(definition);

        assertEquals(1, notes.size());
        assertTrue(notes.get(0).contains("Agent Skill 缺失：skill-b"));
        assertTrue(notes.get(0).contains("已加载：skill-a"));
    }

    @Test
    void stateFactoryMustInitializeDefinitionSnapshotAndMutableState() {
        OpsAnalysisRuntimeStateFactory factory =
                new OpsAnalysisRuntimeStateFactory(
                        new OpsAnalysisSkillReadinessInspector(() -> null));
        OpsAgentRunRequestDTO analysisRequest = OpsAgentRunRequestDTO.builder()
                .runId("run-a")
                .build();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("analysis-a")
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .query("check errors")
                .metadata(new LinkedHashMap<>())
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-a")
                .version(3)
                .engine("GRAPH")
                .build();

        OpsAnalysisRuntimeStateManager.State state = factory.create(
                definition, request, analysisRequest, response);

        assertEquals("agent-a", analysisRequest.getAgentDefinitionId());
        assertEquals(3, analysisRequest.getAgentVersion());
        assertTrue(analysisRequest.getAgentDefinitionSnapshotJson().contains("agent-a"));
        assertEquals("agent-a", response.getAgentDefinitionId());
        assertEquals("GRAPH", response.getAgentRuntime());
        assertTrue(state.runtimeNotes().get(0).contains("agent-a / GRAPH"));
        assertTrue(state.steps().isEmpty());
        assertFalse(state.runStarted().get());
        assertFalse(state.runFinished().get());
    }

    @Test
    void responseNotesMustPrependDistinctRuntimeNotesBeforeExistingNotes() {
        OpsAnalysisResponseNotes notes = new OpsAnalysisResponseNotes();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .executionNotes(new ArrayList<>(List.of("existing", "duplicate")))
                .build();

        notes.mergeRuntimeNotes(
                response, List.of("runtime", "duplicate", "runtime"));

        assertEquals(
                List.of("runtime", "existing", "duplicate"),
                response.getExecutionNotes());
    }
}
