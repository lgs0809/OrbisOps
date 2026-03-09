package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowBoundToolInvocation;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowBoundToolResult;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkflowBoundToolExecutionAdapterTest {

    @Test
    void workflowToolMustUseUnifiedPreApprovalChainAndPreserveEvidence() {
        ToolExecutionApplicationService execution = mock(ToolExecutionApplicationService.class);
        ToolExecutionResponse response = mock(ToolExecutionResponse.class);
        ToolExecutionRecordedResult recorded = new ToolExecutionRecordedResult(
                "result-1", "evidence-1", "preview",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                false, "memory://result-1",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                9L);
        when(response.recorded()).thenReturn(recorded);
        when(response.allowed()).thenReturn(true);
        when(response.payload()).thenReturn(Map.of("status", "ok"));
        when(execution.execute(any(ToolExecutionRequest.class))).thenReturn(response);
        OpsWorkflowBoundToolExecutionAdapter adapter =
                new OpsWorkflowBoundToolExecutionAdapter(execution);
        BoundWorkflowResourceSnapshot resource = new BoundWorkflowResourceSnapshot(
                BoundWorkflowResourceKind.TOOL, "tool-resource", 7,
                "tool-definition-hash", "TOOLSET_SNAPSHOT", true, true);

        WorkflowBoundToolResult result = adapter.execute(new WorkflowBoundToolInvocation(
                "project-1", "actor-1", "session-1", "run-1", "tool-node",
                2, 3, "run-1:tool-node:2:3", "toolset-1", "inspect",
                resource, Map.of("target", "mysql"), true, false));

        ArgumentCaptor<ToolExecutionRequest> captor =
                ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(execution).execute(captor.capture());
        ToolExecutionRequest request = captor.getValue();
        assertEquals(ToolExecutionScope.PRE_APPROVAL_WORKFLOW, request.scope());
        assertEquals(Map.of(), request.landingContext());
        assertEquals("run-1:tool-node:2:3", request.requestContext().get("idempotencyKey"));
        assertEquals("tool-node", request.requestContext().get("workflowNodeId"));
        assertEquals(true, request.requestContext().get("workflowTool"));
        assertTrue(String.valueOf(request.requestContext().get("boundResourceSnapshotHash"))
                .matches("[0-9a-f]{64}"));
        assertEquals("result-1", result.resultId());
        assertEquals("evidence-1", result.evidenceId());
        assertEquals(recorded.outputHash(), result.outputHash());
    }
}
