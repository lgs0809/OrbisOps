package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRetrievalPipelineArchitectureTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void runtimeSelectionMustRecallBeforeSemanticAndLlmReranking() throws Exception {
        String query = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SelectRuntimeSkillsQuery.java");
        int recall = query.indexOf("recallPolicy.recall(");
        int semantic = query.indexOf("semanticScorePort.scores(");
        int rerank = query.indexOf("rerankPort.scores(");

        assertTrue(recall >= 0);
        assertTrue(semantic > recall);
        assertTrue(rerank > semantic);
        assertTrue(query.contains("llmRerankCandidateLimit()"));
    }

    @Test
    void modelFacingRerankMustNeverReceiveSkillBody() throws Exception {
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/skill/OpsSkillLlmRerankAdapter.java");

        assertTrue(adapter.contains("routingProfile().searchDescription()"));
        assertTrue(adapter.contains("routingProfile().exclusions()"));
        assertFalse(adapter.contains("candidate.content("));
        assertFalse(adapter.contains("SKILL.md content"));
    }

    @Test
    void semanticEmbeddingMustExcludeNegativeRoutingText() throws Exception {
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/skill/OpsSkillSemanticScoreAdapter.java");

        assertTrue(adapter.contains("candidate.retrievalDescriptor()"));
        assertFalse(adapter.contains("candidate.descriptor()"));
    }

    private String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }
}
