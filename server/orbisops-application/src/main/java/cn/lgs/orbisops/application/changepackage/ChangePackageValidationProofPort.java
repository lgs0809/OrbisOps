package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationWriteback;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

import java.util.Map;

public interface ChangePackageValidationProofPort {

    void verifySourceProofs(ChangePackageCurrent current,
                            ChangePackageVersion sourceVersion,
                            Map<String, Object> validationReport);

    void recordWritebackProof(ChangePackageCurrent current,
                              ChangePackageValidationWriteback writeback,
                              String actor);
}
