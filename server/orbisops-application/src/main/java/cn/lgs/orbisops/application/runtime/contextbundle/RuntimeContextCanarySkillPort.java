package cn.lgs.orbisops.application.runtime.contextbundle;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextCanarySkillSnapshot;

import java.util.List;

/** Published-language boundary from Skill release governance into Runtime Context assembly. */
public interface RuntimeContextCanarySkillPort {

    List<RuntimeContextCanarySkillSnapshot> resolve(
            String projectId,
            String agentId,
            String runId);
    default List<RuntimeContextCanarySkillSnapshot> resolve(
            String projectId, String agentId, String runId, String query) {
        return resolve(projectId, agentId, runId);
    }
}
