package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;

/** Typed audit event emitted after a successful Context Memory mutation. */
public record ContextMemoryAuditEvent(
        String action,
        ContextMemorySnapshot snapshot) {

    public ContextMemoryAuditEvent {
        action = action == null ? "" : action;
    }
}
