package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAlertRuleCatalogTest {

    @Test
    void returnsOnlyActiveRulesForRequestedSourceAndIndexesNonNullIds() {
        AlertRuleManagementApplicationService alertRules = mock(AlertRuleManagementApplicationService.class);
        OpsAlertRuleMapper mapper = mock(OpsAlertRuleMapper.class);
        when(alertRules.list()).thenReturn(List.of());
        when(mapper.views(anyList())).thenReturn(List.of(
                OpsAlertTriggerRule.builder().id(1L).status(1).sourceType(" alertmanager ").build(),
                OpsAlertTriggerRule.builder().id(2L).status(0).sourceType("ALERTMANAGER").build(),
                OpsAlertTriggerRule.builder().id(3L).status(1).sourceType("WEBHOOK").build(),
                OpsAlertTriggerRule.builder().status(1).sourceType("ALERTMANAGER").build()));

        OpsAlertRuleCatalog catalog = new OpsAlertRuleCatalog(alertRules, mapper);

        List<OpsAlertTriggerRule> active = catalog.activeForSource("ALERTMANAGER");
        Map<Long, OpsAlertTriggerRule> indexed = catalog.activeByIdForSource("ALERTMANAGER");

        assertEquals(2, active.size());
        assertEquals(List.of(1L), indexed.keySet().stream().toList());
        assertTrue(indexed.keySet().stream().noneMatch(java.util.Objects::isNull));
    }
}
