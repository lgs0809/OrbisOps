package cn.lgs.orbisops.domain.changepackage.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

import java.util.List;
import java.util.Optional;

public interface IChangePackageVersionRepository {

    boolean available();

    void append(ChangePackageVersion version);

    Optional<ChangePackageVersion> find(String packageId, int version);

    List<ChangePackageVersion> findAll(String packageId);
}
