package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMainQuestionRewriteSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMainQuestionRewriteConfiguration {

    @Bean
    public OpsMainQuestionRewriteSettings opsMainQuestionRewriteSettings(
            @Value("${orbisops.chat.query-rewrite.enabled:true}") boolean enabled,
            @Value("${orbisops.chat.query-rewrite.max-memory-chars:5000}") int maxMemoryChars,
            @Value("${orbisops.chat.query-rewrite.max-question-chars:1200}") int maxQuestionChars) {
        return new OpsMainQuestionRewriteSettings(
                enabled,
                maxMemoryChars,
                maxQuestionChars);
    }
}
