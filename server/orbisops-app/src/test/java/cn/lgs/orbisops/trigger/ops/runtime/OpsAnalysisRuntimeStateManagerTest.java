package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OpsAnalysisRuntimeStateManagerTest {

    @Test
    void restoresCanonicalRunStateAndPublishesLifecycleOnlyOnce() {
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRunCancellationRegistry cancellationRegistry = mock(OpsRunCancellationRegistry.class);

        OpsAnalysisRuntimeStateManager manager =
                OpsAnalysisRuntimeStateManagerTestFactory.create(
                        graphEventService,
                        cancellationRegistry);
        OpsAgentRunRequestDTO analysisRequest = OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .query("check errors")
                .build();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("analysis-1")
                .build();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        metadata.put(OpsAnalysisRuntimeMetadata.RESPONSE_KEY, response);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1")
                .query("check errors")
                .metadata(metadata)
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops-main")
                .version(7)
                .engine("graph")
                .build();

        OpsAnalysisRuntimeStateManager.State first = manager.ensure(definition, request);
        request.getMetadata().remove("_opsAnalysisState");
        OpsAnalysisRuntimeStateManager.State recovered = manager.ensure(definition, request);

        assertSame(first, recovered);
        manager.publishRunStarted(definition, first);
        assertTrue(first.runStarted().get());
        manager.publishRunStarted(definition, first);
        assertTrue(manager.publishRunFinished(definition, first, "SUCCEEDED", "done"));
        assertFalse(manager.publishRunFinished(definition, first, "SUCCEEDED", "done again"));

        verify(graphEventService, times(1)).publishRunEvent(
                eq("run-1"), eq("analysis-1"), eq("RUN_STARTED"), eq("RUNNING"), anyString());
        verify(graphEventService, times(1)).publishRunEvent(
                eq("run-1"), eq("analysis-1"), eq("RUN_FINISHED"), eq("SUCCEEDED"), eq("done"));
    }
}
