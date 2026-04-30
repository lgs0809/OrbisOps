package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class OpsSkillPublishedVersionAdapter implements SkillRuntimePublishedVersionPort {
    private final OpsSkillRetrievalHttpClient model;
    private final SkillRoutePublicationPort publication;
    public OpsSkillPublishedVersionAdapter(OpsSkillRetrievalHttpClient model, SkillRoutePublicationPort publication) {
        this.model = model; this.publication = publication;
    }
    @Override public List<SkillRuntimeCandidate> visible(String project, List<SkillRuntimeCandidate> current) {
        return visible(project,current,java.util.Set.of());
    }
    @Override public List<SkillRuntimeCandidate> visible(String project,List<SkillRuntimeCandidate> current,java.util.Set<String> explicit) {
        // The unconfigured legacy text-only mode is retained; configuring the fixed
        // retrieval model enables generation publication, without a silent migration.
        return model.configured() ? publication.visible(project,current,model.modelIdentity(),explicit)
                : publication.lifecycleVisible(project,current,explicit);
    }
    @Override public List<SkillRuntimeCandidate> usableFrozen(String project,List<SkillRuntimeCandidate> frozen) {
        return publication.lifecycleVisible(project,frozen,frozen.stream().map(SkillRuntimeCandidate::skillId)
                .collect(java.util.stream.Collectors.toSet()));
    }
}
