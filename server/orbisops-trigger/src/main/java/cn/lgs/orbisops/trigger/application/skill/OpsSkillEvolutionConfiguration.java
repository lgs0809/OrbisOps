package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.trigger.ops.OpsSkillEvolutionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSkillEvolutionConfiguration {

    @Bean
    public OpsSkillEvolutionSettings opsSkillEvolutionSettings(
            @Value("${orbisops.skill-evolver.trigger.enabled:true}") boolean triggerEnabled,
            @Value("${orbisops.skill-evolver.worker.enabled:false}") boolean workerEnabled,
            @Value("${orbisops.skill-evolver.worker.batch-size:5}") int batchSize,
            @Value("${orbisops.skill-evolver.worker.max-attempts:3}") int maxAttempts,
            @Value("${orbisops.skill-evolver.worker.fixed-delay-ms:60000}") long fixedDelayMillis) {
        return new OpsSkillEvolutionSettings(
                triggerEnabled,
                workerEnabled,
                batchSize,
                maxAttempts,
                fixedDelayMillis);
    }
}
