package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEditableWorkspacePort;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OpsSkillCatalogTest {

    @Test
    void disabledCatalogDoesNotResolveLocationsOrEditableWorkspace() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        SkillEditableWorkspacePort workspacePort =
                mock(SkillEditableWorkspacePort.class);
        OpsSkillMarkdownCodec codec = new OpsSkillMarkdownCodec();
        OpsSkillCatalog catalog = new OpsSkillCatalog(
                OpsSkillToolSettings.legacyConstructorDefaults(),
                workspacePort,
                new OpsSkillLocationLoader(resourceLoader),
                codec);

        assertTrue(catalog.loadSkills().isEmpty());
        verify(resourceLoader, never()).getResource(org.mockito.ArgumentMatchers.anyString());
        verify(workspacePort, never()).resolveRoot(org.mockito.ArgumentMatchers.anyString());
    }
}
