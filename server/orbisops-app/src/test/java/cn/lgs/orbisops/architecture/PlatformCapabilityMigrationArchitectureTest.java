package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformCapabilityMigrationArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/migration/";
    private static final String REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TOOLSET = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/toolset/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";

    @Test
    void migrationMustCoverAllFiveCompatibilitySteps() throws IOException {
        String steps = read(APPLICATION + "PlatformCapabilityMigrationStep.java");
        String config = read(TRIGGER
                + "application/migration/OpsPlatformCapabilityMigrationConfiguration.java");
        String orchestrator = read(APPLICATION + "PlatformCapabilityMigrationOrchestrator.java");
        String tool = read(TOOLSET + "JdbcToolDefinitionSemanticsMigrationContributor.java");
        String workflow = read(REPOSITORY + "JdbcWorkflowTypedDefinitionMigrationContributor.java");
        String mcp = read(REPOSITORY + "JdbcMcpRuntimeSourceMigrationContributor.java");
        String bound = read(REPOSITORY + "JdbcBoundWorkflowSnapshotMigrationContributor.java");

        assertAll(
                () -> assertTrue(steps.contains("SKILL_GOVERNANCE_STATE")),
                () -> assertTrue(steps.contains("TOOL_DEFINITION_SEMANTICS_PROVIDER")),
                () -> assertTrue(steps.contains("WORKFLOW_TYPED_DEFINITION")),
                () -> assertTrue(steps.contains("MCP_RUNTIME_SOURCE_CHAIN")),
                () -> assertTrue(steps.contains("BOUND_WORKFLOW_SNAPSHOT")),
                () -> assertTrue(tool.contains(
                        "PlatformCapabilityMigrationStep.TOOL_DEFINITION_SEMANTICS_PROVIDER")),
                () -> assertTrue(workflow.contains(
                        "PlatformCapabilityMigrationStep.WORKFLOW_TYPED_DEFINITION")),
                () -> assertTrue(mcp.contains(
                        "PlatformCapabilityMigrationStep.MCP_RUNTIME_SOURCE_CHAIN")),
                () -> assertTrue(bound.contains(
                        "PlatformCapabilityMigrationStep.BOUND_WORKFLOW_SNAPSHOT")),
                () -> assertTrue(config.contains("List<PlatformCapabilityMigrationContributor>")),
                () -> assertTrue(orchestrator.contains("EnumMap<PlatformCapabilityMigrationStep")),
                () -> assertTrue(orchestrator.contains("PLATFORM_MIGRATION_CONTRIBUTOR_DUPLICATE")),
                () -> assertTrue(orchestrator.contains("PlatformCapabilityMigrationStatus.DRY_RUN_READY")),
                () -> assertTrue(orchestrator.contains("PlatformCapabilityMigrationStatus.MANUAL_REVIEW_REQUIRED")));
    }

    @Test
    void compatibilityContractsMustInspectRealPersistentData() throws IOException {
        String skill = read(REPOSITORY + "JdbcSkillGovernanceMigrationContributor.java");
        String tool = read(TOOLSET + "JdbcToolDefinitionSemanticsMigrationContributor.java");
        String workflow = read(REPOSITORY + "JdbcWorkflowTypedDefinitionMigrationContributor.java");
        String mcp = read(REPOSITORY + "JdbcMcpRuntimeSourceMigrationContributor.java");
        String bound = read(REPOSITORY + "JdbcBoundWorkflowSnapshotMigrationContributor.java");

        assertAll(
                () -> assertTrue(skill.contains("mutation_mode='LOCKED'")),
                () -> assertTrue(skill.contains("execution_mode='QUARANTINED'")),
                () -> assertTrue(skill.contains("legacy_frozen_classification_required=1")),
                () -> assertTrue(skill.contains("SKILL_LEGACY_FROZEN_BACKFILLED_FAIL_CLOSED")),
                () -> assertTrue(tool.contains("FROM ai_ops_toolset")),
                () -> assertTrue(tool.contains("migrationReviewRequired")),
                () -> assertTrue(tool.contains("SET tools_json=?, enabled=0")),
                () -> assertTrue(workflow.contains("ai_ops_agent_definition_version")),
                () -> assertTrue(workflow.contains("documents.migrate")),
                () -> assertTrue(workflow.contains("WORKFLOW_DEFINITION_CAS_DRIFT")),
                () -> assertTrue(mcp.contains("ai_ops_project_mcp")),
                () -> assertTrue(mcp.contains("ai_client_tool_mcp")),
                () -> assertTrue(bound.contains("ai_ops_agent_run_checkpoint")),
                () -> assertTrue(bound.contains("TYPED_WORKFLOW_%")));
    }

    @Test
    void migrationMustNotContractOrDeleteLegacyData() throws IOException {
        String combined = (read(APPLICATION + "PlatformCapabilityMigrationOrchestrator.java")
                + read(REPOSITORY + "JdbcSkillGovernanceMigrationContributor.java")
                + read(TOOLSET + "JdbcToolDefinitionSemanticsMigrationContributor.java")
                + read(REPOSITORY + "JdbcWorkflowTypedDefinitionMigrationContributor.java")
                + read(REPOSITORY + "JdbcMcpRuntimeSourceMigrationContributor.java")
                + read(REPOSITORY + "JdbcBoundWorkflowSnapshotMigrationContributor.java")
                + read(TRIGGER
                + "application/migration/OpsPlatformCapabilityMigrationConfiguration.java"))
                .toLowerCase();

        assertAll(
                () -> assertFalse(combined.contains("delete from")),
                () -> assertFalse(combined.contains("drop table")),
                () -> assertFalse(combined.contains("drop column")),
                () -> assertFalse(combined.contains("truncate table")),
                () -> assertFalse(combined.contains("git ")),
                () -> assertTrue(combined.contains("dry_run_ready")),
                () -> assertTrue(combined.contains("manual_review_required")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
