package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;

public interface ChangePackagePreparationProofPort {

    void recordExecutionProofs(String packageId,
                               int version,
                               String packageHash,
                               String projectId,
                               ChangePackageSnapshot snapshot,
                               String actor);
}
