package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.trigger.ops.skill.OpsSkillToolSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSkillToolConfiguration {

    @Bean
    public OpsSkillToolSettings opsSkillToolSettings(
            @Value("${orbisops.skills.enabled:true}") boolean enabled,
            @Value("${orbisops.skills.locations:classpath:/skills}") String locations,
            @Value("${orbisops.skills.editable-location:./data/skills}") String editableLocation,
            @Value("${orbisops.skills.editable-auto-init:true}") boolean editableAutoInit) {
        return OpsSkillToolSettings.fromRaw(
                enabled,
                locations,
                editableLocation,
                editableAutoInit);
    }
}
