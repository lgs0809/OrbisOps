package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillFileDefinition;
import cn.lgs.orbisops.trigger.ops.skill.SkillCatalogReader;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillFileSourceAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void returnsEmptySourceWhenProviderIsUnavailable() {
        ObjectProvider<SkillCatalogReader> provider = mock(ObjectProvider.class);
        OpsSkillFileSourceAdapter adapter = new OpsSkillFileSourceAdapter(provider);

        assertTrue(adapter.findAll().isEmpty());
        assertTrue(adapter.findById("diagnosis").isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertsExternalSkillSdkObjectToTransportNeutralSnapshot() {
        SkillCatalogReader skillProvider = mock(SkillCatalogReader.class);
        ObjectProvider<SkillCatalogReader> provider = mock(ObjectProvider.class);
        SkillsTool.Skill skill = mock(SkillsTool.Skill.class);
        when(provider.getIfAvailable()).thenReturn(skillProvider);
        when(skill.name()).thenReturn("diagnosis");
        when(skill.basePath()).thenReturn("/skills/diagnosis");
        when(skill.frontMatter()).thenReturn(Map.of(
                "name", "diagnosis", "scope", "PROJECT", "projectId", "p1"));
        when(skill.content()).thenReturn("# Diagnosis");
        when(skill.toXml()).thenReturn("<skill/>");
        when(skillProvider.toMarkdown(skill)).thenReturn("---\nname: diagnosis\n---\n\n# Diagnosis\n");
        when(skillProvider.loadSkills()).thenReturn(List.of(skill));
        when(skillProvider.findSkill("diagnosis")).thenReturn(Optional.of(skill));
        OpsSkillFileSourceAdapter adapter = new OpsSkillFileSourceAdapter(provider);

        SkillFileDefinition listed = adapter.findAll().get(0);
        SkillFileDefinition found = adapter.findById("diagnosis").orElseThrow();

        assertEquals("diagnosis", listed.name());
        assertEquals("PROJECT", listed.frontMatter().get("scope"));
        assertEquals("# Diagnosis", listed.content());
        assertEquals("<skill/>", found.xml());
    }
}
