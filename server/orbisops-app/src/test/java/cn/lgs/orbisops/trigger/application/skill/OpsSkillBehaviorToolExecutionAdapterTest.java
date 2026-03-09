package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillBehaviorToolCall;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolExecutionRequest;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolResult;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillBehaviorToolExecutionAdapterTest {

    @Test
    void replayToolMustUseUnifiedPreApprovalExecutionScope() {
        ToolExecutionApplicationService execution = mock(ToolExecutionApplicationService.class);
        ToolExecutionResponse response = mock(ToolExecutionResponse.class);
        ToolExecutionRecordedResult recorded = new ToolExecutionRecordedResult(
                "result-1", "evidence-1", "preview",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                false, "memory://result-1",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                12L);
        when(response.recorded()).thenReturn(recorded);
        when(response.allowed()).thenReturn(true);
        when(response.payload()).thenReturn(Map.of("status", "ok"));
        when(execution.execute(
                org.mockito.ArgumentMatchers.any(ToolExecutionRequest.class)))
                .thenReturn(response);
        OpsSkillBehaviorToolExecutionAdapter adapter =
                new OpsSkillBehaviorToolExecutionAdapter(execution);

        SkillBehaviorToolResult result = adapter.execute(new SkillBehaviorToolExecutionRequest(
                "replay-1", "project-1", "actor-1", "session-1", "run-1",
                SkillBehaviorReplayArm.CANDIDATE,
                SkillBehaviorReplayExecutionMode.SANDBOX_DRY_RUN,
                2,
                new SkillBehaviorToolCall(
                        "toolset-1", "prepare_change", Map.of("target", "mysql"),
                        false, false, true, false, "")));

        ArgumentCaptor<ToolExecutionRequest> captor =
                ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(execution).execute(captor.capture());
        ToolExecutionRequest request = captor.getValue();
        assertEquals(ToolExecutionScope.PRE_APPROVAL_WORKFLOW, request.scope());
        assertEquals("toolset-1", request.toolsetId());
        assertEquals("prepare_change", request.toolName());
        assertEquals(Map.of(), request.landingContext());
        assertEquals(true, request.requestContext().get("skillReplay"));
        assertEquals("CANDIDATE", request.requestContext().get("skillReplayArm"));
        assertEquals("SANDBOX_DRY_RUN", request.requestContext().get("skillReplayMode"));
        assertEquals(true, request.requestContext().get("changePackageProposal"));
        assertTrue(String.valueOf(request.requestContext().get("idempotencyKey"))
                .matches("skill-replay:[a-f0-9]{64}"));
        assertEquals("skill-replay:replay-1:CANDIDATE",
                request.requestContext().get("workflowNodeId"));
        assertEquals(1, request.requestContext().get("workflowAttempt"));
        assertEquals(2, request.requestContext().get("workflowToolCallIndex"));
        assertEquals("result-1", result.resultId());
        assertTrue(result.allowed());
    }
}
