package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.CustomToolsetRecord;
import cn.lgs.orbisops.application.toolset.CustomToolsetStoreApplicationService;
import cn.lgs.orbisops.application.toolset.CustomToolsetStorePort;
import cn.lgs.orbisops.domain.toolset.service.ToolsetPolicy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsCustomToolsetServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void dangerousCustomCommandIsAutomaticallyChangePackageGatedByDomainPolicy() {
        Map<String, Object> normalized = new ToolsetPolicy().customToolset("project-1", Map.of(
                "toolsetId", "custom.k8s",
                "name", "custom k8s",
                "adapterType", "CONTROLLED_COMMAND",
                "tools", List.of(Map.of(
                        "name", "patch_prod",
                        "commandTemplate", "kubectl patch deployment app",
                        "readOnly", true))), "generated-id");

        Map<String, Object> tool = ((List<Map<String, Object>>) normalized.get("tools")).get(0);
        assertFalse((Boolean) tool.get("readOnly"));
        assertTrue((Boolean) tool.get("writesTargetResource"));
        assertTrue((Boolean) tool.get("requiresChangePackage"));
        assertTrue((Boolean) tool.get("requiresApproval"));
        assertEquals("HIGH", tool.get("riskLevel"));
    }

    @Test
    void memoryFallbackKeepsProjectScopeIndependentFromPrerequisites() {
        InMemoryCustomToolsetStore store = new InMemoryCustomToolsetStore();
        OpsCustomToolsetService service = new OpsCustomToolsetService(
                new CustomToolsetStoreApplicationService(store));
        Map<String, Object> normalized = new ToolsetPolicy().customToolset("project-1", Map.of(
                "toolsetId", "custom.local",
                "name", "local toolset",
                "prerequisites", "docker,kubectl",
                "tools", List.of()), "generated-id");

        service.registerCustomToolset("project-1", normalized, "alice");

        assertEquals(1, service.listCustomToolsets("project-1").size());
        assertTrue(service.listCustomToolsets("project-2").isEmpty());
    }

    private static final class InMemoryCustomToolsetStore
            implements CustomToolsetStorePort {

        private final Map<String, CustomToolsetRecord> records =
                new LinkedHashMap<>();

        @Override
        public List<CustomToolsetRecord> list(String projectId) {
            return records.values().stream()
                    .filter(record -> projectId.equals(record.projectId()))
                    .toList();
        }

        @Override
        public CustomToolsetRecord upsert(CustomToolsetRecord record) {
            records.put(key(record.projectId(), record.toolsetId()), record);
            return record;
        }

        @Override
        public void setEnabled(
                String projectId,
                String toolsetId,
                boolean enabled,
                String actor) {
            records.computeIfPresent(
                    key(projectId, toolsetId),
                    (ignored, record) -> record.withEnabled(enabled, actor));
        }

        private String key(String projectId, String toolsetId) {
            return projectId + ":" + toolsetId;
        }
    }
}
