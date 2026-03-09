package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Owns AgentScope flow mode, output key and runtime budget calculations. */
final class OpsAgentScopeFlowPolicy {

    String mode(OpsAgentDefinition definition) {
        String configured = firstText(definition.getAgentScopeMode(), "");
        if (!StringUtils.hasText(configured)
                && StringUtils.hasText(definition.getEngine())
                && definition.getEngine().toUpperCase(Locale.ROOT).contains("PARALLEL")) {
            configured = "parallel";
        }
        return "parallel".equalsIgnoreCase(configured) ? "PARALLEL" : "SEQUENTIAL";
    }

    int maxConcurrency(OpsAgentDefinition definition, int agentCount) {
        int configured = Optional.ofNullable(definition.getAgentScopeMaxConcurrency())
                .orElse(agentCount);
        return Math.max(1, Math.min(configured, Math.max(1, agentCount)));
    }

    String outputKey(String flowMode, List<OpsAgentScopeConfig> configs) {
        if ("PARALLEL".equals(flowMode)) return "parallel_results";
        return configs.stream()
                .reduce((first, second) -> second)
                .map(OpsAgentScopeConfig::getOutputKey)
                .filter(StringUtils::hasText)
                .orElse("agent_" + (configs.size() - 1));
    }

    int recursionLimit(int maxToolRounds) {
        return Math.max(8, maxToolRounds * 2 + 8);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }
}
