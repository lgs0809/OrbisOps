package cn.lgs.orbisops.application.runtime.contextbundle;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;

public interface RuntimeContextBundleAuditPort {

    void recordCreated(RuntimeContextBundleSnapshot snapshot);
}
