package cn.lgs.orbisops.application.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application process manager for runtime Skill usage and post-run facts. */
public class SkillRuntimeUsageApplicationService {

    private final SkillRuntimeUsagePort usagePort;
    private final SkillEffectMetricApplicationService metricService;
    private final SkillTransactionPort transactionPort;

    public SkillRuntimeUsageApplicationService(
            SkillRuntimeUsagePort usagePort,
            SkillEffectMetricApplicationService metricService,
            SkillTransactionPort transactionPort) {
        if (usagePort == null) {
            throw new IllegalArgumentException("SKILL_RUNTIME_USAGE_PORT_REQUIRED");
        }
        if (metricService == null) {
            throw new IllegalArgumentException("SKILL_EFFECT_METRIC_SERVICE_REQUIRED");
        }
        if (transactionPort == null) {
            throw new IllegalArgumentException("SKILL_TRANSACTION_PORT_REQUIRED");
        }
        this.usagePort = usagePort;
        this.metricService = metricService;
        this.transactionPort = transactionPort;
    }

    public void record(SkillRuntimeUsageCommand command) {
        if (command == null) return;
        transactionPort.required(() -> {
            for (SkillRuntimeUsageReference reference : command.references()) {
                if (reference == null
                        || value(reference.skillId()).isBlank()
                        || reference.version() <= 0) {
                    continue;
                }
                if (usagePort.insert(command, reference)) {
                    metricService.record(
                            command.projectId(),
                            reference.skillId(),
                            reference.version(),
                            command.outcome());
                }
            }
            return Boolean.TRUE;
        });
    }

    public int reconcileRunOutcome(
            String projectId,
            String runId,
            Map<String, Object> facts) {
        if (value(projectId).isBlank()
                || value(runId).isBlank()
                || facts == null
                || facts.isEmpty()) {
            return 0;
        }
        return transactionPort.required(() -> {
            int updatedCount = 0;
            for (SkillRuntimeUsageRecord record : usagePort.lockForRun(
                    projectId,
                    runId)) {
                Map<String, Object> before = record.outcome();
                Map<String, Object> after = new LinkedHashMap<>(before);
                facts.forEach((key, value) -> {
                    if (value != null) after.put(key, value);
                });
                if (before.equals(after)) continue;
                if (!usagePort.compareAndSetOutcome(
                        record.id(),
                        record.persistenceToken(),
                        after)) {
                    throw new IllegalStateException(
                            "SKILL_USAGE_OUTCOME_MVCC_CONFLICT:" + runId);
                }
                metricService.applyOutcomeDelta(
                        projectId,
                        record.skillId(),
                        record.skillVersion(),
                        before,
                        after);
                updatedCount++;
            }
            return updatedCount;
        });
    }

    public List<Map<String, Object>> listForRun(
            String projectId,
            String runId) {
        return usagePort.listForRun(projectId, runId);
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
