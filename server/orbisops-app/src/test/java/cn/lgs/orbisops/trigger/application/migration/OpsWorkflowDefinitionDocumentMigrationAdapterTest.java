package cn.lgs.orbisops.trigger.application.migration;

import cn.lgs.orbisops.application.migration.WorkflowDefinitionDocumentMigrationPort;
import cn.lgs.orbisops.trigger.application.agentdefinition.AgentWorkflowDefinitionMigrator;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWorkflowDefinitionDocumentMigrationAdapterTest {

    private final OpsWorkflowDefinitionDocumentMigrationAdapter adapter =
            new OpsWorkflowDefinitionDocumentMigrationAdapter(
                    new AgentWorkflowDefinitionMigrator(),
                    new OpsAgentDefinitionSnapshotMapper());

    @Test
    void legacyDocumentMustUseAuthoritativeMigratorAndProduceCanonicalHash() {
        OpsAgentDefinition legacy = OpsAgentDefinition.builder()
                .agentId("legacy")
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("plan")
                        .type("PLAN")
                        .build()))
                .build();

        WorkflowDefinitionDocumentMigrationPort.MigrationDocument migrated =
                adapter.migrate(JSON.toJSONString(legacy));

        assertFalse(migrated.current());
        assertFalse(migrated.manualReviewRequired());
        assertEquals("WORKFLOW_SCHEMA_V0_MIGRATABLE", migrated.reasonCode());
        assertTrue(migrated.definitionHash().matches("[a-f0-9]{64}"));
        OpsAgentDefinition result = JSON.parseObject(
                migrated.migratedJson(), OpsAgentDefinition.class);
        assertEquals(1, result.getSchemaVersion());
        assertEquals("AGENT", result.getNodes().get(0).getType());
        assertEquals("plan", result.getNodes().get(0).getMode());
    }

    @Test
    void malformedAndFutureDocumentsMustRequireManualReview() {
        assertTrue(adapter.migrate("not-json").manualReviewRequired());
        OpsAgentDefinition future = OpsAgentDefinition.builder()
                .agentId("future")
                .schemaVersion(2)
                .nodes(List.of())
                .build();

        WorkflowDefinitionDocumentMigrationPort.MigrationDocument result =
                adapter.migrate(JSON.toJSONString(future));

        assertTrue(result.manualReviewRequired());
        assertEquals("WORKFLOW_DEFINITION_JSON_UNSUPPORTED", result.reasonCode());
    }
}
