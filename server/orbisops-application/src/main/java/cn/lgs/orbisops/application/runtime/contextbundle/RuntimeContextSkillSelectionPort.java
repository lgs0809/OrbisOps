package cn.lgs.orbisops.application.runtime.contextbundle;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextSkillSelection;

public interface RuntimeContextSkillSelectionPort {

    RuntimeContextSkillSelection select(RuntimeContextSkillSelectionRequest request);
}
