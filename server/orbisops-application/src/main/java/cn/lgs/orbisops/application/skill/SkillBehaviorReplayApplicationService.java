package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.service.SkillBehaviorAdmissionPolicy;

import java.util.Map;

public final class SkillBehaviorReplayApplicationService {

    public static final String PRODUCTION_WRITE_FORBIDDEN =
            "SKILL_REPLAY_PRODUCTION_WRITE_FORBIDDEN";

    private final SkillBehaviorReplayPort replayPort;
    private final SkillBehaviorToolExecutionPort toolExecutionPort;
    private final SkillBehaviorAdmissionPolicy admissionPolicy;

    public SkillBehaviorReplayApplicationService(
            SkillBehaviorReplayPort replayPort,
            SkillBehaviorToolExecutionPort toolExecutionPort) {
        this(replayPort, toolExecutionPort, new SkillBehaviorAdmissionPolicy());
    }

    SkillBehaviorReplayApplicationService(
            SkillBehaviorReplayPort replayPort,
            SkillBehaviorToolExecutionPort toolExecutionPort,
            SkillBehaviorAdmissionPolicy admissionPolicy) {
        if (replayPort == null || toolExecutionPort == null || admissionPolicy == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_DEPENDENCY_REQUIRED");
        }
        this.replayPort = replayPort;
        this.toolExecutionPort = toolExecutionPort;
        this.admissionPolicy = admissionPolicy;
    }

    public SkillBehaviorEvaluation evaluate(SkillBehaviorReplayRequest request) {
        if (request == null) throw new IllegalArgumentException("SKILL_REPLAY_REQUEST_REQUIRED");
        SkillBehaviorReplayResult noSkill = replay(request, SkillBehaviorReplayArm.NO_SKILL, "");
        SkillBehaviorReplayResult baseline = replay(
                request, SkillBehaviorReplayArm.BASELINE,
                request.frozenSnapshot().baselineSkillHash());
        SkillBehaviorReplayResult candidate = replay(
                request, SkillBehaviorReplayArm.CANDIDATE,
                request.frozenSnapshot().candidateSkillHash());
        return admissionPolicy.evaluate(
                request.evaluationId(), noSkill, baseline, candidate,
                request.minimumSuccessDelta(), request.maximumCostMicros(),
                request.frozenSnapshot().verifierVersion());
    }

    private SkillBehaviorReplayResult replay(
            SkillBehaviorReplayRequest request,
            SkillBehaviorReplayArm arm,
            String skillHash) {
        SkillBehaviorReplayArmRequest armRequest = new SkillBehaviorReplayArmRequest(
                request.replayId(), request.fixtureId(), request.projectId(), request.actor(),
                request.sessionId(), request.runId(), request.skillId(), arm, skillHash,
                request.mode(), request.frozenSnapshot(), request.armFixtures().get(arm));
        SkillBehaviorReplayResult result = replayPort.replay(
                armRequest,
                toolRequest -> executeTool(request, armRequest, toolRequest));
        if (result == null || result.arm() != arm) {
            throw new IllegalStateException("SKILL_REPLAY_ARM_RESULT_INVALID:" + arm);
        }
        if (!skillHash.equals(result.activeSkillHash())) {
            throw new IllegalStateException("SKILL_REPLAY_SKILL_HASH_MISMATCH:" + arm);
        }
        return result;
    }

    private SkillBehaviorToolResult executeTool(
            SkillBehaviorReplayRequest request,
            SkillBehaviorReplayArmRequest armRequest,
            SkillBehaviorToolExecutionRequest toolRequest) {
        if (toolRequest == null || toolRequest.call() == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_TOOL_REQUEST_REQUIRED");
        }
        if (!request.replayId().equals(toolRequest.replayId())
                || armRequest.arm() != toolRequest.arm()
                || armRequest.mode() != toolRequest.mode()
                || !request.projectId().equals(toolRequest.projectId())
                || !request.sessionId().equals(toolRequest.sessionId())
                || !request.runId().equals(toolRequest.runId())) {
            throw new SecurityException("SKILL_REPLAY_TOOL_CONTEXT_MISMATCH");
        }
        SkillBehaviorToolCall call = toolRequest.call();
        if (call.productionWrite() || call.directLanding()) {
            throw new SecurityException(PRODUCTION_WRITE_FORBIDDEN);
        }
        return switch (request.mode()) {
            case FROZEN_MOCK -> frozenResult(request.frozenToolResults(), call);
            case READ_ONLY -> {
                if (!call.readOnly() || call.changePackageProposal()) {
                    throw new SecurityException("SKILL_REPLAY_READ_ONLY_TOOL_REQUIRED");
                }
                yield toolExecutionPort.execute(toolRequest);
            }
            case SANDBOX_DRY_RUN -> {
                if (!call.readOnly() && !call.changePackageProposal()) {
                    throw new SecurityException("SKILL_REPLAY_CHANGE_PACKAGE_PROPOSAL_REQUIRED");
                }
                yield toolExecutionPort.execute(toolRequest);
            }
        };
    }

    private SkillBehaviorToolResult frozenResult(
            Map<String, SkillBehaviorToolResult> results,
            SkillBehaviorToolCall call) {
        String resultId = call.frozenResultId();
        if (resultId.isBlank()) {
            throw new IllegalArgumentException("SKILL_REPLAY_FROZEN_RESULT_REQUIRED");
        }
        SkillBehaviorToolResult result = results.get(resultId);
        if (result == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_FROZEN_RESULT_NOT_FOUND:" + resultId);
        }
        return result;
    }
}
