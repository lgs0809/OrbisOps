package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryExtractionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMemoryExtractionConfiguration {

    @Bean
    public OpsMemoryExtractionSettings opsMemoryExtractionSettings(
            @Value("${orbisops.chat.memory.extraction-enabled:true}") boolean enabled,
            @Value("${orbisops.chat.memory.extraction-max-items-per-message:6}") int maxItemsPerMessage,
            @Value("${orbisops.chat.memory.llm-extraction-enabled:true}") boolean modelEnabled,
            @Value("${orbisops.chat.memory.llm-extraction-max-input-chars:4000}") int modelMaxInputChars) {
        return new OpsMemoryExtractionSettings(
                enabled,
                maxItemsPerMessage,
                modelEnabled,
                modelMaxInputChars);
    }
}
