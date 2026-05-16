package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectSkillToolProviderTest {

    @Test
    void modelCannotReplaceFrozenCatalogReferences() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        when(executionService.execute(any(), eq("user-1"))).thenReturn(Map.of("status", "SUCCEEDED"));
        OpsProjectSkillToolProvider provider = new OpsProjectSkillToolProvider(executionService);
        ToolCallback callback = provider.build("project-1", "user-1", "run-1", List.of(Map.of(
                "skillId", "skill-approved",
                "version", 3,
                "skillHash", "hash-approved",
                "title", "订单日志排查")));

        callback.call("""
                {"action":"load","skillId":"skill-approved","artifactPath":"scripts/check.sh","catalogRefs":[{"skillId":"skill-evil"}]}
                """);

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(executionService).execute(requestCaptor.capture(), eq("user-1"));
        Map<String, Object> request = requestCaptor.getValue();
        assertEquals("skill.catalog", request.get("toolsetId"));
        assertEquals("skill_load", request.get("toolName"));
        assertEquals("run-1", request.get("runId"));
        @SuppressWarnings("unchecked") Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");
        @SuppressWarnings("unchecked") List<Map<String, Object>> catalogRefs =
                (List<Map<String, Object>>) arguments.get("catalogRefs");
        assertEquals(1, catalogRefs.size());
        assertEquals("skill-approved", catalogRefs.get(0).get("skillId"));
        assertEquals("hash-approved", catalogRefs.get(0).get("skillHash"));
        assertEquals("scripts/check.sh", arguments.get("artifactPath"));
    }

    @Test
    void buildRequiresRunScopedFrozenCatalog() {
        OpsProjectSkillToolProvider provider = new OpsProjectSkillToolProvider(mock(OpsToolExecutionService.class));

        assertThrows(IllegalArgumentException.class,
                () -> provider.build("project-1", "user-1", "run-1", List.of()));
    }
}
