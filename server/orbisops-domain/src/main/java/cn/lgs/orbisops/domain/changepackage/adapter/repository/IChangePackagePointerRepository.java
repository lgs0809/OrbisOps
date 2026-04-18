package cn.lgs.orbisops.domain.changepackage.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingCompletion;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationFailure;

import java.util.Optional;

public interface IChangePackagePointerRepository {

    boolean available();

    Optional<ChangePackagePointer> find(String packageId);

    boolean compareAndSetStatus(ChangePackagePointer expected, ChangePackageStatus nextStatus);

    boolean compareAndSetVersion(ChangePackagePointer expected,
                                 ChangePackagePointer next,
                                 ChangePackageCurrentState nextState);

    boolean compareAndSetValidationFailure(ChangePackagePointer expected,
                                           ChangePackageValidationFailure failure);

    boolean compareAndSetApproved(ChangePackagePointer expected,
                                  ChangePackageSnapshot approvedSnapshot,
                                  String actor);

    boolean compareAndSetLandingStarted(ChangePackagePointer expected, String landingRunId);

    boolean compareAndSetLandingResult(ChangePackagePointer expected,
                                       ChangePackageLandingCompletion completion);
}
