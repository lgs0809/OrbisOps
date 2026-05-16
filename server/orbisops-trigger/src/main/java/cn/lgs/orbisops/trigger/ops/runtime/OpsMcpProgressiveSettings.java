package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Immutable progressive MCP exposure settings. */
@Component
public final class OpsMcpProgressiveSettings {

    private final boolean enforceProjectManaged;
    private final boolean disclosureEnabled;

    public OpsMcpProgressiveSettings(
            @Value("${orbisops.progressive-mcp.enforce-project-managed:true}")
            boolean enforceProjectManaged,
            @Value("${orbisops.progressive-mcp.disclosure.enabled:true}")
            boolean disclosureEnabled) {
        this.enforceProjectManaged = enforceProjectManaged;
        this.disclosureEnabled = disclosureEnabled;
    }

    public boolean enforceProjectManaged() {
        return enforceProjectManaged;
    }

    public boolean disclosureEnabled() {
        return disclosureEnabled;
    }

    public static OpsMcpProgressiveSettings forTest(
            boolean enforceProjectManaged,
            boolean disclosureEnabled) {
        return new OpsMcpProgressiveSettings(enforceProjectManaged, disclosureEnabled);
    }
}
