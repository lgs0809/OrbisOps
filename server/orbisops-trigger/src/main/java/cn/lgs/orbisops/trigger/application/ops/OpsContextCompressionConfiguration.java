package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsContextCompressionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsContextCompressionConfiguration {

    @Bean
    public OpsContextCompressionSettings opsContextCompressionSettings(
            @Value("${ops.chat.memory.compression-enabled:true}") boolean enabled,
            @Value("${ops.chat.memory.compression-hot-threshold-messages:20}") int thresholdMessages,
            @Value("${ops.chat.memory.compression-keep-recent:8}") int keepRecent,
            @Value("${ops.chat.memory.compression-llm-enabled:true}") boolean modelEnabled,
            @Value("${ops.chat.memory.compression-llm-max-input-chars:6000}") int modelMaxInputChars) {
        return new OpsContextCompressionSettings(
                enabled,
                thresholdMessages,
                keepRecent,
                modelEnabled,
                modelMaxInputChars);
    }
}
