package cn.lgs.orbisops.application.episode;

import cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPort;
import cn.lgs.orbisops.domain.shared.service.BackgroundModelRetryPolicy;
import java.time.Clock;
import java.util.EnumMap;

/** One stopped job per maintenance tick, retaining its original identity, owner and failure history. */
public final class BackgroundModelRecoveryService {
    private final BackgroundModelRecoveryPort store;
    private final BackgroundModelReadinessPort readiness;
    private final BackgroundTaskOwnerAccessPort owners;
    private final Clock clock;
    private int familyCursor;
    private final EnumMap<BackgroundModelRecoveryPort.Family,String> cursors = new EnumMap<>(BackgroundModelRecoveryPort.Family.class);

    public BackgroundModelRecoveryService(BackgroundModelRecoveryPort store, BackgroundModelReadinessPort readiness,
            BackgroundTaskOwnerAccessPort owners, Clock clock) {
        this.store=store;this.readiness=readiness;this.owners=owners;this.clock=clock;
    }
    public synchronized int recoverNext() {
        var families=BackgroundModelRecoveryPort.Family.values();
        var family=families[Math.floorMod(familyCursor++,families.length)];
        var candidates=store.candidates(family,"","",cursors.getOrDefault(family,""),20);
        if(candidates.isEmpty()) {cursors.put(family,"");return 0;}
        for(var candidate:candidates) {
            cursors.put(family,candidate.taskId());
            try {if(restore(candidate,"SYSTEM_BACKGROUND_MODEL_RECOVERY"))return 1;}
            catch(SecurityException denied) { /* Revoked/disabled owner stays stopped; no privilege substitution. */ }
        }
        return 0;
    }
    public boolean recover(String project, BackgroundModelRecoveryPort.Family family, String taskId, String actor) {
        if(project==null || project.isBlank() || taskId==null || taskId.isBlank()) throw new IllegalArgumentException("BACKGROUND_MODEL_TASK_ID_REQUIRED");
        var rows=store.candidates(family,project,taskId,"",1);
        return !rows.isEmpty() && restore(rows.get(0),actor);
    }
    private boolean restore(BackgroundModelRecoveryPort.Candidate candidate,String actor) {
        if(!readiness.ready(candidate)) return false;
        long next=clock.millis()+BackgroundModelRetryPolicy.delayMillis(candidate.family()+":"+candidate.taskId(),
                Math.max(1,candidate.attempts()),30_000,0);
        return store.restore(candidate,next,actor,()->{
            owners.requireAccess(candidate.projectId(),candidate.ownerId());
            if(!readiness.ready(candidate)) throw new SecurityException("BACKGROUND_MODEL_CONFIGURATION_CHANGED");
        });
    }
}
