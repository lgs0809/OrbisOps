package cn.lgs.orbisops.domain.audit.model;

public record ConfigAuditPolicySnapshot(
        AuditPolicy policy,
        boolean persistent,
        String updateTime) {

    public ConfigAuditPolicySnapshot {
        if (policy == null) throw new IllegalArgumentException("AUDIT_POLICY_REQUIRED");
        updateTime = updateTime == null ? "" : updateTime.trim();
    }
}
