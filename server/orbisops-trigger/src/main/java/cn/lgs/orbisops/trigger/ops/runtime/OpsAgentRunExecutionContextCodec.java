package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStyle;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Canonical JSON-safe codec for durable trusted Agent authority snapshots. */
public final class OpsAgentRunExecutionContextCodec {

    /** v5 removes Intent disposition and per-tool Landing approval ACLs. */
    public static final int CURRENT_SCHEMA_VERSION = 5;

    public Map<String, Object> encode(AgentRunExecutionContext context) {
        if (context == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        data.put("runId", context.runId());
        data.put("workSessionId", context.workSessionId());
        data.put("projectId", context.projectId());
        data.put("triggerSource", context.triggerSource().name());
        data.put("executionStyle", context.executionStyle().name());
        data.put("stage", context.stage().name());
        data.put("agentSnapshot", encodeAgent(context.agentSnapshot()));
        data.put("approvedPackage", context.approvedPackage()
                .map(this::encodeApprovedPackage)
                .orElse(Map.of()));
        data.put("capabilityProfile", context.capabilityProfile().name());
        data.put("allowedResourceIds", listCopy(context.allowedResourceIds()));
        data.put("allowedToolIds", listCopy(context.allowedToolIds()));
        data.put("deadline", context.deadline().toString());
        return Map.copyOf(data);
    }

    public AgentRunExecutionContext decode(Object value) {
        if (value instanceof AgentRunExecutionContext context) return context;
        Map<String, Object> data = objectMap(value);
        if (data.isEmpty()) return null;
        int schemaVersion = schemaVersion(data);
        AgentSnapshot agent = decodeAgent(data.get("agentSnapshot"));
        if (agent == null) return null;
        Instant deadline = instant(data.get("deadline"));
        AgentExecutionStage stage = enumValue(
                AgentExecutionStage.class, data.get("stage"), AgentExecutionStage.INVESTIGATE);
        ApprovedPackageSnapshot approved = decodeApprovedPackage(
                data.get("approvedPackage"), deadline, schemaVersion);
        return new AgentRunExecutionContext(
                required(data.get("runId"), "RUN_ID_REQUIRED"),
                required(data.get("workSessionId"), "WORK_SESSION_ID_REQUIRED"),
                required(data.get("projectId"), "PROJECT_ID_REQUIRED"),
                enumValue(TriggerSource.class, data.get("triggerSource"), TriggerSource.API),
                executionStyle(data, stage),
                stage,
                agent,
                Optional.ofNullable(approved),
                capabilityProfile(data.get("capabilityProfile"), stage),
                contextStringSet(data, "allowedResourceIds", agent),
                contextStringSet(data, "allowedToolIds", agent),
                deadline);
    }

    public Map<String, Object> encodeAgent(AgentSnapshot snapshot) {
        if (snapshot == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentId", snapshot.agentId());
        data.put("agentVersion", snapshot.agentVersion());
        data.put("definitionHash", snapshot.definitionHash());
        data.put("systemPromptHash", snapshot.systemPromptHash());
        data.put("modelProfile", snapshot.modelProfile());
        data.put("toolBindingSnapshot", listCopy(snapshot.toolBindingSnapshot()));
        data.put("mcpBindingSnapshot", listCopy(snapshot.mcpBindingSnapshot()));
        data.put("skillBindingSnapshot", listCopy(snapshot.skillBindingSnapshot()));
        data.put("knowledgeBindingSnapshot", listCopy(snapshot.knowledgeBindingSnapshot()));
        return Map.copyOf(data);
    }

    public AgentSnapshot decodeAgent(Object value) {
        if (value instanceof AgentSnapshot snapshot) return snapshot;
        Map<String, Object> data = objectMap(value);
        if (data.isEmpty()) return null;
        return new AgentSnapshot(
                text(data.get("agentId")),
                integer(data.get("agentVersion")),
                text(data.get("definitionHash")),
                text(data.get("systemPromptHash")),
                text(data.get("modelProfile")),
                agentStringSet(data, "toolBindingSnapshot"),
                agentStringSet(data, "mcpBindingSnapshot"),
                agentStringSet(data, "skillBindingSnapshot"),
                agentStringSet(data, "knowledgeBindingSnapshot"));
    }

    public Map<String, Object> encodeApprovedPackage(ApprovedPackageSnapshot snapshot) {
        if (snapshot == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        data.put("packageId", snapshot.packageId());
        data.put("packageVersion", snapshot.packageVersion());
        data.put("packageHash", snapshot.packageHash());
        data.put("projectId", snapshot.projectId());
        data.put("targetEnvironment", snapshot.targetEnvironment());
        data.put("artifactDigest", snapshot.artifactDigest());
        data.put("approvalExpiresAt", snapshot.approvalExpiresAt().toString());
        return Map.copyOf(data);
    }

    public ApprovedPackageSnapshot decodeApprovedPackage(Object value) {
        return decodeApprovedPackage(value, Instant.EPOCH.plusSeconds(1), CURRENT_SCHEMA_VERSION);
    }

    private ApprovedPackageSnapshot decodeApprovedPackage(
            Object value,
            Instant legacyExpiry,
            int parentSchemaVersion) {
        if (value instanceof ApprovedPackageSnapshot snapshot) return snapshot;
        Map<String, Object> data = objectMap(value);
        if (data.isEmpty()) return null;
        int schemaVersion = data.containsKey("schemaVersion")
                ? schemaVersion(data)
                : parentSchemaVersion;
        Instant approvalExpiresAt = approvalExpiry(data, legacyExpiry, schemaVersion);
        return new ApprovedPackageSnapshot(
                required(data.get("packageId"), "PACKAGE_ID_REQUIRED"),
                positiveLong(data.get("packageVersion"), "APPROVED_PACKAGE_VERSION_INVALID"),
                required(data.get("packageHash"), "PACKAGE_HASH_REQUIRED"),
                required(data.get("projectId"), "PROJECT_ID_REQUIRED"),
                required(data.get("targetEnvironment"), "TARGET_ENVIRONMENT_REQUIRED"),
                text(data.get("artifactDigest")),
                approvalExpiresAt);
    }

    private AgentExecutionStyle executionStyle(
            Map<String, Object> data,
            AgentExecutionStage stage) {
        String explicit = text(data.get("executionStyle"));
        if (!explicit.isBlank()) {
            return enumValue(AgentExecutionStyle.class, explicit, AgentExecutionStyle.REACT);
        }
        if (stage == AgentExecutionStage.LANDING) return AgentExecutionStyle.REACT;
        String legacy = text(data.get("disposition")).toUpperCase();
        return "EXPLICIT_AGENT".equals(legacy)
                ? AgentExecutionStyle.WORKFLOW
                : AgentExecutionStyle.REACT;
    }

    private CapabilityProfile defaultProfile(AgentExecutionStage stage) {
        return switch (stage) {
            case LANDING -> CapabilityProfile.PROD_FULL;
            case PREPARE -> CapabilityProfile.TEST_FULL;
            case INVESTIGATE -> CapabilityProfile.PROD_DIAGNOSTIC;
        };
    }

    private CapabilityProfile capabilityProfile(Object value, AgentExecutionStage stage) {
        String normalized = text(value).toUpperCase();
        if ("PROD_LANDING".equals(normalized)) {
            // Durable compatibility for authority snapshots written before Phase 652
            // renamed the Landing capability to its actual semantics: PROD_FULL.
            return CapabilityProfile.PROD_FULL;
        }
        return enumValue(CapabilityProfile.class, value, defaultProfile(stage));
    }

    private Instant approvalExpiry(
            Map<String, Object> data,
            Instant legacyExpiry,
            int schemaVersion) {
        String encoded = text(data.get("approvalExpiresAt"));
        if (!encoded.isBlank()) return instant(encoded);
        if (legacyExpiry != null && legacyExpiry.isAfter(Instant.EPOCH)) {
            // Old schemas did not always persist approval expiry. Reusing the already-frozen
            // run deadline migrates safely without extending authority.
            return legacyExpiry;
        }
        throw new SecurityException("APPROVED_PACKAGE_EXPIRY_REQUIRED");
    }

    private int schemaVersion(Map<String, Object> data) {
        Object raw = data.get("schemaVersion");
        if (raw == null || text(raw).isBlank()) return 1;
        long parsed = positiveLong(raw, "INVALID_AUTHORITY_SCHEMA_VERSION");
        if (parsed > CURRENT_SCHEMA_VERSION) {
            throw new SecurityException("UNSUPPORTED_AUTHORITY_SCHEMA_VERSION:" + parsed);
        }
        return Math.toIntExact(parsed);
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private java.util.List<String> listCopy(Set<String> values) {
        return values == null || values.isEmpty()
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(values);
    }

    private Set<String> agentStringSet(Map<String, Object> data, String key) {
        return agentStringSet(data, key, new LinkedHashSet<>());
    }

    private Set<String> agentStringSet(
            Map<String, Object> data,
            String key,
            Set<String> resolving) {
        if (!resolving.add(key)) throw new SecurityException("AUTHORITY_JSON_REF_CYCLE:" + key);
        Object value = data.get(key);
        String ref = jsonRef(value);
        if (!ref.isBlank()) {
            String target = refField(ref);
            if (!Set.of(
                    "toolBindingSnapshot",
                    "mcpBindingSnapshot",
                    "skillBindingSnapshot",
                    "knowledgeBindingSnapshot").contains(target)) {
                throw new SecurityException("AUTHORITY_JSON_REF_FORBIDDEN:" + ref);
            }
            return agentStringSet(data, target, resolving);
        }
        return stringSet(value);
    }

    private Set<String> contextStringSet(
            Map<String, Object> data,
            String key,
            AgentSnapshot agent) {
        Object value = data.get(key);
        String ref = jsonRef(value);
        if (ref.isBlank()) return stringSet(value);
        if (ref.endsWith(".agentSnapshot.toolBindingSnapshot")) return agent.toolBindingSnapshot();
        if (ref.endsWith(".agentSnapshot.mcpBindingSnapshot")) return agent.mcpBindingSnapshot();
        if (ref.endsWith(".agentSnapshot.skillBindingSnapshot")) return agent.skillBindingSnapshot();
        if (ref.endsWith(".agentSnapshot.knowledgeBindingSnapshot")) return agent.knowledgeBindingSnapshot();
        throw new SecurityException("AUTHORITY_JSON_REF_FORBIDDEN:" + ref);
    }

    private String jsonRef(Object value) {
        if (!(value instanceof Map<?, ?> map)) return "";
        return text(map.get("$ref"));
    }

    private String refField(String ref) {
        String normalized = text(ref);
        int index = normalized.lastIndexOf('.');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private Set<String> stringSet(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof Map<?, ?> map && map.containsKey("$ref")) {
            throw new SecurityException("AUTHORITY_JSON_REF_UNRESOLVED:" + text(map.get("$ref")));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) add(result, item);
        } else if (value instanceof Object[] array) {
            for (Object item : array) add(result, item);
        } else if (value != null) {
            for (String item : String.valueOf(value).split(",")) add(result, item);
        }
        return Set.copyOf(result);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, Object value, E fallback) {
        String normalized = text(value);
        if (normalized.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, normalized.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw new SecurityException("INVALID_AUTHORITY_ENUM_" + type.getSimpleName());
        }
    }

    private Instant instant(Object value) {
        String normalized = required(value, "DEADLINE_REQUIRED");
        try {
            return Instant.parse(normalized);
        } catch (Exception error) {
            throw new SecurityException("INVALID_AUTHORITY_DEADLINE", error);
        }
    }

    private int integer(Object value) {
        return Math.toIntExact(longValue(value));
    }

    private long positiveLong(Object value, String code) {
        long result = longValue(value);
        if (result <= 0L) throw new SecurityException(code);
        return result;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (Exception error) {
            throw new SecurityException("INVALID_AUTHORITY_NUMBER", error);
        }
    }

    private void add(Set<String> values, Object value) {
        String normalized = text(value);
        if (!normalized.isBlank()) values.add(normalized);
    }

    private String required(Object value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new SecurityException(code);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
