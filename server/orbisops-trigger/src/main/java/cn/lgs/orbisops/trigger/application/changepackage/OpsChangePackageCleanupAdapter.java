package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageCleanupPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageRepairCleanupOutcome;
import cn.lgs.orbisops.application.repair.RepairWorkspaceApplicationService;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import org.springframework.stereotype.Component;

/** ACL translating Repair cleanup facts into the ChangePackage published language. */
@Component
public class OpsChangePackageCleanupAdapter implements ChangePackageCleanupPort {

    private final RepairWorkspaceApplicationService repairWorkspaces;

    public OpsChangePackageCleanupAdapter(
            RepairWorkspaceApplicationService repairWorkspaces) {
        if (repairWorkspaces == null) {
            throw new IllegalArgumentException("REPAIR_WORKSPACE_APPLICATION_SERVICE_REQUIRED");
        }
        this.repairWorkspaces = repairWorkspaces;
    }

    @Override
    public ChangePackageRepairCleanupOutcome cleanupRepairWorkspace(
            ChangePackageCurrent current,
            String workspaceId,
            String actor) {
        RepairWorkspace workspace = repairWorkspaces.get(workspaceId);
        requireEquals(workspace.projectId(), current.projectId(),
                "workspace projectId 与 ChangePackage 不一致");
        requireEquals(workspace.serviceId(),
                text(current.state().nullable(ChangePackageCurrentField.SERVICE_ID)),
                "workspace serviceId 与 ChangePackage 不一致");
        requireEquals(workspace.repositoryId(),
                text(current.state().nullable(ChangePackageCurrentField.REPOSITORY_ID)),
                "workspace repositoryId 与 ChangePackage 不一致");
        requireEquals(workspace.baseCommit(),
                text(current.state().nullable(ChangePackageCurrentField.BASE_COMMIT)),
                "workspace baseCommit 与 ChangePackage 不一致");
        String repairCommit = text(current.state().nullable(ChangePackageCurrentField.REPAIR_COMMIT));
        if (!repairCommit.isBlank()) {
            requireEquals(workspace.verifiedCommit(), repairCommit,
                    "workspace verifiedCommit 与 ChangePackage repairCommit 不一致");
        }
        RepairCleanupResult cleanup = repairWorkspaces.cleanup(workspaceId, actor);
        return new ChangePackageRepairCleanupOutcome(
                cleanup.workspaceId(),
                cleanup.status(),
                cleanup.removed(),
                cleanup.worktreePath());
    }

    private void requireEquals(String left, String right, String message) {
        if (!text(left).equals(text(right))) throw new IllegalStateException(message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
