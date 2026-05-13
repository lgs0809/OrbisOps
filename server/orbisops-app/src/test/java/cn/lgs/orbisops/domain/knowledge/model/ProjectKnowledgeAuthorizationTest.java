package cn.lgs.orbisops.domain.knowledge.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectKnowledgeAuthorizationTest {

    @Test
    void normalizesRequiredAuthorizationIdentity() {
        ProjectKnowledgeAuthorization authorization = new ProjectKnowledgeAuthorization(
                " project-1 ", " global-kb ", KnowledgeStatus.ENABLED,
                " alice ", null, null);

        assertEquals("project-1", authorization.projectId());
        assertEquals("global-kb", authorization.globalKbId());
        assertEquals("alice", authorization.enabledBy());
        assertEquals("", authorization.enabledAt());
    }

    @Test
    void rejectsMissingProjectKnowledgeOrOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> ProjectKnowledgeAuthorization.enabled("", "global-kb", "alice"));
        assertThrows(IllegalArgumentException.class,
                () -> ProjectKnowledgeAuthorization.enabled("project-1", "", "alice"));
        assertThrows(IllegalArgumentException.class,
                () -> ProjectKnowledgeAuthorization.enabled("project-1", "global-kb", ""));
    }
}
