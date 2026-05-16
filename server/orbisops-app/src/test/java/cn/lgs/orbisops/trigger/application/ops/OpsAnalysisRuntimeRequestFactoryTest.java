package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpsAnalysisRuntimeRequestFactoryTest {

    private final OpsAnalysisRuntimeRequestFactory factory = new OpsAnalysisRuntimeRequestFactory(
            new OpsAnalysisAgentDefinitionSnapshotResolver(mock(OpsAgentDefinitionQueryGateway.class)));

    @Test
    void shouldBuildRuntimeIdentitySnapshotAndMetadataEnvelope() {
        OpsAgentDefinition snapshot = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .version(7)
                .definitionHash("hash-v7")
                .build();
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("run-7")
                .requestedBy("alice")
                .projectId("payment")
                .agentDefinitionId("ignored-agent")
                .agentVersion(1)
                .agentDefinitionSnapshotJson(JSON.toJSONString(snapshot))
                .query("原始 query")
                .question("正式 question")
                .triggerSource("alert-webhook")
                .triggerEventId("event-7")
                .build();
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();

        OpsAgentChatRequest runtime = factory.create(request, response);

        assertAll(
                () -> assertEquals("run-7", runtime.getRunId()),
                () -> assertEquals("run-7", runtime.getSessionId()),
                () -> assertEquals("alice", runtime.getUserId()),
                () -> assertEquals("payment", runtime.getProjectId()),
                () -> assertEquals("AGENT", runtime.getMode()),
                () -> assertEquals("正式 question", runtime.getQuery()),
                () -> assertEquals("ops-agent", runtime.getAgentDefinitionId()),
                () -> assertEquals(7, runtime.getAgentVersion()),
                () -> assertEquals("hash-v7", runtime.getAgentDefinition().getDefinitionHash()),
                () -> assertEquals("alert-webhook", runtime.getMetadata().get("triggerSource")),
                () -> assertEquals("event-7", runtime.getMetadata().get("triggerEventId")),
                () -> assertSame(request, runtime.getMetadata().get(WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST)),
                () -> assertSame(response, runtime.getMetadata().get(WorkSessionMetadataKeys.OPS_ANALYSIS_RESPONSE)),
                () -> assertNotNull(runtime.getMetadata().get(WorkSessionMetadataKeys.OPS_ANALYSIS_QUESTION_CONTEXT)));
    }

    @Test
    void shouldUseStableFallbacksWithoutRunIdentityOrQuestion() {
        OpsAgentChatRequest runtime = factory.create(new OpsAgentRunRequestDTO(), new OpsAnalysisResponseDTO());

        assertAll(
                () -> assertEquals("ops-analysis", runtime.getUserId()),
                () -> assertTrue(runtime.getSessionId().startsWith("ops_")),
                () -> assertEquals("分析当前业务系统最近运行状态", runtime.getQuery()),
                () -> assertEquals("ops-analysis", runtime.getMetadata().get("triggerSource")),
                () -> assertEquals("", runtime.getMetadata().get("triggerEventId")));
    }
}
