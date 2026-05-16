package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;

import java.util.Locale;
import java.util.Map;

/** Product-level authority exposed to an Agent node; PROD_FULL is platform Landing only. */
enum OpsRuntimeAgentAuthority {
    OBSERVE_ONLY,
    PREPARE_CHANGE,
    PROD_FULL;

    static OpsRuntimeAgentAuthority resolve(OpsRuntimeResourceContext context) {
        if (context != null && context.getRequest() != null
                && Boolean.TRUE.equals(context.getRequest().getTrustedObserveOnly())) {
            return OBSERVE_ONLY;
        }
        AgentRunExecutionContext execution = context == null ? null : context.getExecutionContext();
        if (execution == null) {
            return OBSERVE_ONLY;
        }
        if (execution.stage() == AgentExecutionStage.LANDING) {
            return PROD_FULL;
        }
        String configured = configured(context);
        if (!configured.isBlank()) {
            return switch (configured) {
                case "OBSERVE_ONLY" -> OBSERVE_ONLY;
                case "PREPARE_CHANGE" -> PREPARE_CHANGE;
                case "PROD_FULL" -> throw new SecurityException("AGENT_PROD_FULL_AUTHORITY_FORBIDDEN");
                default -> throw new IllegalArgumentException("AGENT_AUTHORITY_UNSUPPORTED:" + configured);
            };
        }
        if (context != null && (Boolean.TRUE.equals(context.getRepairEnabled())
                || Boolean.TRUE.equals(context.getChangePackageEnabled()))) {
            return PREPARE_CHANGE;
        }
        return OBSERVE_ONLY;
    }

    boolean mayPrepareChange() {
        return this == PREPARE_CHANGE || this == PROD_FULL;
    }

    private static String configured(OpsRuntimeResourceContext context) {
        if (context == null) return "";
        OpsWorkflowNode node = context.getNode();
        if (node != null) {
            String value = config(node.getConfig(), "authority");
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String config(Map<String, Object> config, String key) {
        if (config == null) return "";
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
