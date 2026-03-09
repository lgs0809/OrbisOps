package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.trigger.ops.skill.OpsSkillToolSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSkillToolConfiguration {

    @Bean
    public OpsSkillToolSettings opsSkillToolSettings(
            @Value("${ops.skills.enabled:true}") boolean enabled,
            @Value("${ops.skills.locations:classpath:/skills}") String locations,
            @Value("${ops.skills.editable-location:./data/skills}") String editableLocation,
            @Value("${ops.skills.editable-auto-init:true}") boolean editableAutoInit) {
        return OpsSkillToolSettings.fromRaw(
                enabled,
                locations,
                editableLocation,
                editableAutoInit);
    }
}
