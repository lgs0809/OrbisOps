package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillProjectValidationAdapterTest {

    @Test
    void existingProjectPassesValidation() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        when(projects.exists("project-1")).thenReturn(true);
        OpsSkillProjectValidationAdapter adapter = new OpsSkillProjectValidationAdapter(projects);

        assertDoesNotThrow(() -> adapter.requireExisting(" project-1 "));
    }

    @Test
    void missingProjectFailsWithStableMessage() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        OpsSkillProjectValidationAdapter adapter = new OpsSkillProjectValidationAdapter(projects);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> adapter.requireExisting("missing"));

        assertEquals("项目不存在：missing", error.getMessage());
    }
}
