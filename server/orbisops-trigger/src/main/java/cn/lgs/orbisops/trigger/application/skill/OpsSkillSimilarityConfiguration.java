package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.trigger.ops.skill.OpsSkillSimilaritySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSkillSimilarityConfiguration {

    @Bean
    public OpsSkillSimilaritySettings opsSkillSimilaritySettings(
            @Value("${orbisops.skill-evolution.similarity-threshold:0.72}") double threshold) {
        return new OpsSkillSimilaritySettings(threshold);
    }
}
