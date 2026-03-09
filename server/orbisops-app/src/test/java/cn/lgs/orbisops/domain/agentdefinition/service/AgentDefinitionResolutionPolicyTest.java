package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefinitionResolutionPolicyTest {

    private final AgentDefinitionResolutionPolicy policy = new AgentDefinitionResolutionPolicy();

    @Test
    void shouldHideDraftAndValidatedVersionsOutsidePreview() {
        AgentDefinitionVersionState draft = state("agent-a", 2, "project-a", AgentDefinitionLifecycle.DRAFT);
        AgentDefinitionVersionState validated = state("agent-a", 3, "project-a", AgentDefinitionLifecycle.VALIDATED);

        assertThrows(IllegalArgumentException.class, () -> policy.assertVisible(draft, true, false));
        assertThrows(IllegalArgumentException.class, () -> policy.assertVisible(validated, true, false));
        assertDoesNotThrow(() -> policy.assertVisible(draft, true, true));
    }

    @Test
    void shouldRejectDisabledDefinitionForCurrentAndExplicitResolution() {
        AgentDefinitionVersionState disabled = state("agent-a", 1, "project-a", AgentDefinitionLifecycle.DISABLED);

        assertThrows(IllegalArgumentException.class, () -> policy.assertVisible(disabled, false, false));
        assertThrows(IllegalArgumentException.class, () -> policy.assertVisible(disabled, true, true));
    }

    @Test
    void shouldRejectPlatformTemplateAndCrossProjectExecution() {
        AgentDefinitionVersionState template = state("template", 1, "", AgentDefinitionLifecycle.PUBLISHED);
        AgentDefinitionVersionState foreign = state("foreign", 1, "project-b", AgentDefinitionLifecycle.PUBLISHED);

        assertThrows(IllegalArgumentException.class,
                () -> policy.assertRunnableInProject(template, "project-a"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertRunnableInProject(foreign, "project-a"));
    }

    @Test
    void shouldRecognizeProjectOwnership() {
        AgentDefinitionVersionState owned = state("agent-a", 1, "project-a", AgentDefinitionLifecycle.PUBLISHED);

        assertTrue(policy.belongsToProject(owned, " project-a "));
        assertDoesNotThrow(() -> policy.assertRunnableInProject(owned, "project-a"));
    }

    private AgentDefinitionVersionState state(String agentId,
                                               int version,
                                               String projectId,
                                               AgentDefinitionLifecycle lifecycle) {
        return new AgentDefinitionVersionState(agentId, version, "hash-" + version, projectId, lifecycle);
    }
}
