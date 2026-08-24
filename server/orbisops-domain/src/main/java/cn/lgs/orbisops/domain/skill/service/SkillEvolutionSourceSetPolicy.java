package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import java.util.*;

/** Defense against summary-only, duplicate or cross-scope author inputs. Database readers verify current acceptance. */
public final class SkillEvolutionSourceSetPolicy {
    public void requireUsable(List<SkillExperienceConsolidationSample> sources,String project,String currentRun,String currentHash) {
        if(sources==null || sources.size()<3 || sources.size()>SkillSourceBatchPolicy.ARCHIVE_LIMIT) throw invalid();
        Set<String> ids=new HashSet<>(),tasks=new HashSet<>(),conditions=new HashSet<>();
        boolean current=false;
        for(var source:sources) {
            if(!"SUCCEEDED".equals(source.outcome()) || source.taskEpisodeId().isBlank() || source.conditionKey().isBlank()
                    || source.sourceId().isBlank() || !ids.add(source.sourceId()) || !tasks.add(source.taskEpisodeId())
                    || source.episodeJson().isBlank() || !source.sourceHash().equals(CanonicalObjectHasher.sha256Text(source.episodeJson()))) throw invalid();
            var full=CanonicalJson.parseObject(source.episodeJson());
            if(!"accepted-task-episode-v1".equals(full.get("format")) || !project.equals(full.get("projectId"))
                    || !source.runId().equals(full.get("runId")) || !source.sessionId().equals(full.get("sessionId"))
                    || !source.sourceId().equals(full.get("sourceId")) || !source.taskEpisodeId().equals(full.get("episodeId"))) throw invalid();
            conditions.add(source.conditionKey());
            if(currentRun.equals(source.runId()) && currentHash.equals(source.sourceHash())) current=true;
        }
        if(!current || conditions.size()<2) throw invalid();
    }
    private IllegalStateException invalid() { return new IllegalStateException("SKILL_EVOLUTION_SOURCE_SET_INVALID"); }
}
