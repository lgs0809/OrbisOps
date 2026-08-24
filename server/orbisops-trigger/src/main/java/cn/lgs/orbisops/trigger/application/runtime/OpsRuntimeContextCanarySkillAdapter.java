package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextCanarySkillPort;
import cn.lgs.orbisops.application.skill.SkillCanaryContextApplicationService;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextCanarySkillSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillCanaryCandidateSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** Cross-context ACL from Skill release governance to Runtime Context canary identity. */
@Component
public class OpsRuntimeContextCanarySkillAdapter implements RuntimeContextCanarySkillPort {

    private final ObjectProvider<SkillCanaryContextApplicationService> serviceProvider;

    public OpsRuntimeContextCanarySkillAdapter(
            ObjectProvider<SkillCanaryContextApplicationService> serviceProvider) {
        if (serviceProvider == null) {
            throw new IllegalArgumentException("SKILL_CANARY_CONTEXT_PROVIDER_REQUIRED");
        }
        this.serviceProvider = serviceProvider;
    }

    @Override
    public List<RuntimeContextCanarySkillSnapshot> resolve(
            String projectId,
            String agentId,
            String runId) {
        return resolve(projectId, agentId, runId, "");
    }

    @Override
    public List<RuntimeContextCanarySkillSnapshot> resolve(
            String projectId, String agentId, String runId, String query) {
        SkillCanaryContextApplicationService service = serviceProvider.getIfAvailable();
        if (service == null) return List.of();
        return service.resolveCandidates(projectId, agentId, runId, query).stream()
                .map(this::snapshot)
                .toList();
    }

    private RuntimeContextCanarySkillSnapshot snapshot(
            SkillCanaryCandidateSnapshot candidate) {
        return new RuntimeContextCanarySkillSnapshot(
                candidate.runtimeSkillId(),
                candidate.runtimeVersion(),
                candidate.candidateHash(),
                candidate.candidateId(),
                candidate.releaseId(),
                candidate.projectId(),
                candidate.agentId());
    }
}
