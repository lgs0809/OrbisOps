package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.*;

/** No model or maintenance scan occupies user request threads. Restart resumes persisted checks. */
@Configuration
@lombok.extern.slf4j.Slf4j
public class OpsSkillMaintenanceWorker {
    private final SkillMaintenanceApplicationService service;
    private final boolean enabled;
    private final JTokkitTokenCountEstimator tokenizer=new JTokkitTokenCountEstimator(EncodingType.O200K_BASE);
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"skill-maintenance");t.setDaemon(true);return t;});
    private long cursor=0,nextScan=0,nextFailureLog=0;
    public OpsSkillMaintenanceWorker(SkillMaintenancePort store,SkillCatalogQueryService catalog,SkillManagementUseCase management,
            SkillTransactionPort tx,SkillCompressionReviewPort review,
            @Value("${orbisops.skill-evolution.enabled:true}") boolean enabled) {
        service=new SkillMaintenanceApplicationService(store,catalog,management,tx,review);
        this.enabled=enabled;
    }
    @Bean public SkillMaintenanceApplicationService skillMaintenanceApplicationService() {return service;}
    @PostConstruct void start() {worker.scheduleWithFixedDelay(this::runOnce,30,10,TimeUnit.SECONDS);}
    @PreDestroy void stop() {worker.shutdownNow();}
    public void runOnce() {
        if(!enabled) return;
        try {
            long now=System.currentTimeMillis();
            if(now>=nextScan || cursor!=0) {
                // Named tokenizer estimate, not provider billing or an asserted exact count for an alias model.
                cursor=service.scanBatch(cursor,tokenizer::estimate,"o200k-referenced-text-estimate-v1",Instant.ofEpochMilli(now));
                if(cursor==0) nextScan=now+TimeUnit.HOURS.toMillis(24);
            }
            service.checkNext();
        } catch(RuntimeException unavailable) {
            // Existing rows/leases remain authoritative during schema startup, DB or model outages.
            long now=System.currentTimeMillis();
            if(now>=nextFailureLog) {
                log.warn("Skill maintenance scan deferred reason={}",OpsSkillBackgroundFailure.code(unavailable));
                nextFailureLog=now+TimeUnit.MINUTES.toMillis(1);
            }
        }
    }
}
