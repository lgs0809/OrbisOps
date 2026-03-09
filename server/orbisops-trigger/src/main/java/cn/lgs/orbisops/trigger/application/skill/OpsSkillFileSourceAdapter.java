package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillFileDefinition;
import cn.lgs.orbisops.application.skill.SkillFileSourcePort;
import cn.lgs.orbisops.trigger.ops.skill.SkillCatalogReader;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public final class OpsSkillFileSourceAdapter implements SkillFileSourcePort {

    private final ObjectProvider<SkillCatalogReader> provider;

    public OpsSkillFileSourceAdapter(ObjectProvider<SkillCatalogReader> provider) {
        if (provider == null) throw new IllegalArgumentException("SKILL_TOOL_PROVIDER_REQUIRED");
        this.provider = provider;
    }

    @Override
    public List<SkillFileDefinition> findAll() {
        SkillCatalogReader current = provider.getIfAvailable();
        return current == null ? List.of() : current.loadSkills().stream()
                .map(skill -> definition(current, skill))
                .toList();
    }

    @Override
    public Optional<SkillFileDefinition> findById(String skillId) {
        SkillCatalogReader current = provider.getIfAvailable();
        if (current == null) return Optional.empty();
        return current.findSkill(skillId).map(skill -> definition(current, skill));
    }

    private SkillFileDefinition definition(SkillCatalogReader current, SkillsTool.Skill skill) {
        Map<String, Object> frontMatter = skill == null || skill.frontMatter() == null
                ? Map.of() : skill.frontMatter();
        return new SkillFileDefinition(
                skill.name(), skill.basePath(), frontMatter, skill.content(),
                current.toMarkdown(skill), skill.toXml());
    }
}
