package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.GovernedMemoryCreationResult;
import cn.lgs.orbisops.application.memory.GovernedMemoryExternalAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Adapter projecting governed-memory creation into the shared configuration audit stream. */
@Component
public class OpsGovernedMemoryExternalAuditAdapter implements GovernedMemoryExternalAuditPort {

    private final OpsConfigAuditService auditService;
    private final OpsGovernedMemoryMapper mapper;

    public OpsGovernedMemoryExternalAuditAdapter(
            OpsConfigAuditService auditService,
            OpsGovernedMemoryMapper mapper) {
        this.auditService = auditService;
        this.mapper = mapper;
    }

    @Override
    public void recordCreate(
            String projectId,
            boolean conflict,
            GovernedMemoryCreationResult result) {
        if (auditService == null || result == null) return;
        Map<String, Object> after = mapper.creationView(result);
        auditService.record(
                projectId,
                "memory",
                conflict ? "conflict" : "create",
                result.snapshot().memoryId(),
                null,
                after);
    }
}
