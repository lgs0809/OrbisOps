package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillHintAndMultiCandidateArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/skill/";

    @Test
    void hintSelectionMustBeStableEvidenceAwareAndRepositoryIndependent() throws IOException {
        String selector = read(APPLICATION + "SkillEvolutionHintSelector.java");

        assertAll(
                () -> assertTrue(selector.contains("MMR_DUPLICATION_PENALTY")),
                () -> assertTrue(selector.contains("SOURCE_DIVERSITY_BONUS")),
                () -> assertTrue(selector.contains("HARD_CASE_QUOTA")),
                () -> assertTrue(selector.contains("evidenceQuality")),
                () -> assertTrue(selector.contains("ScoredHint::baseScore")),
                () -> assertTrue(selector.contains("thenComparing(item -> item.hint().hintId())")),
                () -> assertTrue(selector.contains("novel(item, selected)")),
                () -> assertFalse(selector.contains("Repository")),
                () -> assertFalse(selector.contains("JdbcTemplate")),
                () -> assertFalse(selector.contains("findPendingHints")));
    }

    @Test
    void authoringDefaultsToOneAuditedProposalAndKeepsExplicitBoundedComparisons() throws IOException {
        String port = read(APPLICATION + "SkillEvolutionAuthoringPort.java");
        String service = read(APPLICATION + "SkillEvolutionMultiCandidateAuthoringService.java");
        String audit = read(APPLICATION + "SkillEvolutionAuthoringAudit.java");
        String pipeline = read(APPLICATION + "SkillEvolutionApplicationService.java");

        assertAll(
                () -> assertTrue(port.contains("SkillEvolutionAuthoredCandidate author(")),
                () -> assertTrue(port.contains("default List<SkillEvolutionAuthoredCandidate> authorCandidates")),
                () -> assertTrue(service.contains("DEFAULT_BUDGET = 1")),
                () -> assertTrue(service.contains("Math.max(1, Math.min(4, requestedBudget))")),
                () -> assertFalse(service.contains("fallbackAuthor")),
                () -> assertTrue(service.contains("CanonicalObjectHasher.sha256(safeInput)")),
                () -> assertTrue(service.contains("candidateDirections")),
                () -> assertTrue(audit.contains("modelId")),
                () -> assertTrue(audit.contains("promptVersion")),
                () -> assertTrue(audit.contains("seed")),
                () -> assertTrue(audit.contains("inputHash")),
                () -> assertTrue(pipeline.contains("SkillEvolutionMultiCandidateAuthoringService")),
                () -> assertTrue(pipeline.contains("candidateSetAudits")),
                () -> assertTrue(pipeline.contains("productionWriteAllowed\", false")),
                () -> assertTrue(pipeline.contains("READ_ONLY_OR_SANDBOX")),
                () -> assertFalse(service.contains("ToolExecutionApplicationService")),
                () -> assertFalse(service.contains("ChangePackageLanding")));
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
