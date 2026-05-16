package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionKind;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStyle;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import cn.lgs.orbisops.domain.worksession.runtime.service.AgentStageTransitionPolicy;
import cn.lgs.orbisops.domain.worksession.runtime.service.RuntimeCapabilityProfileResolver;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Creates trusted runtime authority from server trigger, execution style and project bindings. */
public final class OpsAgentRunExecutionContextFactory {

    public static final long RUNTIME_AUTHORITY_TTL_SECONDS = 1800L;
    public static final String AUTHORITATIVE_KEY = "_agentRunExecutionContext";
    public static final String VIEW_KEY = "agentRunExecutionContext";
    public static final String TRUSTED_APPROVED_PACKAGE_KEY = "_approvedLandingPackageSnapshot";
    private static final String RECOVERY_TRUST_KEY = "_agentRunRecoveryTrust";
    private static final Object RECOVERY_TRUST_TOKEN = new Object();

    private static final AgentStageTransitionPolicy STAGE_POLICY = new AgentStageTransitionPolicy();
    private static final RuntimeCapabilityProfileResolver PROFILE_RESOLVER = new RuntimeCapabilityProfileResolver();
    private static final OpsAgentSnapshotFactory SNAPSHOTS = new OpsAgentSnapshotFactory();
    private static final OpsAgentRunExecutionContextCodec CODEC = new OpsAgentRunExecutionContextCodec();

    public AgentRunExecutionContext bindServerContext(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition) {
        if (request == null) throw new IllegalArgumentException("AGENT_REQUEST_REQUIRED");
        if (definition == null) throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        if (request.getMetadata() == null) request.setMetadata(new java.util.LinkedHashMap<>());
        Map<String, Object> metadata = request.getMetadata();
        if (metadata.remove(RECOVERY_TRUST_KEY) == RECOVERY_TRUST_TOKEN) {
            AgentRunExecutionContext recovered = resolve(request);
            if (recovered == null) throw new SecurityException("RECOVERED_AUTHORITY_CONTEXT_REQUIRED");
            return renewRecovered(request, definition, recovered);
        }
        Object approved = metadata.get(TRUSTED_APPROVED_PACKAGE_KEY);
        if (approved instanceof ApprovedPackageSnapshot snapshot) {
            return bindLanding(request, definition, snapshot);
        }
        return bindPreApproval(request, definition);
    }

    public AgentRunExecutionContext bindPreApproval(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition) {
        if (request == null) throw new IllegalArgumentException("AGENT_REQUEST_REQUIRED");
        if (definition == null) throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        if (request.getMetadata() == null) request.setMetadata(new java.util.LinkedHashMap<>());
        request.getMetadata().remove(AUTHORITATIVE_KEY);
        request.getMetadata().remove(VIEW_KEY);
        request.getMetadata().remove(TRUSTED_APPROVED_PACKAGE_KEY);

        TriggerSource triggerSource = trustedTriggerSource(request.getMetadata());
        AgentExecutionStage stage = preApprovalStage(triggerSource);
        AgentExecutionStyle style = executionStyle(request, triggerSource);
        assertExecutionStyleCompatible(style, definition);
        AgentSnapshot snapshot = SNAPSHOTS.fromRequest(request, definition);
        Set<String> resources = resources(definition);
        AgentRunExecutionContext context = new AgentRunExecutionContext(
                required(request.getRunId(), "RUN_ID_REQUIRED"),
                firstText(request.getSessionId(), request.getRunId()),
                required(request.getProjectId(), "PROJECT_ID_REQUIRED"),
                triggerSource,
                style,
                stage,
                snapshot,
                Optional.empty(),
                PROFILE_RESOLVER.resolve(stage),
                resources,
                snapshot.toolBindingSnapshot(),
                Instant.now().plusSeconds(RUNTIME_AUTHORITY_TTL_SECONDS));
        store(request.getMetadata(), context);
        return context;
    }

    public AgentRunExecutionContext bindLanding(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            ApprovedPackageSnapshot approvedPackage) {
        if (request == null) throw new IllegalArgumentException("AGENT_REQUEST_REQUIRED");
        if (definition == null) throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        if (approvedPackage == null) throw new IllegalArgumentException("APPROVED_PACKAGE_REQUIRED");
        STAGE_POLICY.verifyNewLandingRun(true, true);
        if (!approvedPackage.projectId().equals(request.getProjectId())) {
            throw new SecurityException("LANDING_PROJECT_MISMATCH");
        }
        Instant now = Instant.now();
        if (approvedPackage.expired(now)) throw new SecurityException("LANDING_APPROVAL_EXPIRED");
        if (request.getMetadata() == null) request.setMetadata(new java.util.LinkedHashMap<>());
        AgentSnapshot snapshot = SNAPSHOTS.fromRequest(request, definition);
        AgentRunExecutionContext context = new AgentRunExecutionContext(
                required(request.getRunId(), "RUN_ID_REQUIRED"),
                firstText(request.getSessionId(), request.getRunId()),
                required(request.getProjectId(), "PROJECT_ID_REQUIRED"),
                TriggerSource.LANDING,
                AgentExecutionStyle.REACT,
                AgentExecutionStage.LANDING,
                snapshot,
                Optional.of(approvedPackage),
                PROFILE_RESOLVER.resolve(AgentExecutionStage.LANDING),
                resources(definition),
                snapshot.toolBindingSnapshot(),
                earlier(now.plusSeconds(RUNTIME_AUTHORITY_TTL_SECONDS), approvedPackage.approvalExpiresAt()));
        request.getMetadata().put(TRUSTED_APPROVED_PACKAGE_KEY, approvedPackage);
        store(request.getMetadata(), context);
        return context;
    }

    public AgentRunExecutionContext resolve(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return null;
        Object value = request.getMetadata().get(AUTHORITATIVE_KEY);
        return value instanceof AgentRunExecutionContext context ? context : null;
    }

    /** Called only by the durable repository mapper after loading a server-owned snapshot. */
    void restorePersistedAuthority(Map<String, Object> metadata) {
        if (metadata == null) return;
        AgentRunExecutionContext context = CODEC.decode(metadata.get(AUTHORITATIVE_KEY));
        if (context == null) return;
        metadata.put(AUTHORITATIVE_KEY, context);
        metadata.put(VIEW_KEY, view(context));
        context.approvedPackage().ifPresent(snapshot -> metadata.put(TRUSTED_APPROVED_PACKAGE_KEY, snapshot));
        metadata.put(RECOVERY_TRUST_KEY, RECOVERY_TRUST_TOKEN);
    }

    Map<String, Object> persistableMetadata(Map<String, Object> source) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        if (source != null) result.putAll(source);
        result.remove(RECOVERY_TRUST_KEY);
        Object authority = result.get(AUTHORITATIVE_KEY);
        if (authority instanceof AgentRunExecutionContext context) {
            result.put(AUTHORITATIVE_KEY, CODEC.encode(context));
            context.approvedPackage().ifPresent(snapshot -> result.put(
                    TRUSTED_APPROVED_PACKAGE_KEY, CODEC.encodeApprovedPackage(snapshot)));
        } else if (result.get(TRUSTED_APPROVED_PACKAGE_KEY) instanceof ApprovedPackageSnapshot snapshot) {
            result.put(TRUSTED_APPROVED_PACKAGE_KEY, CODEC.encodeApprovedPackage(snapshot));
        }
        return result;
    }

    private AgentRunExecutionContext renewRecovered(
            OpsAgentChatRequest request,
            OpsAgentDefinition definition,
            AgentRunExecutionContext recovered) {
        if (!recovered.runId().equals(required(request.getRunId(), "RUN_ID_REQUIRED"))) {
            throw new SecurityException("RECOVERED_RUN_ID_MISMATCH");
        }
        if (!recovered.projectId().equals(required(request.getProjectId(), "PROJECT_ID_REQUIRED"))) {
            throw new SecurityException("RECOVERED_PROJECT_ID_MISMATCH");
        }
        Instant now = Instant.now();
        if (recovered.expired(now)) throw new SecurityException("RECOVERED_AUTHORITY_CONTEXT_EXPIRED");
        AgentSnapshot current = SNAPSHOTS.fromRequest(request, definition);
        assertSameAgentSnapshot(recovered.agentSnapshot(), current);
        Instant recoveredDeadline = recovered.deadline();
        if (recovered.approvedPackage().isPresent()) {
            ApprovedPackageSnapshot snapshot = recovered.approvedPackage().orElseThrow();
            if (recovered.stage() != AgentExecutionStage.LANDING) {
                throw new SecurityException("RECOVERED_APPROVED_PACKAGE_STAGE_MISMATCH");
            }
            if (snapshot.expired(now)) throw new SecurityException("LANDING_APPROVAL_EXPIRED");
            request.getMetadata().put(TRUSTED_APPROVED_PACKAGE_KEY, snapshot);
            recoveredDeadline = earlier(recoveredDeadline, snapshot.approvalExpiresAt());
        }
        AgentRunExecutionContext renewed = new AgentRunExecutionContext(
                recovered.runId(),
                recovered.workSessionId(),
                recovered.projectId(),
                recovered.triggerSource(),
                recovered.executionStyle(),
                recovered.stage(),
                recovered.agentSnapshot(),
                recovered.approvedPackage(),
                recovered.capabilityProfile(),
                recovered.allowedResourceIds(),
                recovered.allowedToolIds(),
                recoveredDeadline);
        store(request.getMetadata(), renewed);
        return renewed;
    }

    private void assertSameAgentSnapshot(AgentSnapshot approved, AgentSnapshot current) {
        if (approved == null || current == null || !approved.equals(current)) {
            throw new SecurityException("RECOVERED_AGENT_SNAPSHOT_MISMATCH");
        }
    }

    private AgentExecutionStyle executionStyle(OpsAgentChatRequest request, TriggerSource triggerSource) {
        if (triggerSource == TriggerSource.LANDING) return AgentExecutionStyle.REACT;
        String mode = request == null ? "" : text(request.getMode()).toUpperCase();
        return "WORKFLOW".equals(mode) ? AgentExecutionStyle.WORKFLOW : AgentExecutionStyle.REACT;
    }

    private void assertExecutionStyleCompatible(
            AgentExecutionStyle style,
            OpsAgentDefinition definition) {
        if (style != AgentExecutionStyle.REACT || definition == null) return;
        if (AgentDefinitionKind.parse(definition.getDefinitionKind()) == AgentDefinitionKind.SPECIALIZED_WORKFLOW) {
            throw new SecurityException("DEFAULT_REACT_AGENT_REQUIRED");
        }
    }

    private AgentExecutionStage preApprovalStage(TriggerSource triggerSource) {
        TriggerSource source = triggerSource == null ? TriggerSource.CHAT : triggerSource;
        return switch (source) {
            case INSPECTION, SCHEDULE, ALERT -> AgentExecutionStage.INVESTIGATE;
            case LANDING -> throw new SecurityException("LANDING_REQUIRES_APPROVED_PACKAGE");
            case CHAT, API, WORKFLOW -> AgentExecutionStage.PREPARE;
        };
    }

    private Set<String> resources(OpsAgentDefinition definition) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.addAll(copy(definition.getExecutionTargetIds()));
        result.addAll(copy(definition.getMcpIds()));
        return Set.copyOf(result);
    }

    private TriggerSource trustedTriggerSource(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("_trustedTriggerSource");
        return value instanceof TriggerSource source ? source : TriggerSource.CHAT;
    }

    private void store(Map<String, Object> metadata, AgentRunExecutionContext context) {
        metadata.put(AUTHORITATIVE_KEY, context);
        metadata.put(VIEW_KEY, view(context));
    }

    private Map<String, Object> view(AgentRunExecutionContext context) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("runId", context.runId());
        view.put("workSessionId", context.workSessionId());
        view.put("projectId", context.projectId());
        view.put("triggerSource", context.triggerSource().name());
        view.put("executionStyle", context.executionStyle().name());
        view.put("stage", context.stage().name());
        view.put("capabilityProfile", context.capabilityProfile().name());
        view.put("allowedResourceIds", context.allowedResourceIds());
        view.put("allowedToolIds", context.allowedToolIds());
        view.put("deadline", context.deadline().toString());
        view.put("approvedPackageBound", context.approvedPackage().isPresent());
        context.approvedPackage().ifPresent(snapshot -> {
            view.put("packageId", snapshot.packageId());
            view.put("packageVersion", snapshot.packageVersion());
            view.put("packageHash", snapshot.packageHash());
            view.put("targetEnvironment", snapshot.targetEnvironment());
        });
        return java.util.Collections.unmodifiableMap(view);
    }

    private Set<String> copy(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().map(this::text)
                .filter(StringUtils::hasText).forEach(result::add);
        return Set.copyOf(result);
    }

    private Instant earlier(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isBefore(second) ? first : second;
    }

    private String required(Object value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private String firstText(Object... values) {
        if (values == null) return "";
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
