package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.service.SkillRouteProjectionIdentity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded cyclic repair scan; PG commits never claim a distributed MySQL/PG transaction. */
@lombok.extern.slf4j.Slf4j
@Component
public final class OpsSkillRouteProjectionWorker {
    private final SkillRouteProjectionSourcePort source;
    private final SkillRouteIndexPort index;
    private final OpsSkillRetrievalHttpClient model;
    private final SkillRoutePublicationPort publication;
    private long cursor;
    public OpsSkillRouteProjectionWorker(SkillRouteProjectionSourcePort source,SkillRouteIndexPort index,OpsSkillRetrievalHttpClient model,SkillRoutePublicationPort publication) {
        this.source=source;this.index=index;this.model=model;this.publication=publication;
    }
    @Scheduled(fixedDelayString="${orbisops.skill-runtime.retrieval.scan-delay-ms:60000}")
    public synchronized void scan() {
        if(!model.configured()) return;
        var page=source.page(cursor,10);
        if(page.isEmpty()) { cursor=0;return; }
        for(var entry:page) {
            try {
                var candidate=SkillCatalogSnapshot.fromView(new SkillCatalogViewMapper().toView(entry,false)).runtimeCandidate();
                if(!candidate.activeAtUse() || !candidate.routingReady()) continue;
                if(!index.contains(candidate,model.modelIdentity())) {
                    index.stage(candidate,model.modelIdentity());
                    var embedding=model.embed(SkillRouteProjectionIdentity.document(candidate),false);
                    if(source.current(candidate)) index.ready(candidate,model.modelIdentity(),embedding);
                }
                // Retrying after PG READY but before MySQL pointer commit must not skip activation.
                publication.activate(candidate,model.modelIdentity());
            } catch(RuntimeException unavailable) {
                log.warn("Skill route projection pending skill={} version={} reason={}",entry.skillId(),entry.currentVersion(),OpsSkillBackgroundFailure.code(unavailable));
                // A BUILDING generation stays invisible. Restart or the next scan retries it;
                // the previously READY immutable generation is never overwritten.
            } finally { cursor=entry.id(); }
        }
    }
}
