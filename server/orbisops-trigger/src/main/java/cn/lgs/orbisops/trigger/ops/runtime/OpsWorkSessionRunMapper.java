package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.run.WorkSessionRunStartCommand;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryDecision;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsWorkSessionRunMapper {

    private final OpsAgentRunExecutionContextFactory executionContextFactory =
            new OpsAgentRunExecutionContextFactory();

    public WorkSessionRunStartCommand startCommand(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            OpsRuntimeExecutionPlan plan,
            OpsExecutionHarness harness) {
        if (request == null) throw new IllegalArgumentException("runId 不能为空");
        Map<String, Object> metadata = executionContextFactory.persistableMetadata(
                request.getMetadata());
        Map<String, Object> promptIdentity = new LinkedHashMap<>();
        promptIdentity.put("instruction", text(definition == null ? null : definition.getInstruction()));
        promptIdentity.put("nodes", definition == null || definition.getNodes() == null
                ? List.of() : definition.getNodes());
        promptIdentity.put("edges", definition == null || definition.getEdges() == null
                ? List.of() : definition.getEdges());
        Map<String, Object> requestIdentity = new LinkedHashMap<>();
        requestIdentity.put("query", text(request.getQuery()));
        requestIdentity.put("projectId", text(request.getProjectId()));
        requestIdentity.put("agentId", text(definition == null ? null : definition.getAgentId()));
        requestIdentity.put("agentVersion", definition == null || definition.getVersion() == null
                ? 0 : definition.getVersion());
        return new WorkSessionRunStartCommand(
                text(request.getRunId()),
                text(request.getProjectId()),
                text(request.getSessionId()),
                text(request.getUserId()),
                text(definition == null ? null : definition.getAgentId()),
                definition == null ? null : definition.getVersion(),
                text(definition == null ? null : definition.getDefinitionHash()),
                harness == null ? "" : harness.name(),
                harness == null ? 0 : harness.version(),
                harness == null ? "" : harness.harnessHash(),
                text(plan == null ? null : plan.getEngine()),
                text(plan == null ? null : plan.getAdapterKey()),
                text(request.getModelId()),
                firstText(metadata.get("modelProfileId"), request.getModelId()),
                number(metadata.getOrDefault("modelProfileVersion", 1L)),
                promptIdentity,
                requestIdentity,
                requestPayload(request),
                metadata);
    }

    public void bindStart(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            OpsRuntimeExecutionPlan plan,
            OpsExecutionHarness harness,
            WorkSessionRunClaim claim) {
        request.setAgentDefinitionId(definition == null ? null : definition.getAgentId());
        request.setAgentVersion(definition == null ? null : definition.getVersion());
        Map<String, Object> metadata = metadata(request);
        metadata.put("agentId", text(definition == null ? null : definition.getAgentId()));
        metadata.put("agentVersion", definition == null ? null : definition.getVersion());
        metadata.put("agentDefinitionHash", text(definition == null ? null : definition.getDefinitionHash()));
        metadata.put("engine", text(plan == null ? null : plan.getEngine()));
        metadata.put("adapterKey", text(plan == null ? null : plan.getAdapterKey()));
        bindClaim(metadata, claim);
        metadata.put("executionHarness", harness == null ? "" : harness.name());
    }

    public WorkSessionRunClaim claim(OpsAgentChatRequest request) {
        if (!hasClaim(request)) return null;
        Map<String, Object> metadata = request.getMetadata();
        return new WorkSessionRunClaim(
                required(request.getRunId(), "runId"),
                required(request.getProjectId(), "projectId"),
                required(text(metadata.get(OpsWorkSessionClaimMetadata.ATTEMPT_ID)), "attemptId"),
                required(text(metadata.get(OpsWorkSessionClaimMetadata.LEASE_TOKEN)), "leaseToken"),
                number(metadata.get(OpsWorkSessionClaimMetadata.FENCING_TOKEN)),
                number(metadata.get(OpsWorkSessionClaimMetadata.STATE_VERSION)),
                required(text(metadata.get(OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH)), "runManifestHash"));
    }

    public WorkSessionRunClaim toolClaim(Map<String, Object> request) {
        if (request == null) return null;
        Map<String, Object> metadata = objectMap(request.get("metadata"));
        if (text(metadata.get(OpsWorkSessionClaimMetadata.ATTEMPT_ID)).isBlank()) return null;
        return new WorkSessionRunClaim(
                required(text(request.get("runId")), "runId"),
                required(text(request.get("projectId")), "projectId"),
                required(text(metadata.get(OpsWorkSessionClaimMetadata.ATTEMPT_ID)), "attemptId"),
                required(text(metadata.get(OpsWorkSessionClaimMetadata.LEASE_TOKEN)), "leaseToken"),
                number(metadata.get(OpsWorkSessionClaimMetadata.FENCING_TOKEN)),
                number(metadata.get(OpsWorkSessionClaimMetadata.STATE_VERSION)),
                required(text(metadata.get(OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH)), "runManifestHash"));
    }

    public boolean hasClaim(OpsAgentChatRequest request) {
        return request != null && request.getMetadata() != null
                && !text(request.getMetadata().get(OpsWorkSessionClaimMetadata.ATTEMPT_ID)).isBlank()
                && !text(request.getMetadata().get(OpsWorkSessionClaimMetadata.LEASE_TOKEN)).isBlank();
    }

    public void bindManifestHash(OpsAgentChatRequest request, String manifestHash) {
        metadata(request).put(OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH, text(manifestHash));
    }

    Map<String, Object> persistableMetadata(Map<String, Object> metadata) {
        return executionContextFactory.persistableMetadata(metadata);
    }

    public Map<String, Object> responsePayload(OpsAgentChatResponse response) {
        if (response == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", response.getSessionId());
        result.put("userId", response.getUserId());
        result.put("agentId", response.getAgentId());
        result.put("agentVersion", response.getAgentVersion());
        result.put("mode", response.getMode());
        result.put("engine", response.getEngine());
        result.put("content", response.getContent());
        result.put("events", eventPayloads(response.getEvents()));
        result.put("metadata", executionContextFactory.persistableMetadata(response.getMetadata()));
        return result;
    }

    public Map<String, Object> view(WorkSessionRunSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run_id", snapshot.runId());
        result.put("project_id", snapshot.projectId());
        result.put("session_id", snapshot.sessionId());
        result.put("user_id", snapshot.owner());
        result.put("agent_id", snapshot.agentId());
        result.put("agent_version", snapshot.agentVersion());
        result.put("agent_definition_hash", snapshot.agentDefinitionHash());
        result.put("execution_harness", snapshot.executionHarness());
        result.put("status", snapshot.status().name());
        result.put("current_attempt_id", snapshot.currentAttemptId());
        result.put("state_version", snapshot.stateVersion());
        result.put("fencing_token", snapshot.fencingToken());
        result.put("worker_id", snapshot.workerId());
        result.put("lease_token", snapshot.leaseToken());
        result.put("lease_expires_at", timestamp(snapshot.leaseExpiresAt()));
        result.put("cancel_requested", snapshot.cancelRequested() ? 1 : 0);
        result.put("run_manifest_hash", snapshot.manifestHash());
        result.put("error_message", snapshot.errorMessage());
        result.put("created_at", timestamp(snapshot.createdAt()));
        result.put("updated_at", timestamp(snapshot.updatedAt()));
        result.put("runManifest", snapshot.manifest());
        result.put("response", snapshot.responsePayload());
        return result;
    }

    public OpsAgentChatRequest resumeRequest(WorkSessionRunSnapshot snapshot) {
        Map<String, Object> payload = snapshot.requestPayload();
        OpsAgentChatRequest request = new OpsAgentChatRequest();
        request.setUserId(snapshot.owner());
        request.setSessionId(firstText(payload.get("sessionId"), snapshot.sessionId()));
        request.setRunId(snapshot.runId());
        request.setQuery(text(payload.get("query")));
        request.setMode(text(payload.get("mode")));
        request.setEngine(text(payload.get("engine")));
        request.setProjectId(snapshot.projectId());
        request.setAgentDefinitionId(snapshot.agentId());
        request.setAgentVersion(snapshot.agentVersion());
        request.setPreviewDraft(bool(payload.get("previewDraft")));
        request.setModelId(text(payload.get("modelId")));
        request.setRagEnabled(bool(payload.get("ragEnabled")));
        request.setKnowledgeBaseId(text(payload.get("knowledgeBaseId")));
        request.setEnableThinking(bool(payload.get("enableThinking")));
        request.setTrustedObserveOnly(bool(payload.get("trustedObserveOnly")));
        Map<String, Object> metadata = objectMap(payload.get("metadata"));
        metadata.keySet().removeIf(key -> key.startsWith("_workSession"));
        executionContextFactory.restorePersistedAuthority(metadata);
        metadata.put("executionHarness", snapshot.executionHarness());
        metadata.put("resumedFromAttemptId", snapshot.currentAttemptId());
        String contextBundleId = text(snapshot.manifest().get("contextBundleId"));
        String contextBundleHash = text(snapshot.manifest().get("contextBundleHash"));
        if (!contextBundleId.isBlank() && !contextBundleHash.isBlank()) {
            metadata.put("contextBundleId", contextBundleId);
            metadata.put("contextBundleHash", contextBundleHash);
            metadata.put("resumeContextBundlePinned", true);
        }
        request.setMetadata(metadata);
        return request;
    }

    public cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort.RecoveryDecision recoveryView(
            WorkSessionRecoveryDecision decision) {
        return new cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort.RecoveryDecision(
                decision.runId(), decision.projectId(), decision.attemptId(),
                decision.status().name(), decision.reasonCode());
    }

    private Map<String, Object> requestPayload(OpsAgentChatRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", request.getUserId());
        result.put("sessionId", request.getSessionId());
        result.put("runId", request.getRunId());
        result.put("query", request.getQuery());
        result.put("mode", request.getMode());
        result.put("engine", request.getEngine());
        result.put("projectId", request.getProjectId());
        result.put("agentDefinitionId", request.getAgentDefinitionId());
        result.put("agentVersion", request.getAgentVersion());
        result.put("previewDraft", request.getPreviewDraft());
        result.put("modelId", request.getModelId());
        result.put("ragEnabled", request.getRagEnabled());
        result.put("knowledgeBaseId", request.getKnowledgeBaseId());
        result.put("enableThinking", request.getEnableThinking());
        result.put("trustedObserveOnly", Boolean.TRUE.equals(request.getTrustedObserveOnly()));
        result.put("metadata", executionContextFactory.persistableMetadata(request.getMetadata()));
        return result;
    }

    private List<Map<String, Object>> eventPayloads(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (OpsRuntimeEvent event : events) {
            if (event == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventType", event.getEventType());
            item.put("nodeId", event.getNodeId());
            item.put("nodeType", event.getNodeType());
            item.put("agent", event.getAgent());
            item.put("source", event.getSource());
            item.put("status", event.getStatus());
            item.put("summary", event.getSummary());
            item.put("content", event.getContent());
            item.put("timestamp", event.getTimestamp());
            item.put("payload", event.getPayload() == null ? Map.of() : event.getPayload());
            result.add(item);
        }
        return List.copyOf(result);
    }

    private void bindClaim(Map<String, Object> metadata, WorkSessionRunClaim claim) {
        metadata.put(OpsWorkSessionClaimMetadata.ATTEMPT_ID, claim.attemptId());
        metadata.put(OpsWorkSessionClaimMetadata.LEASE_TOKEN, claim.leaseToken());
        metadata.put(OpsWorkSessionClaimMetadata.FENCING_TOKEN, claim.fencingToken());
        metadata.put(OpsWorkSessionClaimMetadata.STATE_VERSION, claim.stateVersion());
        metadata.put(OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH, claim.runManifestHash());
    }

    private Map<String, Object> metadata(OpsAgentChatRequest request) {
        if (request.getMetadata() == null) request.setMetadata(new LinkedHashMap<>());
        return request.getMetadata();
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Boolean bool(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(text(value));
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String required(String value, String field) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
