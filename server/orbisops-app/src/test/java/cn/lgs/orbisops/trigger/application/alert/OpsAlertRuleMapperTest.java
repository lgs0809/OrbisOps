package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsAlertRuleMapperTest {

    private final OpsAlertRuleMapper mapper = new OpsAlertRuleMapper();

    @Test
    void mapsDtoJsonIntoTypedCandidate() {
        OpsAlertTriggerRule dto = OpsAlertTriggerRule.builder()
                .id(7L)
                .ruleName("Payment Alert")
                .projectId("project-1")
                .agentDefinitionId("agent-1")
                .matchLabelsJson("{\"env\":\"prod\",\"service\":\"~pay.*\"}")
                .build();

        AlertRuleCandidate candidate = mapper.candidate(dto);

        assertEquals(7L, candidate.id());
        assertEquals(Map.of("env", "prod", "service", "~pay.*"), candidate.matchLabels());
    }

    @Test
    void rejectsMalformedLabelsJson() {
        OpsAlertTriggerRule dto = OpsAlertTriggerRule.builder()
                .ruleName("Payment Alert")
                .matchLabelsJson("{")
                .build();

        assertEquals("ALERT_RULE_MATCH_LABELS_JSON_INVALID",
                assertThrows(IllegalArgumentException.class, () -> mapper.candidate(dto)).getMessage());
    }

    @Test
    void mapsTypedDefinitionBackToCompatibilityDto() {
        AlertRuleDefinition definition = new AlertRuleDefinition(
                7L, "Payment Alert", 1, "ALERTMANAGER", "High.*", "critical", "payment",
                Map.of("env", "prod"), "channel-1", "ops", "secret", "project-1", "agent-1",
                "LATEST_PUBLISHED", 4, "agent-v4", "question", 30, "5m", true, true,
                5, 120, 20, 300, "created", "updated");

        OpsAlertTriggerRule dto = mapper.view(definition);

        assertEquals(7L, dto.getId());
        assertEquals("{\"env\":\"prod\"}", dto.getMatchLabelsJson());
        assertEquals("agent-v4", dto.getAgentDefinitionHash());
        assertEquals(300, dto.getDedupWindowSeconds());
    }
}
