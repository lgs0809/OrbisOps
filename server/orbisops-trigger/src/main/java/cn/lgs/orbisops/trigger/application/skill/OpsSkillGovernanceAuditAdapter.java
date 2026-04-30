package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillGovernanceAuditPort;
import cn.lgs.orbisops.application.skill.SkillGovernanceAuditRecord;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public final class OpsSkillGovernanceAuditAdapter implements SkillGovernanceAuditPort {

    private final OpsConfigAuditService audits;

    public OpsSkillGovernanceAuditAdapter(OpsConfigAuditService audits) {
        this.audits = audits;
    }

    @Override
    public void record(SkillGovernanceAuditRecord record) {
        if (record == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_AUDIT_RECORD_REQUIRED");
        Map<String, Object> after = state(record.after());
        after.put("actor", record.actor());
        after.put("reason", record.reason());
        after.put("approvalId", record.approvalId());
        after.put("occurredAt", record.occurredAt());
        audits.record(record.projectId(), "skill-governance",
                record.action().toLowerCase().replace('_', '-'), record.skillId(),
                state(record.before()), after);
    }

    private Map<String, Object> state(SkillGovernanceState state) {
        if (state == null) return Map.of();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("lifecycleStatus", state.lifecycleStatus().name());
        value.put("mutationMode", state.mutationMode().name());
        value.put("executionMode", state.executionMode().name());
        value.put("bindingMode", state.bindingMode().name());
        value.put("lockType", state.lock().type().name());
        value.put("lockReason", state.lock().reason());
        value.put("lockActor", state.lock().actor());
        value.put("lockApprovalId", state.lock().approvalId());
        value.put("lockAt", state.lock().lockedAt());
        value.put("legacyFrozenClassificationRequired", state.legacyFrozenClassificationRequired());
        return value;
    }
}
