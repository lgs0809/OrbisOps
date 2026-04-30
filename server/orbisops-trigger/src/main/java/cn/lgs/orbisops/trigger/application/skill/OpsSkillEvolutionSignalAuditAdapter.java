package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionSignalAuditPort;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Adapter for Skill Evolution signal/hint audit projections. */
@Component
public class OpsSkillEvolutionSignalAuditAdapter implements SkillEvolutionSignalAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsSkillEvolutionSignalAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void recordSignalCreated(SkillEvolutionSignalSnapshot signal) {
        if (auditService == null || signal == null) return;
        auditService.recordRuntimeEvent(
                signal.projectId(),
                signal.agentId(),
                "",
                "skill-evolution",
                "signal-created",
                signal.signalId(),
                "LOW",
                "CREATED",
                Map.of("signalType", signal.signalType(), "runId", signal.runId()));
    }

    @Override
    public void recordHintsConsumed(String candidateId, List<String> hintIds) {
        if (auditService == null) return;
        auditService.record(
                "",
                "skill-evolution",
                "hints-consumed",
                value(candidateId),
                null,
                Map.of(
                        "candidateId", value(candidateId),
                        "hintIds", hintIds == null ? List.of() : List.copyOf(hintIds)));
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
