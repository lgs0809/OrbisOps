package cn.lgs.orbisops.domain.changepackage.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApproval;

public interface IChangePackageApprovalRepository {

    boolean available();

    void saveDecision(ChangePackageApproval approval);

    int countDistinctApproved(String packageId, int version, String packageHash);
}
