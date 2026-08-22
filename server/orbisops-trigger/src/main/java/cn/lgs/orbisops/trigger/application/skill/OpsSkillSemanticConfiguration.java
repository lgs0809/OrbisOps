package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.trigger.ops.skill.OpsSkillSemanticSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSkillSemanticConfiguration {

    @Bean
    public OpsSkillSemanticSettings opsSkillSemanticSettings(
            @Value("${orbisops.skill-runtime.semantic-enabled:true}") boolean semanticEnabled,
            @Value("${orbisops.skill-runtime.semantic-candidate-limit:64}") int candidateLimit,
            @Value("${orbisops.skill-runtime.embedding-cache-size:2048}") int cacheSize) {
        return new OpsSkillSemanticSettings(
                semanticEnabled,
                candidateLimit,
                cacheSize);
    }
}
