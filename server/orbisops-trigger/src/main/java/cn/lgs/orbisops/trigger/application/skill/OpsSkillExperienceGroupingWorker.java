package cn.lgs.orbisops.trigger.application.skill;
import cn.lgs.orbisops.application.skill.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

/** Re-enqueue only accepted, previously insufficient and never published sources through the normal job command. */
@Component
public final class OpsSkillExperienceGroupingWorker {
    private final SkillExperienceGroupingStore store;
    private final SkillEvolutionJobApplicationService jobs;
    public OpsSkillExperienceGroupingWorker(SkillExperienceGroupingStore store,SkillEvolutionJobApplicationService jobs) {this.store=store;this.jobs=jobs;}
    @Scheduled(fixedDelayString="${orbisops.skill-evolution.grouping-backfill-delay-ms:60000}",initialDelay=30000)
    public void discover() {
        for(var source:store.ungrouped(10)) {
            try {jobs.enqueue(source.runId(),source.sessionId(),source.projectId(),source.agentId(),"EXPERIENCE_GROUPING_BACKFILL");}
            catch(IllegalStateException revoked) { /* The next scan rechecks current acceptance. */ }
        }
    }
}
