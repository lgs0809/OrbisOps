package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpsWorkSessionRunAdapter {

    private final WorkSessionRunApplicationService application;
    private final OpsWorkSessionRunMapper mapper;

    public OpsWorkSessionRunAdapter(
            WorkSessionRunApplicationService application,
            OpsWorkSessionRunMapper mapper) {
        if (application == null) throw new IllegalArgumentException("WORK_SESSION_RUN_APPLICATION_REQUIRED");
        if (mapper == null) throw new IllegalArgumentException("WORK_SESSION_RUN_MAPPER_REQUIRED");
        this.application = application;
        this.mapper = mapper;
    }

    public WorkSessionRunClaim begin(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            OpsRuntimeExecutionPlan plan,
            OpsExecutionHarness harness) {
        WorkSessionRunClaim claim = application.begin(
                mapper.startCommand(request, definition, plan, harness));
        mapper.bindStart(request, definition, plan, harness, claim);
        return claim;
    }

    public void bindContextBundle(OpsAgentChatRequest request) {
        WorkSessionRunClaim claim = mapper.claim(request);
        String manifestHash = application.bindContextBundle(
                claim,
                request == null ? Map.of() : mapper.persistableMetadata(request.getMetadata()));
        mapper.bindManifestHash(request, manifestHash);
    }

    public void heartbeat(OpsAgentChatRequest request) {
        if (!mapper.hasClaim(request)) return;
        application.heartbeat(mapper.claim(request));
    }

    public void suspendForApproval(OpsAgentChatRequest request) {
        if (!mapper.hasClaim(request)) {
            throw new IllegalStateException("WORK_SESSION_APPROVAL_WAIT_REQUIRES_CLAIM");
        }
        application.suspendForApproval(mapper.claim(request));
    }

    public void checkpoint(
            OpsAgentChatRequest request,
            String checkpointType,
            Map<String, Object> payload) {
        if (!mapper.hasClaim(request)) return;
        application.checkpoint(mapper.claim(request), checkpointType, payload);
    }

    public void checkpointToolExecution(
            Map<String, Object> request,
            String checkpointType,
            Map<String, Object> payload) {
        WorkSessionRunClaim claim = mapper.toolClaim(request);
        if (claim == null) return;
        String projectionId = text(payload == null ? null : payload.get("projectionId"));
        String deliveryKey = projectionId.isBlank()
                ? ""
                : "tool-completion-checkpoint:" + projectionId;
        application.checkpoint(claim, checkpointType, payload, deliveryKey);
    }

    public Map<String, Object> latestCheckpoint(
            String runId,
            String projectId,
            String checkpointTypePrefix) {
        return application.latestCheckpoint(runId, projectId, checkpointTypePrefix).payload();
    }

    public void finish(
            OpsAgentChatRequest request,
            String terminalStatus,
            String errorMessage) {
        finish(request, terminalStatus, errorMessage, null);
    }

    public void finish(
            OpsAgentChatRequest request,
            String terminalStatus,
            String errorMessage,
            OpsAgentChatResponse response) {
        if (!mapper.hasClaim(request)) return;
        application.finish(
                mapper.claim(request),
                terminalStatus,
                errorMessage,
                mapper.responsePayload(response));
    }

    public boolean requestCancel(
            String runId,
            String projectId,
            String actor,
            String reason) {
        return application.requestCancel(runId, projectId, actor, reason);
    }

    public boolean requestCancelForActor(String runId, String actor, String reason) {
        return application.requestCancelForActor(runId, actor, reason);
    }

    public boolean cancelRequested(String runId, String projectId) {
        return application.cancelRequested(runId, projectId);
    }

    public Map<String, Object> get(String runId, String projectId) {
        return mapper.view(application.get(runId, projectId));
    }

    public void assertActorCanRead(String runId, String projectId, String actor) {
        application.assertActorCanRead(runId, projectId, actor);
    }

    public OpsAgentChatRequest resumeRequest(String runId, String projectId, String actor) {
        WorkSessionRunSnapshot snapshot = application.resume(runId, projectId, actor);
        return mapper.resumeRequest(snapshot);
    }

    public OpsAgentChatRequest approvalResumeRequest(String runId, String projectId, String actor) {
        WorkSessionRunSnapshot snapshot = application.resumeApproval(runId, projectId, actor);
        return mapper.resumeRequest(snapshot);
    }

    public OpsAgentChatRequest recoveryRequest(
            String runId,
            String projectId,
            String expiredAttemptId) {
        WorkSessionRunSnapshot snapshot = application.recoverySnapshot(
                runId, projectId, expiredAttemptId);
        return mapper.resumeRequest(snapshot);
    }

    public List<WorkSessionRecoveryPort.RecoveryDecision> recoverExpiredLeases(int limit) {
        return application.recoverExpiredLeases(limit).stream()
                .map(mapper::recoveryView)
                .toList();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
