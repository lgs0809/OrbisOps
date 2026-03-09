package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;

import java.util.Map;

public interface ChangePackageAuditPort {

    void record(String projectId,
                String action,
                String packageId,
                ChangePackageCurrent before,
                Map<String, Object> after);
}
