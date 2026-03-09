package cn.lgs.orbisops.domain.audit.service;

public final class AnalysisAuditPolicy {

    public int listLimit(int requested) {
        return Math.max(1, Math.min(requested, 100));
    }

    public int historyCapacity(int configured) {
        return Math.max(1, configured);
    }
}
