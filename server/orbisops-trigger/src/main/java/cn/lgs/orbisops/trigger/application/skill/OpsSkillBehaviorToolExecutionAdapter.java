package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillBehaviorToolExecutionPort;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolExecutionRequest;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolResult;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Skill Replay ACL into the unified Tool Execution application service. */
@Component
public final class OpsSkillBehaviorToolExecutionAdapter
        implements SkillBehaviorToolExecutionPort {

    private final ToolExecutionApplicationService toolExecution;

    public OpsSkillBehaviorToolExecutionAdapter(
            ToolExecutionApplicationService toolExecution) {
        if (toolExecution == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_TOOL_EXECUTION_REQUIRED");
        }
        this.toolExecution = toolExecution;
    }

    @Override
    public SkillBehaviorToolResult execute(
            SkillBehaviorToolExecutionRequest request) {
        ToolExecutionResponse response = toolExecution.execute(new ToolExecutionRequest(
                request.projectId(),
                "",
                request.actor(),
                request.call().toolsetId(),
                request.call().toolName(),
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                request.call().arguments(),
                request.sessionId(),
                request.runId(),
                Map.of(
                        "skillReplay", true,
                        "skillReplayId", request.replayId(),
                        "skillReplayArm", request.arm().name(),
                        "skillReplayMode", request.mode().name(),
                        "skillReplayToolCallIndex", request.toolCallIndex(),
                        "changePackageProposal", request.call().changePackageProposal(),
                        "idempotencyKey", idempotencyKey(request),
                        "workflowNodeId", "skill-replay:" + request.replayId() + ":" + request.arm().name(),
                        "workflowAttempt", 1,
                        "workflowToolCallIndex", request.toolCallIndex()),
                Map.of()));
        return new SkillBehaviorToolResult(
                response.recorded().resultId(),
                response.allowed(),
                response.recorded().outputHash(),
                response.payload());
    }

    private String idempotencyKey(SkillBehaviorToolExecutionRequest request) {
        String hash = CanonicalObjectHasher.sha256(Map.of(
                "runId", request.runId(),
                "replayId", request.replayId(),
                "arm", request.arm().name(),
                "toolCallIndex", request.toolCallIndex()));
        return "skill-replay:" + hash;
    }
}
