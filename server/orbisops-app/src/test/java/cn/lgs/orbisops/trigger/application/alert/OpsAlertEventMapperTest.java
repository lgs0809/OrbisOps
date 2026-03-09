package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertEventDraft;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerEvent;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsAlertEventMapperTest {

    private final OpsAlertEventMapper mapper = new OpsAlertEventMapper();

    @Test
    void mapsRuleAndAlertPayloadIntoTypedDraft() {
        OpsAlertTriggerRule rule = OpsAlertTriggerRule.builder()
                .id(7L)
                .ruleName("Payment Alert")
                .projectId("project-1")
                .notificationTarget("ops")
                .build();

        AlertEventDraft draft = mapper.draft(
                rule, "ALERTMANAGER", "TRIGGERED", "dispatch-1", "fp-1",
                "HighErrorRate", "critical", "payment", "run-1", "PENDING", "", "",
                Map.of("env", "prod"), Map.of("summary", "slow"), Map.of("status", "firing"));

        assertEquals(7L, draft.ruleId());
        assertEquals("project-1", draft.projectId());
        assertEquals("ops", draft.receiver());
        assertEquals("dispatch-1", draft.dispatchKey());
        assertEquals("prod", draft.labels().get("env"));
    }

    @Test
    void mapsTypedSnapshotBackToCompatibilityDtoJson() {
        AlertEventSnapshot snapshot = new AlertEventSnapshot(
                11L, 7L, "Payment Alert", "project-1", "ALERTMANAGER", "TRIGGERED",
                "dispatch-1", "fp-1", "HighErrorRate", "critical", "payment", "ops",
                "run-1", "SUCCEEDED", "done", "completed", "", Map.of("env", "prod"),
                Map.of("summary", "slow"), Map.of("status", "firing"), "created");

        OpsAlertTriggerEvent dto = mapper.view(snapshot);

        assertEquals(11L, dto.getId());
        assertEquals("dispatch-1", dto.getDedupKey());
        assertEquals("{\"env\":\"prod\"}", dto.getLabelsJson());
        assertEquals("{\"summary\":\"slow\"}", dto.getAnnotationsJson());
        assertEquals("{\"status\":\"firing\"}", dto.getPayloadJson());
    }
}
