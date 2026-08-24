package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowChangeVerificationPolicy;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;

/** Reads approval and execution facts through the owning application service. */
@Component
final class OpsWorkflowChangeContextReader {
    private final ChangePackageQueryService changes;
    OpsWorkflowChangeContextReader(ChangePackageQueryService changes) { this.changes=changes; }

    /** A UI-selected identity is still untrusted: resolve it against current project facts before routing. */
    Map<String,Object> selected(String project, Map<String,Object> metadata) {
        Object selected = metadata == null ? null : metadata.get("selectedChangePackageId");
        if (selected == null) return Map.of("status", "NEED_RESOLUTION");
        if (!(selected instanceof String id) || !id.matches("[A-Za-z0-9_-]{1,100}"))
            throw new IllegalArgumentException("CHANGE_VERIFICATION_PACKAGE_ID_REQUIRED");
        var current = changes.detail(id);
        if (!project.equals(current.get("projectId")) || !id.equals(current.get("packageId")))
            throw new SecurityException("CHANGE_VERIFICATION_PROJECT_MISMATCH");
        // Approval, execution, windows and SLOs are deliberately NOT taken from metadata.
        return Map.of("status", "READY", "projectId", project, "packageId", id);
    }

    Map<String,Object> read(String project,Map<String,Object> input,Instant now) {
        if (!project.equals(input.get("projectId"))) throw new SecurityException("CHANGE_VERIFICATION_PROJECT_MISMATCH");
        Object id=input.get("packageId");
        if (!(id instanceof String packageId) || !packageId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("CHANGE_VERIFICATION_PACKAGE_ID_REQUIRED");
        }
        var policy=new WorkflowChangeVerificationPolicy();
        Map<String,Object> current;
        try { current=changes.detail(packageId); }
        catch (RuntimeException unavailable) { return policy.unavailable(packageId,"CHANGE_RECORD_UNAVAILABLE"); }
        if (!project.equals(current.get("projectId"))) throw new SecurityException("CHANGE_VERIFICATION_PROJECT_MISMATCH");
        // Ignore input fields claiming LANDED, approval, expected versions, or relaxed SLOs.
        return policy.prepare(project,packageId,current,
                "LANDED".equals(current.get("status")) ? changes.landingOperationRuns(packageId,200) : java.util.List.of(),now);
    }
}
