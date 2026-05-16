package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Active trigger-rule query boundary. */
public final class OpsAlertRuleCatalog {

    private final AlertRuleManagementApplicationService alertRules;
    private final OpsAlertRuleMapper mapper;

    public OpsAlertRuleCatalog(
            AlertRuleManagementApplicationService alertRules,
            OpsAlertRuleMapper mapper) {
        this.alertRules = alertRules;
        this.mapper = mapper;
    }

    public List<OpsAlertTriggerRule> activeForSource(String sourceType) {
        return mapper.views(alertRules.list()).stream()
                .filter(this::active)
                .filter(rule -> normalized(sourceType).equalsIgnoreCase(normalized(rule.getSourceType())))
                .toList();
    }

    public Map<Long, OpsAlertTriggerRule> activeByIdForSource(String sourceType) {
        Map<Long, OpsAlertTriggerRule> result = new LinkedHashMap<>();
        activeForSource(sourceType).forEach(rule -> {
            if (rule.getId() != null) {
                result.put(rule.getId(), rule);
            }
        });
        return Map.copyOf(result);
    }

    private boolean active(OpsAlertTriggerRule rule) {
        return rule != null && Integer.valueOf(1).equals(rule.getStatus());
    }

    private String normalized(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
