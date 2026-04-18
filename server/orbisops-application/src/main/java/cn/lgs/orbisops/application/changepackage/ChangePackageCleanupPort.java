package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;

/** Cross-context cleanup boundary from ChangePackage to Repair workspace. */
public interface ChangePackageCleanupPort {

    ChangePackageRepairCleanupOutcome cleanupRepairWorkspace(
            ChangePackageCurrent current,
            String workspaceId,
            String actor);
}
