package cn.lgs.orbisops.domain.worksession.run.service;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionCheckpoint;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionParticipantRole;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryCandidate;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryDecision;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStart;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStartDraft;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkSessionRunPolicy {

    private static final Set<String> ALLOWED_HARNESSES = Set.of(
            "BUILTIN_ASSISTANT", "PROJECT_PRE_APPROVAL", "APPROVED_LANDING");
    private static final List<String> INITIAL_METADATA_KEYS = List.of(
            "mcpSnapshotRefs", "mcpPolicyRefs", "projectConfigVersion", "projectConfigHash");
    private static final List<String> CONTEXT_METADATA_KEYS = List.of(
            "contextBundleId", "contextBundleHash", "memoryContextHash",
            "usedSkillVersionRefs", "usedSkillRefsHash", "toolsetRefs", "toolsetBoundaryHash",
            "policyRefs", "policyHash", "runtimeBoundaryHash", "approvalBoundaryHash");

    public WorkSessionRunStart prepareStart(WorkSessionRunStartDraft draft) {
        if (draft == null) throw new IllegalArgumentException("WORK_SESSION_START_REQUIRED");
        if (draft.agentVersion() == null || draft.agentVersion() <= 0
                || draft.agentId().isBlank() || draft.agentDefinitionHash().isBlank()) {
            throw new IllegalArgumentException("WORK_SESSION_AGENT_VERSION_NOT_PINNED");
        }
        String executionHarness = draft.executionHarness().trim().toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_HARNESSES.contains(executionHarness)) {
            throw new IllegalArgumentException("WORK_SESSION_HARNESS_INVALID");
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", required(draft.runId(), "runId"));
        manifest.put("sessionId", required(draft.sessionId(), "sessionId"));
        manifest.put("projectId", required(draft.projectId(), "projectId"));
        manifest.put("actor", required(draft.actor(), "actor"));
        manifest.put("executionHarness", executionHarness);
        manifest.put("harnessVersion", draft.harnessVersion());
        manifest.put("harnessHash", draft.harnessHash());
        manifest.put("agentId", draft.agentId());
        manifest.put("agentVersion", draft.agentVersion());
        manifest.put("agentDefinitionHash", draft.agentDefinitionHash());
        manifest.put("engine", draft.engine());
        manifest.put("adapterKey", draft.adapterKey());
        manifest.put("modelId", draft.modelId());
        manifest.put("modelProfileId", draft.modelProfileId().isBlank()
                ? draft.modelId() : draft.modelProfileId());
        manifest.put("modelProfileVersion", Math.max(1L, draft.modelProfileVersion()));
        manifest.put("promptHash", CanonicalObjectHasher.sha256(draft.promptIdentity()));
        copy(draft.metadata(), manifest, INITIAL_METADATA_KEYS);
        manifest.put("requestHash", CanonicalObjectHasher.sha256(draft.requestIdentity()));
        String manifestHash = CanonicalObjectHasher.sha256(manifest);
        return new WorkSessionRunStart(
                draft.runId(),
                draft.projectId(),
                draft.sessionId(),
                draft.actor(),
                draft.agentId(),
                draft.agentVersion(),
                draft.agentDefinitionHash(),
                executionHarness,
                manifest,
                manifestHash,
                draft.requestPayload(),
                required(draft.attemptId(), "attemptId"),
                required(draft.leaseToken(), "leaseToken"),
                required(draft.workerId(), "workerId"),
                draft.startedAt(),
                draft.leaseExpiresAt());
    }

    public Map<String, Object> mergeContextManifest(
            WorkSessionRunSnapshot snapshot,
            Map<String, Object> metadata) {
        if (snapshot == null) throw new IllegalArgumentException("WORK_SESSION_RUN_REQUIRED");
        Map<String, Object> manifest = new LinkedHashMap<>(snapshot.manifest());
        copy(metadata == null ? Map.of() : metadata, manifest, CONTEXT_METADATA_KEYS);
        return manifest;
    }

    public String manifestHash(Map<String, Object> manifest) {
        return CanonicalObjectHasher.sha256(manifest == null ? Map.of() : manifest);
    }

    public WorkSessionCheckpoint checkpoint(
            String checkpointType,
            Map<String, Object> payload,
            Instant now) {
        Map<String, Object> safe = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        return new WorkSessionCheckpoint(
                required(checkpointType, "checkpointType"),
                safe,
                CanonicalObjectHasher.sha256(safe),
                requiredTime(now));
    }

    public WorkSessionRunStatus terminalStatus(String value) {
        return WorkSessionRunStatus.terminal(value);
    }

    public boolean actorAllowed(
            WorkSessionRunSnapshot run,
            String actor,
            WorkSessionParticipantRole role,
            boolean write) {
        if (run == null) return false;
        String normalizedActor = required(actor, "actor");
        if (normalizedActor.equals(run.owner())) return true;
        if (role == null) return false;
        return !write || role.canWrite();
    }

    public WorkSessionRecoveryDecision recoveryDecision(WorkSessionRecoveryCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("WORK_SESSION_RECOVERY_CANDIDATE_REQUIRED");
        if (candidate.uncertainSideEffect()) {
            return new WorkSessionRecoveryDecision(
                    candidate.runId(),
                    candidate.projectId(),
                    candidate.attemptId(),
                    WorkSessionRunStatus.RECOVERY_REVIEW_REQUIRED,
                    "TOOL_EXECUTION_REPLAY_NOT_SAFE");
        }
        // Cancellation forbids replay, but cannot erase an uncertain write above.
        if (candidate.cancelRequested()) {
            return new WorkSessionRecoveryDecision(
                    candidate.runId(), candidate.projectId(), candidate.attemptId(),
                    WorkSessionRunStatus.CANCELED, "CANCELED_AFTER_LEASE_EXPIRY");
        }
        String reasonCode = !candidate.hasToolExecution()
                ? "LEASE_EXPIRED_BEFORE_TOOL_EXECUTION"
                : candidate.hasIncompleteToolExecution()
                        ? "LEASE_EXPIRED_DURING_READ_ONLY_TOOL"
                        : "LEASE_EXPIRED_AFTER_AUTHORITATIVE_TOOL_COMPLETION";
        return new WorkSessionRecoveryDecision(
                candidate.runId(),
                candidate.projectId(),
                candidate.attemptId(),
                WorkSessionRunStatus.RECOVERABLE,
                reasonCode);
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, List<String> keys) {
        for (String key : keys) target.put(key, source.get(key));
    }

    private String required(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }

    private Instant requiredTime(Instant value) {
        if (value == null) throw new IllegalArgumentException("WORK_SESSION_TIME_REQUIRED");
        return value;
    }
}
