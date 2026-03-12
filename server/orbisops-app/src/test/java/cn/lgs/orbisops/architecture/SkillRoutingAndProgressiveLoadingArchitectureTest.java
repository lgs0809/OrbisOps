package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRoutingAndProgressiveLoadingArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/skill/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void routingEvaluationMustExposeCompleteConfusionMetrics() throws IOException {
        String policy = read(DOMAIN + "service/SkillRoutingEvaluationPolicy.java");
        String graphPort = read(APPLICATION + "SkillConfusionGraphPort.java");
        String adapter = read(INFRASTRUCTURE + "JdbcSkillConfusionGraphAdapter.java");

        assertAll(
                () -> assertTrue(policy.contains("top1Correct")),
                () -> assertTrue(policy.contains("top3Correct")),
                () -> assertTrue(policy.contains("falsePositives")),
                () -> assertTrue(policy.contains("falseNegatives")),
                () -> assertTrue(policy.contains("shadowing")),
                () -> assertTrue(policy.contains("noSkillTruePositive")),
                () -> assertTrue(policy.contains("NO_SKILL")),
                () -> assertTrue(graphPort.contains("replaceEdges")),
                () -> assertTrue(adapter.contains("ORDER BY (false_positive_count + false_negative_count + shadowing_count) DESC")),
                () -> assertFalse(policy.contains("JdbcTemplate")));
    }

    @Test
    void progressivePackageMustEnforceSectionsAndBudgets() throws IOException {
        String policy = read(DOMAIN + "service/SkillProgressivePackagePolicy.java");
        String sectionType = read(DOMAIN + "model/SkillPackageSectionType.java");
        String assembler = read(APPLICATION + "SkillProgressivePackageAssembler.java");

        assertAll(
                () -> assertTrue(sectionType.contains("ROUTING_SUMMARY")),
                () -> assertTrue(sectionType.contains("CORE_PROCEDURE")),
                () -> assertTrue(sectionType.contains("OPTIONAL_MODULE")),
                () -> assertTrue(sectionType.contains("ARTIFACT")),
                () -> assertTrue(sectionType.contains("EVAL_SUITE")),
                () -> assertTrue(policy.contains("SKILL_PACKAGE_REQUIRED_SECTION_BUDGET_EXCEEDED")),
                () -> assertTrue(policy.contains("maxOptionalModules")),
                () -> assertTrue(policy.contains("maxArtifacts")),
                () -> assertTrue(policy.contains("evaluationMode")),
                () -> assertTrue(assembler.contains("SkillProgressivePackagePolicy")),
                () -> assertFalse(policy.contains("SkillOptimizationMemory")),
                () -> assertFalse(assembler.contains("SkillOptimizationMemory")),
                () -> assertFalse(policy.contains("ChatSession")),
                () -> assertFalse(assembler.contains("RuntimeContextBundle")));
    }

    @Test
    void optimizationMemoryMustRemainOutsideNormalSkillLoading() throws IOException {
        String packageQuery = read(APPLICATION + "SkillPackageQueryService.java");
        String runtimeAssembler = read(APPLICATION + "SkillRuntimeCandidateAssembler.java");
        String context = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/runtime/contextbundle/"
                + "RuntimeContextBundleCreateApplicationService.java");

        assertAll(
                () -> assertFalse(packageQuery.contains("SkillOptimizationMemory")),
                () -> assertFalse(runtimeAssembler.contains("SkillOptimizationMemory")),
                () -> assertFalse(context.contains("SkillOptimizationMemory")),
                () -> assertFalse(packageQuery.contains("ai_ops_skill_optimization_memory")),
                () -> assertFalse(runtimeAssembler.contains("ai_ops_skill_optimization_memory")));
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
