package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillOptimizationMemoryBoundaryArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";
    private static final String TRIGGER_RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String TRIGGER_OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void optimizationMemoryMustNotBecomeConversationMemory() throws IOException {
        List<String> optimizationSources = List.of(
                read(APPLICATION + "SkillOptimizationMemoryPort.java"),
                read(APPLICATION + "SkillOptimizationMemoryApplicationService.java"),
                read(APPLICATION + "SkillOptimizationRunApplicationService.java"),
                read(APPLICATION + "SkillDefectDiagnosisApplicationService.java"));
        String llmContext = read(TRIGGER_OPS + "OpsLlmSkillContextService.java");
        String contextBundle = read(APPLICATION.replace("skill/", "runtime/contextbundle/")
                + "RuntimeContextBundleCreateApplicationService.java");

        assertAll(
                () -> optimizationSources.forEach(source -> {
                    assertFalse(source.contains("application.memory"));
                    assertFalse(source.contains("ChatSession"));
                    assertFalse(source.contains("RuntimeContextBundle"));
                    assertFalse(source.contains("Conversation"));
                }),
                () -> assertFalse(llmContext.contains("SkillOptimizationMemory")),
                () -> assertFalse(contextBundle.contains("SkillOptimizationMemory")),
                () -> assertTrue(read(APPLICATION + "SkillOptimizationMemoryPort.java")
                        .contains("not a conversation memory port")));
    }

    @Test
    void optimizationRunMustBeBoundedAndPersistedThroughNarrowPorts() throws IOException {
        String run = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/skill/model/SkillOptimizationRun.java");
        String policy = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/skill/service/SkillOptimizationPolicy.java");
        String adapter = read(INFRASTRUCTURE + "JdbcSkillOptimizationAdapter.java");
        String schema = read(INFRASTRUCTURE + "JdbcSkillOptimizationSchemaInitializer.java");

        assertAll(
                () -> assertTrue(run.contains("maxRounds < 1 || maxRounds > 3")),
                () -> assertTrue(policy.contains("SKILL_OPTIMIZATION_ACTIVE_ROUND_EXISTS")),
                () -> assertTrue(policy.contains("SKILL_OPTIMIZATION_ROUND_LIMIT_REACHED")),
                () -> assertTrue(adapter.contains("implements\n        SkillDefectDiagnosisPort,\n        SkillOptimizationMemoryPort,\n        SkillOptimizationRunPort")),
                () -> assertTrue(schema.contains("ai_ops_skill_defect_diagnosis")),
                () -> assertTrue(schema.contains("ai_ops_skill_optimization_memory")),
                () -> assertTrue(schema.contains("ai_ops_skill_optimization_run")),
                () -> assertFalse(adapter.contains("ChatSession")),
                () -> assertFalse(adapter.contains("MemoryRepository")));
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
