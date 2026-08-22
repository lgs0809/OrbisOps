package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryFacadeSettings;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSemanticMemoryRetrievalSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMemoryFacadeConfiguration {

    @Bean
    public OpsMemoryFacadeSettings opsMemoryFacadeSettings(
            @Value("${orbisops.chat.memory.enabled:true}") boolean enabled,
            @Value("${orbisops.chat.memory.hot-max-messages:12}") int hotMaxMessages,
            @Value("${orbisops.chat.memory.hot-buffer-messages:24}") int hotBufferMessages,
            @Value("${orbisops.chat.memory.item-match-limit:8}") int itemMatchLimit,
            @Value("${orbisops.chat.memory.semantic-top-k:8}") int semanticTopK,
            @Value("${orbisops.chat.memory.context-max-chars:8000}") int contextMaxChars,
            @Value("${orbisops.chat.memory.assemble-timeout-millis:1200}") long assembleTimeoutMillis,
            @Value("${orbisops.chat.memory.extraction-async-enabled:true}") boolean extractionAsyncEnabled,
            @Value("${orbisops.chat.memory.recency-aware-enabled:true}") boolean recencyAwareEnabled,
            @Value("${orbisops.chat.memory.recency-half-life-turns:6}") double recencyHalfLifeTurns) {
        return new OpsMemoryFacadeSettings(
                enabled,
                hotMaxMessages,
                hotBufferMessages,
                itemMatchLimit,
                semanticTopK,
                contextMaxChars,
                assembleTimeoutMillis,
                extractionAsyncEnabled,
                recencyAwareEnabled,
                recencyHalfLifeTurns);
    }

    @Bean
    public OpsSemanticMemoryRetrievalSettings opsSemanticMemoryRetrievalSettings(
            OpsMemoryFacadeSettings settings) {
        return OpsSemanticMemoryRetrievalSettings.from(settings);
    }
}
