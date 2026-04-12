package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.trigger.ops.OpsAnalysisRunSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring property binding boundary for asynchronous analysis runs. */
@Configuration
public class OpsAnalysisRunConfiguration {

    @Bean
    public OpsAnalysisRunSettings opsAnalysisRunSettings(
            @Value("${orbisops.runs.max-memory-records:200}") int maxMemoryRecords,
            @Value("${orbisops.runs.allow-in-memory-fallback:false}") boolean allowInMemoryFallback,
            @Value("${orbisops.runs.reject-when-queue-full:true}") boolean rejectWhenQueueFull) {
        return new OpsAnalysisRunSettings(
                maxMemoryRecords,
                allowInMemoryFallback,
                rejectWhenQueueFull);
    }
}
