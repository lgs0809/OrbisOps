package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeAction;

public interface ControlledCodeAuditPort {

    void record(ControlledCodeAuditEvent event);

    record ControlledCodeAuditEvent(
            String projectId,
            String actor,
            ControlledCodeAction action,
            String targetId,
            String riskLevel,
            String status,
            Object payload) {
    }
}
