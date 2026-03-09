package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.application.alert.AlertRuleAgentResolverPort;
import cn.lgs.orbisops.domain.alert.model.AlertAgentResolution;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.stereotype.Component;

@Component
public class OpsAlertRuleAgentResolverAdapter implements AlertRuleAgentResolverPort {

    private final OpsAgentDefinitionQueryGateway definitions;

    public OpsAlertRuleAgentResolverAdapter(OpsAgentDefinitionQueryGateway definitions) {
        this.definitions = definitions;
    }

    @Override
    public AlertAgentResolution resolve(AlertRuleCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("ALERT_RULE_REQUIRED");
        String mode = text(candidate.agentBindingMode()).isBlank()
                ? "LATEST_PUBLISHED"
                : candidate.agentBindingMode().trim().toUpperCase();
        Integer version = "PINNED_VERSION".equals(mode) ? candidate.agentVersion() : null;
        OpsAgentDefinition resolved = definitions.resolveForProject(
                text(candidate.agentDefinitionId()),
                version,
                false,
                text(candidate.projectId()));
        if (resolved == null || resolved.getVersion() == null || resolved.getVersion() <= 0
                || text(resolved.getDefinitionHash()).isBlank()) {
            throw new IllegalStateException("ALERT_AGENT_VERSION_INCOMPLETE");
        }
        return new AlertAgentResolution(resolved.getVersion(), resolved.getDefinitionHash());
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
