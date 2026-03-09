package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsBuiltInToolsetCatalogTest {

    private final List<OpsToolsetDefinition> catalog =
            new OpsBuiltInToolsetCatalog().definitions();

    @Test
    void catalogMustPreserveCanonicalToolsetCountAndUniqueIds() {
        assertEquals(30, catalog.size());
        Set<String> ids = new HashSet<>();
        for (OpsToolsetDefinition toolset : catalog) {
            assertTrue(ids.add(toolset.getToolsetId()),
                    () -> "duplicate toolset id: " + toolset.getToolsetId());
            assertEquals("BUILT_IN", toolset.getSourceType());
            assertTrue(toolset.isEnabled());
            assertFalse(toolset.getTools().isEmpty());
            assertEquals(toolset.getToolsetId().split("\\.")[0],
                    toolset.getTags().get(0));
            Set<String> toolNames = new HashSet<>();
            for (OpsToolDefinition tool : toolset.getTools()) {
                assertTrue(toolNames.add(tool.getToolName()),
                        () -> "duplicate tool in " + toolset.getToolsetId()
                                + ": " + tool.getToolName());
                assertTrue(tool.isEnabled());
            }
        }
    }

    @Test
    void representativeReadValidationRepairAndWorkflowDefinitionsMustRemainStable() {
        OpsToolsetDefinition prometheus = find("observability.prometheus");
        OpsToolsetDefinition mysql = find("db.mysql.readonly");
        OpsToolsetDefinition repair = find("code.repair");
        OpsToolsetDefinition inspection = find("inspection.task");

        assertEquals("LOCAL_PROMETHEUS", prometheus.getAdapterType());
        assertEquals("LOCAL_PROMETHEUS",
                tool(prometheus, "prometheus_instant_query").getAdapterType());
        assertTrue(prometheus.isReadOnlyDefault());
        assertEquals(List.of(
                        "prometheus_instant_query",
                        "prometheus_range_query",
                        "prometheus_label_values",
                        "prometheus_series_query"),
                names(prometheus));

        OpsToolDefinition mysqlDryRun = tool(mysql, "mysql_sql_dry_run");
        assertEquals("LOCAL_VALIDATION", mysqlDryRun.getAdapterType());
        assertFalse(mysqlDryRun.isReadOnly());
        assertEquals("MEDIUM", mysqlDryRun.getRiskLevel());

        OpsToolDefinition codeEdit = tool(repair, "code_edit");
        assertTrue(codeEdit.isWritesRepairWorkspace());
        assertFalse(codeEdit.isWritesTargetResource());
        assertEquals("MEDIUM", codeEdit.getRiskLevel());

        assertEquals("INSPECTION_TASK",
                tool(inspection, "inspection_task_list").getAdapterType());
        assertEquals("INSPECTION_TASK",
                tool(inspection, "inspection_task_create").getAdapterType());
        assertEquals("LOW",
                tool(inspection, "inspection_task_create").getRiskLevel());

        assertFalse(catalog.stream().anyMatch(item -> item.getToolsetId().endsWith(".landing")));
    }

    @Test
    void targetWriteCatalogMustRemainApprovalGated() {
        for (String id : List.of(
                "infra.k8s.remediation",
                "config.nacos.publish",
                "db.mysql.change",
                "cache.redis.change",
                "cicd.deploy",
                "release.platform.execute",
                "job.platform.execute",
                "deployment.local-java")) {
            OpsToolDefinition tool = find(id).getTools().get(0);
            assertFalse(tool.isReadOnly());
            assertTrue(tool.isWritesTargetResource());
            assertTrue(tool.isRequiresChangePackage());
            assertTrue(tool.isRequiresApproval());
            assertEquals("HIGH", tool.getRiskLevel());
        }
    }

    private OpsToolsetDefinition find(String id) {
        return catalog.stream()
                .filter(toolset -> id.equals(toolset.getToolsetId()))
                .findFirst()
                .orElseThrow();
    }

    private OpsToolDefinition tool(
            OpsToolsetDefinition toolset,
            String name) {
        return toolset.getTools().stream()
                .filter(tool -> name.equals(tool.getToolName()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> names(OpsToolsetDefinition toolset) {
        return toolset.getTools().stream()
                .map(OpsToolDefinition::getToolName)
                .toList();
    }
}
