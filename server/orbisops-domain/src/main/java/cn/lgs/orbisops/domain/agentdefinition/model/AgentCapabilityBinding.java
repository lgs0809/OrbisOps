package cn.lgs.orbisops.domain.agentdefinition.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Typed persisted capability binding for one Agent Definition owner. */
public record AgentCapabilityBinding(
        Long id,
        String agentId,
        int version,
        AgentDefinitionLifecycle lifecycle,
        String projectId,
        AgentCapabilityOwnerType ownerType,
        String nodeId,
        AgentCapabilityType capabilityType,
        String capabilityId,
        AgentCapabilityScope capabilityScope,
        Map<String, Object> bindConfig,
        String createBy,
        Instant createTime) {

    public AgentCapabilityBinding {
        agentId = required(agentId, "AGENT_CAPABILITY_AGENT_ID_REQUIRED");
        if (version < 0) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_VERSION_INVALID");
        }
        lifecycle = Objects.requireNonNull(lifecycle, "AGENT_CAPABILITY_LIFECYCLE_REQUIRED");
        projectId = optional(projectId);
        ownerType = Objects.requireNonNull(ownerType, "AGENT_CAPABILITY_OWNER_TYPE_REQUIRED");
        nodeId = optional(nodeId);
        capabilityType = Objects.requireNonNull(capabilityType, "AGENT_CAPABILITY_TYPE_REQUIRED");
        capabilityId = required(capabilityId, "AGENT_CAPABILITY_ID_REQUIRED");
        capabilityScope = Objects.requireNonNull(capabilityScope, "AGENT_CAPABILITY_SCOPE_REQUIRED");
        bindConfig = bindConfig == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(bindConfig));
        createBy = nullable(createBy);
    }

    public static AgentCapabilityBinding create(String agentId,
                                                int version,
                                                AgentDefinitionLifecycle lifecycle,
                                                String projectId,
                                                AgentCapabilityOwnerType ownerType,
                                                String nodeId,
                                                AgentCapabilityType capabilityType,
                                                String capabilityId,
                                                Map<String, Object> bindConfig) {
        return new AgentCapabilityBinding(
                null,
                agentId,
                version,
                lifecycle,
                projectId,
                ownerType,
                nodeId,
                capabilityType,
                capabilityId,
                capabilityType.defaultScope(),
                bindConfig,
                "system",
                null);
    }

    private static String required(String value, String error) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullable(String value) {
        return value == null ? null : value.trim();
    }
}
