package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleAuditPort;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsRuntimeContextBundleAuditAdapter implements RuntimeContextBundleAuditPort {

    private final ObjectProvider<OpsConfigAuditService> auditProvider;

    public OpsRuntimeContextBundleAuditAdapter(ObjectProvider<OpsConfigAuditService> auditProvider) {
        this.auditProvider = auditProvider;
    }

    @Override
    public void recordCreated(RuntimeContextBundleSnapshot snapshot) {
        OpsConfigAuditService audit = auditProvider.getIfAvailable();
        if (audit == null || snapshot == null) return;
        audit.record(
                snapshot.projectId(),
                "runtime-context-bundle",
                "create",
                snapshot.bundleId(),
                null,
                Map.of(
                        "contextBundleId", snapshot.bundleId(),
                        "contextBundleHash", snapshot.bundleHash(),
                        "sessionId", snapshot.sessionId()));
    }
}
