package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionLeasePort;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionJobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Spring-managed renewal threads cannot be starved by the model worker they protect. */
@Configuration
public class OpsSkillEvolutionLeaseConfiguration {
    @Bean("skillEvolutionLeaseScheduler")
    public ThreadPoolTaskScheduler skillEvolutionLeaseScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("skill-lease-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    public SkillEvolutionLeasePort skillEvolutionLeasePort(ISkillEvolutionJobRepository repository,
            @Qualifier("skillEvolutionLeaseScheduler") TaskScheduler scheduler) {
        return claim -> {
            if (!repository.renewLease(claim)) throw new IllegalStateException("SKILL_EVOLUTION_CLAIM_LOST");
            var owned = new AtomicBoolean(true);
            var future = scheduler.scheduleWithFixedDelay(() -> {
                if (!owned.get()) return;
                try {
                    if (!repository.renewLease(claim)) owned.set(false);
                } catch (RuntimeException unavailable) {
                    // Stop trusting ownership after a failed renewal. Database fences remain authoritative.
                    owned.set(false);
                }
            }, scheduler.getClock().instant().plusSeconds(30), Duration.ofSeconds(30));
            return new SkillEvolutionLeasePort.Scope() {
                public void requireOwned() {
                    if (!owned.get()) throw new IllegalStateException("SKILL_EVOLUTION_CLAIM_LOST");
                }
                public void close() {
                    owned.set(false);
                    if (future != null) future.cancel(false);
                }
            };
        };
    }
}
