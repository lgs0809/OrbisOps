package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentNodeDefinitionPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentNodeStatusCapabilityPolicyTest {
    private final AgentNodeDefinitionPolicy policy = new AgentNodeDefinitionPolicy();

    @Test
    void explicitStatusLookupIsSufficientWithoutUnrelatedMcpOrRag() {
        assertDoesNotThrow(() -> policy.validate(node("AGENT", "REACT", Map.of("changePackageStatusEnabled", true))));
    }

    @Test
    void MissingDisabledAndStringCapabilitiesCannotBypassToolRequirement() {
        for (Map<String, Object> config : List.<Map<String, Object>>of(Map.of(),
                Map.of("changePackageStatusEnabled", false), Map.of("changePackageStatusEnabled", "true"))) {
            assertThrows(IllegalArgumentException.class, () -> policy.validate(node("AGENT", "REACT", config)));
        }
    }

    @Test
    void StatusCapabilityCannotBeDeclaredOnUnsupportedNodes() {
        for (String type : List.of("ROUTER", "CHAT", "START")) {
            assertThrows(IllegalArgumentException.class, () -> policy.validate(node(type, "", Map.of("changePackageStatusEnabled", true))));
        }
        assertThrows(IllegalArgumentException.class, () -> policy.validate(node("AGENT", "LLM", Map.of("changePackageStatusEnabled", true))));
    }

    private AgentGraphDefinition.Node node(String type, String mode, Map<String, Object> config) {
        return new AgentGraphDefinition.Node("resolver", type, mode, "request-resolver", false, false, List.of(), 0, config);
    }
}
