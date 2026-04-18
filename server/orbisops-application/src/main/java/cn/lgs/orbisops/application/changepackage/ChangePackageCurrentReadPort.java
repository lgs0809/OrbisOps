package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;

import java.util.Optional;

/** Narrow current-state read boundary for cross-context command surfaces such as Channel approval. */
public interface ChangePackageCurrentReadPort {

    boolean available();

    Optional<ChangePackageCurrent> find(String packageId);
}
