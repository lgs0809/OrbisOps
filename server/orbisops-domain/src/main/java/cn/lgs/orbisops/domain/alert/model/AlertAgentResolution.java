package cn.lgs.orbisops.domain.alert.model;

public record AlertAgentResolution(Integer version, String definitionHash) {

    public AlertAgentResolution {
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("ALERT_AGENT_VERSION_REQUIRED");
        }
        definitionHash = definitionHash == null ? "" : definitionHash.trim();
        if (definitionHash.isBlank()) {
            throw new IllegalArgumentException("ALERT_AGENT_DEFINITION_HASH_REQUIRED");
        }
    }
}
