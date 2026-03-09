package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionSignalIdPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** UUID adapter for Skill Evolution signal identifiers. */
@Component
public class OpsSkillEvolutionSignalIdentityAdapter implements SkillEvolutionSignalIdPort {

    @Override
    public String newSignalId() {
        return "skill-signal-" + UUID.randomUUID();
    }
}
