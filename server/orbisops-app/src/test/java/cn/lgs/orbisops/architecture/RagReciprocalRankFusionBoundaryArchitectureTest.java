package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagReciprocalRankFusionBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String FUSION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagReciprocalRankFusion.java";

    @Test
    void stableGroupingScoreMergeAndCandidateLimitMustRemainInsideFusionBoundary() throws IOException {
        String fusion = read(FUSION);

        assertAll(
                () -> assertTrue(fusion.contains("class RagReciprocalRankFusion")),
                () -> assertTrue(fusion.contains("RagRankedDocument::key")),
                () -> assertTrue(fusion.contains("LinkedHashMap::new")),
                () -> assertTrue(fusion.contains("mapToDouble(RagRankedDocument::score)")),
                () -> assertTrue(fusion.contains("retrieval_sources")),
                () -> assertTrue(fusion.contains("distinct()")),
                () -> assertTrue(fusion.contains("Collectors.joining(\",\")")),
                () -> assertTrue(fusion.contains("retrieval_score")),
                () -> assertTrue(fusion.contains("Comparator.comparingDouble(RagRankedDocument::score).reversed()")),
                () -> assertTrue(fusion.contains("plan.rerankEnabled()")),
                () -> assertTrue(fusion.contains("plan.rerankCandidateTopK()")),
                () -> assertTrue(fusion.contains("plan.finalTopK()")),
                () -> assertFalse(fusion.contains("IRagKnowledgeRepository")),
                () -> assertFalse(fusion.contains("VectorStore")),
                () -> assertFalse(fusion.contains("RagMultimodalEmbeddingService")),
                () -> assertFalse(fusion.contains("qa_mmr")),
                () -> assertFalse(fusion.contains("rerank_score")),
                () -> assertFalse(fusion.contains("HttpClient")),
                () -> assertFalse(fusion.contains("com.alibaba.fastjson")),
                () -> assertFalse(fusion.contains("ChatClientRequest")));
    }

    @Test
    void advisorMustDelegateFusionWithoutOwningGroupingOrMergedMetadata() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagRetrievalOrchestrator retrievalOrchestrator")),
                () -> assertTrue(advisor.contains("retrievalOrchestrator.retrieve")),
                () -> assertFalse(advisor.contains("RagReciprocalRankFusion reciprocalRankFusion")),
                () -> assertFalse(advisor.contains("reciprocalRankFusion.fuse")),
                () -> assertFalse(advisor.contains("fusedCandidates")),
                () -> assertFalse(advisor.contains("mergeRankedDocuments")),
                () -> assertFalse(advisor.contains("groupingBy(RagRankedDocument::key")),
                () -> assertFalse(advisor.contains("retrieval_sources")),
                () -> assertFalse(advisor.contains("metadata.put(\"retrieval_score\"")),
                () -> assertFalse(advisor.contains("mmrDiversitySelector.select")),
                () -> assertFalse(advisor.contains("rerankProtocol.rerank")),
                () -> assertTrue(advisor.lines().count() < 520));
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
