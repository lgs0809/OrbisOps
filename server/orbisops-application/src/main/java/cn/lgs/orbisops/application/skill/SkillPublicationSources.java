package cn.lgs.orbisops.application.skill;

import java.util.Map;

/** Authorized, version-checked frozen input for content review; values remain untrusted evidence. */
public record SkillPublicationSources(Map<String,Object> input) {
    public SkillPublicationSources { input=Map.copyOf(input); }
}
