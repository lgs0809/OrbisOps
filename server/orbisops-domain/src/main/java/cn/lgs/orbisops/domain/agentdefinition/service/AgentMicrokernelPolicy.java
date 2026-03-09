package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.OpsBuiltinSubAgentRole;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Determines whether an Agent Definition contains the platform main assistant runtime. */
public final class AgentMicrokernelPolicy {

    private static final Set<String> REQUIRED_ROLES =
            Set.of(OpsBuiltinSubAgentRole.MAIN_ASSISTANT.name());

    public boolean supportsBuiltInMicrokernel(Collection<String> roles) {
        Set<String> normalized = roles == null
                ? Set.of()
                : roles.stream()
                .filter(java.util.Objects::nonNull)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        return normalized.containsAll(REQUIRED_ROLES);
    }

    public Set<String> requiredRoles() {
        return REQUIRED_ROLES;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
