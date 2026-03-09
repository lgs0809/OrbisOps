package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.skill.SelectRuntimeSkillsQuery;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillCatalogExecutionDispatchHandlerTest {

    @Test
    void missingFrozenCatalogMustFailClosed() {
        OpsSkillCatalogExecutionDispatchHandler handler = new OpsSkillCatalogExecutionDispatchHandler(
                mock(SkillCatalogQueryService.class), mock(SelectRuntimeSkillsQuery.class));

        assertThrows(SecurityException.class, () ->
                handler.dispatch(target("skill_search"), request(Map.of("query", "diagnose"))));
    }

    @Test
    void binaryArtifactContentMustNotEnterModelContext() {
        SkillCatalogQueryService catalog = mock(SkillCatalogQueryService.class);
        SelectRuntimeSkillsQuery runtimeSkills = mock(SelectRuntimeSkillsQuery.class);
        when(catalog.getSkillVersion(
                "project-1", "skill-1", 2, "skill-hash", "package-hash", "PROJECT"))
                .thenReturn(Map.of("name", "Skill One", "content", "# skill"));
        when(catalog.getSkillArtifact(
                "project-1", "skill-1", 2, "skill-hash", "package-hash", "PROJECT", "assets/model.bin"))
                .thenReturn(Map.of("encoding", "BASE64", "content", "unsafe-binary"));
        OpsSkillCatalogExecutionDispatchHandler handler = new OpsSkillCatalogExecutionDispatchHandler(catalog, runtimeSkills);

        Map<?, ?> output = (Map<?, ?>) handler.dispatch(target("skill_load"), request(Map.of(
                "skillId", "skill-1",
                "artifactPath", "assets/model.bin",
                "catalogRefs", List.of(Map.of(
                        "skillId", "skill-1",
                        "version", 2,
                        "skillHash", "skill-hash",
                        "packageHash", "package-hash",
                        "scope", "PROJECT")))));
        Map<?, ?> artifact = (Map<?, ?>) output.get("artifact");

        assertFalse(artifact.containsKey("content"));
        assertEquals(true, artifact.get("binaryContentAvailable"));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "skill.catalog", toolName, "SKILL", "LOW",
                true, false, false, false, false);
    }

    private ToolExecutionRequest request(Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "skill.catalog", "skill_load",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, arguments,
                "session-1", "run-1", Map.of("projectId", "project-1"), Map.of());
    }
}
