package cn.lgs.orbisops.domain.alert;

import cn.lgs.orbisops.domain.alert.model.AlertAgentResolution;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.domain.alert.service.AlertRuleDefinitionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleDefinitionPolicyTest {

    private final AlertRuleDefinitionPolicy policy = new AlertRuleDefinitionPolicy();
    private final AlertAgentResolution resolution = new AlertAgentResolution(4, "agent-v4");

    @Test
    void requiresRuleProjectAndAgent() {
        IllegalArgumentException name = assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(candidate(null, "project", "agent", null, null), resolution, ""));
        IllegalArgumentException project = assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(candidate("rule", null, "agent", null, null), resolution, ""));
        IllegalArgumentException agent = assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(candidate("rule", "project", null, null, null), resolution, ""));

        assertEquals("触发规则名称不能为空", name.getMessage());
        assertEquals("告警触发规则必须选择 projectId", project.getMessage());
        assertEquals("告警触发规则必须选择执行 Agent", agent.getMessage());
    }

    @Test
    void resolvesLatestPublishedAndKeepsResolvedIdentity() {
        AlertRuleDefinition normalized = policy.normalize(
                candidate(" rule ", " project ", " agent ", null, null), resolution, "");

        assertEquals("rule", normalized.ruleName());
        assertEquals("project", normalized.projectId());
        assertEquals("agent", normalized.agentDefinitionId());
        assertEquals("LATEST_PUBLISHED", normalized.agentBindingMode());
        assertEquals(4, normalized.agentVersion());
        assertEquals("agent-v4", normalized.agentDefinitionHash());
    }

    @Test
    void requiresPinnedVersionAndRejectsHashMismatch() {
        AlertRuleCandidate missingVersion = candidate("rule", "project", "agent", "PINNED_VERSION", null);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(missingVersion, resolution, ""));

        AlertRuleCandidate pinned = candidate("rule", "project", "agent", "PINNED_VERSION", 4);
        AlertRuleCandidate mismatched = copy(
                pinned, "wrong-hash", null, null, null, null, null, null, null, null);
        SecurityException mismatch = assertThrows(SecurityException.class,
                () -> policy.normalize(mismatched, resolution, ""));

        assertEquals("PINNED_VERSION 告警规则必须选择 Agent 版本", missing.getMessage());
        assertEquals("ALERT_AGENT_DEFINITION_HASH_MISMATCH", mismatch.getMessage());
    }

    @Test
    void clampsRuntimeLimitsAndAppliesDefaults() {
        AlertRuleCandidate candidate = copy(
                candidate("rule", "project", "agent", null, null),
                null, null, null, 0, 99, 0, 100, 1, null);

        AlertRuleDefinition normalized = policy.normalize(candidate, resolution, "");

        assertEquals(1, normalized.rangeMinutes());
        assertEquals(10, normalized.subAgentMaxIterations());
        assertEquals(1, normalized.nodeTimeoutSeconds());
        assertEquals(50, normalized.maxEvidenceItems());
        assertEquals(30, normalized.dedupWindowSeconds());
        assertEquals("5m", normalized.promWindow());
        assertTrue(normalized.includeRecentLogs());
        assertEquals("ALERTMANAGER", normalized.sourceType());
        assertEquals(1, normalized.status());
    }

    @Test
    void requiresNotificationChannelAndTargetWhenEnabled() {
        AlertRuleCandidate noChannel = copy(
                candidate("rule", "project", "agent", null, null),
                null, true, null, null, null, null, null, null, null);
        AlertRuleCandidate noTarget = copy(
                candidate("rule", "project", "agent", null, null),
                null, true, "channel-1", null, null, null, null, null, "");

        assertEquals("启用告警通知时必须选择 Channel",
                assertThrows(IllegalArgumentException.class,
                        () -> policy.normalize(noChannel, resolution, "")).getMessage());
        assertEquals("启用告警通知时必须填写通知目标",
                assertThrows(IllegalArgumentException.class,
                        () -> policy.normalize(noTarget, resolution, "")).getMessage());
    }

    @Test
    void rejectsInvalidRuleAndLabelRegex() {
        AlertRuleCandidate invalidRule = new AlertRuleCandidate(
                null, "rule", 1, "ALERTMANAGER", "[", "", "", Map.of(),
                "", "", "", "project", "agent", "LATEST_PUBLISHED", null, "",
                "", null, "", null, false, null, null, null, null);
        AlertRuleCandidate invalidLabel = new AlertRuleCandidate(
                null, "rule", 1, "ALERTMANAGER", "", "", "", Map.of("service", "~["),
                "", "", "", "project", "agent", "LATEST_PUBLISHED", null, "",
                "", null, "", null, false, null, null, null, null);

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(invalidRule, resolution, "")).getMessage().contains("alertNameRegex"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> policy.normalize(invalidLabel, resolution, "")).getMessage().contains("matchLabels.service"));
    }

    @Test
    void preservesExistingSecretForMaskedUpdate() {
        AlertRuleCandidate update = new AlertRuleCandidate(
                7L, "rule", 1, "ALERTMANAGER", "", "", "", Map.of(),
                "", "", "******", "project", "agent", "LATEST_PUBLISHED", null, "",
                "", null, "", null, false, null, null, null, null);

        AlertRuleDefinition normalized = policy.normalize(update, resolution, "stored-secret");

        assertEquals("stored-secret", normalized.webhookSecret());
    }

    private AlertRuleCandidate candidate(
            String name,
            String project,
            String agent,
            String mode,
            Integer version) {
        return new AlertRuleCandidate(
                null, name, null, "", "", "", "", Map.of(),
                "", "", "", project, agent, mode, version, "",
                "", null, "", null, false, null, null, null, null);
    }

    private AlertRuleCandidate copy(
            AlertRuleCandidate source,
            String hash,
            Boolean notify,
            String channelId,
            Integer rangeMinutes,
            Integer iterations,
            Integer timeout,
            Integer evidence,
            Integer dedup,
            String target) {
        return new AlertRuleCandidate(
                source.id(), source.ruleName(), source.status(), source.sourceType(),
                source.alertNameRegex(), source.severityRegex(), source.serviceRegex(), source.matchLabels(),
                channelId == null ? source.notificationChannelId() : channelId,
                target == null ? source.notificationTarget() : target,
                source.webhookSecret(), source.projectId(), source.agentDefinitionId(),
                source.agentBindingMode(), source.agentVersion(), hash == null ? source.agentDefinitionHash() : hash,
                source.questionTemplate(), rangeMinutes, source.promWindow(), source.includeRecentLogs(),
                notify == null ? source.notifyChannel() : notify, iterations, timeout, evidence, dedup);
    }
}
