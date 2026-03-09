package cn.lgs.orbisops.domain.changepackage.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageEvent;

import java.util.List;

public interface IChangePackageEventRepository {

    boolean available();

    void append(ChangePackageEvent event);

    List<ChangePackageEvent> findRecent(String packageId, int limit);
}
