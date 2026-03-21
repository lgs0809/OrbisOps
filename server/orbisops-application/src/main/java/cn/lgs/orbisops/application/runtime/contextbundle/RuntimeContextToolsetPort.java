package cn.lgs.orbisops.application.runtime.contextbundle;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolsetSnapshot;

import java.util.List;

/** Published-language boundary from Toolset catalog into Runtime Context assembly. */
public interface RuntimeContextToolsetPort {

    List<RuntimeContextToolsetSnapshot> listEffective(
            String projectId,
            String actor);
}
