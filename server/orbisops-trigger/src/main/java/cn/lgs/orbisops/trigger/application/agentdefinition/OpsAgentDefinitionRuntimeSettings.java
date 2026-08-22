package cn.lgs.orbisops.trigger.application.agentdefinition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Runtime configuration and mutable effective default identity for Agent Definition loading. */
@Component
public class OpsAgentDefinitionRuntimeSettings {

    private final String locations;
    private final String configuredDefaultAgentId;
    private final boolean jdbcEnabled;
    private volatile String effectiveDefaultAgentId;

    public OpsAgentDefinitionRuntimeSettings(
            @Value("${orbisops.agents.locations:classpath*:/agents/ops/*.yml,classpath*:/agents/ops/*.yaml}")
            String locations,
            @Value("${orbisops.agents.default-agent-id:" + OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID + "}")
            String configuredDefaultAgentId,
            @Value("${orbisops.agents.jdbc-enabled:true}")
            boolean jdbcEnabled) {
        this.locations = locations;
        this.configuredDefaultAgentId = StringUtils.hasText(configuredDefaultAgentId)
                ? configuredDefaultAgentId.trim()
                : OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID;
        this.effectiveDefaultAgentId = this.configuredDefaultAgentId;
        this.jdbcEnabled = jdbcEnabled;
    }

    public static OpsAgentDefinitionRuntimeSettings forTest(String locations,
                                                            String defaultAgentId,
                                                            boolean jdbcEnabled) {
        return new OpsAgentDefinitionRuntimeSettings(locations, defaultAgentId, jdbcEnabled);
    }

    public String locations() {
        return locations;
    }

    public String configuredDefaultAgentId() {
        return configuredDefaultAgentId;
    }

    public String effectiveDefaultAgentId() {
        return effectiveDefaultAgentId;
    }

    public void useEffectiveDefaultAgentId(String defaultAgentId) {
        if (!StringUtils.hasText(defaultAgentId)) {
            throw new IllegalArgumentException("AGENT_EFFECTIVE_DEFAULT_ID_REQUIRED");
        }
        this.effectiveDefaultAgentId = defaultAgentId.trim();
    }

    public boolean jdbcEnabled() {
        return jdbcEnabled;
    }
}
