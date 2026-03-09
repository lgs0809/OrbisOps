package cn.lgs.orbisops.domain.changepackage.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentQuery;

import java.util.List;
import java.util.Optional;

public interface IChangePackageCurrentRepository {

    boolean available();

    void verifyReadable();

    void insert(ChangePackageCurrent current);

    Optional<ChangePackageCurrent> find(String packageId);

    List<ChangePackageCurrent> findAll(ChangePackageCurrentQuery query);
}
