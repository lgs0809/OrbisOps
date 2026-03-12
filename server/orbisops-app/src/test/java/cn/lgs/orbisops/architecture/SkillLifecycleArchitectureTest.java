package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLifecycleArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void lifecycleMustExposeAllOperationsAndRetentionStates() throws IOException {
        String operations = read(DOMAIN + "model/SkillLifecycleOperation.java");
        String retention = read(DOMAIN + "model/SkillRetentionState.java");
        String policy = read(DOMAIN + "service/SkillLifecyclePolicy.java");

        assertAll(
                () -> assertTrue(operations.contains("CREATE")),
                () -> assertTrue(operations.contains("PATCH")),
                () -> assertTrue(operations.contains("MERGE")),
                () -> assertTrue(operations.contains("SPLIT")),
                () -> assertTrue(operations.contains("COMPRESS")),
                () -> assertTrue(operations.contains("DEPRECATE")),
                () -> assertTrue(operations.contains("PRUNE")),
                () -> assertTrue(operations.contains("REVIVE")),
                () -> assertTrue(operations.contains("ROLLBACK")),
                () -> assertTrue(retention.contains("ACTIVE")),
                () -> assertTrue(retention.contains("DEPRECATED")),
                () -> assertTrue(retention.contains("HIDDEN_FROM_ROUTING")),
                () -> assertTrue(retention.contains("RETAINED_FOR_ROLLBACK")),
                () -> assertTrue(retention.contains("PURGE_ELIGIBLE")),
                () -> assertTrue(policy.contains(
                        "SKILL_LIFECYCLE_SEALED_IN_PLACE_MUTATION_FORBIDDEN")),
                () -> assertTrue(policy.contains(
                        "SKILL_LIFECYCLE_BEHAVIOR_REPLAY_REQUIRED")),
                () -> assertTrue(policy.contains("SkillRetentionState.DEPRECATED")),
                () -> assertTrue(policy.contains("SkillRetentionState.HIDDEN_FROM_ROUTING")),
                () -> assertTrue(policy.contains("SkillRetentionState.RETAINED_FOR_ROLLBACK")),
                () -> assertTrue(policy.contains("SkillRetentionState.PURGE_ELIGIBLE")));
    }

    @Test
    void lifecyclePersistenceMustBeAppendAuditWithoutPhysicalDelete() throws IOException {
        String port = read(APPLICATION + "SkillLifecyclePort.java");
        String service = read(APPLICATION + "SkillLifecycleApplicationService.java");
        String adapter = read(INFRASTRUCTURE + "JdbcSkillLifecycleAdapter.java");
        String schema = read(INFRASTRUCTURE + "JdbcSkillLifecycleSchemaInitializer.java");
        String combined = (port + service + adapter).toLowerCase();

        assertAll(
                () -> assertTrue(port.contains("saveProposal")),
                () -> assertTrue(port.contains("saveDecision")),
                () -> assertTrue(port.contains("saveLineage")),
                () -> assertTrue(service.indexOf("port.saveProposal(proposal)")
                        < service.indexOf("port.saveDecision(decision)")),
                () -> assertTrue(service.contains(
                        "if (decision.approved() && !decision.lineageEdges().isEmpty())")),
                () -> assertTrue(adapter.contains("ai_ops_skill_lifecycle_proposal")),
                () -> assertTrue(adapter.contains("ai_ops_skill_lifecycle_decision")),
                () -> assertTrue(adapter.contains("ai_ops_skill_lineage_edge")),
                () -> assertTrue(schema.contains("ai_ops_skill_lifecycle_proposal")),
                () -> assertTrue(schema.contains("ai_ops_skill_lifecycle_decision")),
                () -> assertTrue(schema.contains("ai_ops_skill_lineage_edge")),
                () -> assertFalse(combined.contains("delete from")),
                () -> assertFalse(combined.contains("delete(")),
                () -> assertFalse(combined.contains("removeskill")),
                () -> assertFalse(combined.contains("void purge")));
    }

    @Test
    void lifecycleMustRemainOutsideConversationAndRuntimeContext() throws IOException {
        String policy = read(DOMAIN + "service/SkillLifecyclePolicy.java");
        String service = read(APPLICATION + "SkillLifecycleApplicationService.java");
        String port = read(APPLICATION + "SkillLifecyclePort.java");
        String combined = policy + service + port;

        assertAll(
                () -> assertFalse(combined.contains("ChatSession")),
                () -> assertFalse(combined.contains("RuntimeContextBundle")),
                () -> assertFalse(combined.contains("SkillOptimizationMemory")),
                () -> assertFalse(combined.contains("ToolExecutionApplicationService")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(service.contains("JdbcTemplate")));
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
