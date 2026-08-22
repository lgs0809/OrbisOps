package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsFinalReportSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsFinalReportConfiguration {

    @Bean
    public OpsFinalReportSettings opsFinalReportSettings(
            @Value("${orbisops.multi-agent.final-report-llm-enabled:true}") boolean llmEnabled,
            @Value("${orbisops.multi-agent.final-report-max-chars:8000}") int maxChars) {
        return new OpsFinalReportSettings(llmEnabled, maxChars);
    }
}
