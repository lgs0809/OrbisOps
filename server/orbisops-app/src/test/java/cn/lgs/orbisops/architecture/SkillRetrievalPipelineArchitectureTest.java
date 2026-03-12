package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRetrievalPipelineArchitectureTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void runtimeSelectionMustRecallIndependentlyBeforeFusionAndReranking() throws Exception {
        String query = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SelectRuntimeSkillsQuery.java");
        String hybrid = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/skill/SkillHybridRetrieval.java");
        int recall = hybrid.indexOf("lexical.recall(query,authorized,");
        int semantic = hybrid.indexOf("semantic.scores(projectId,query,authorized)");
        int fusion = hybrid.indexOf("policy.fuse(words,vectors,exact)");
        int rerank = hybrid.indexOf("reranker.scores(query,documents)");
        assertTrue(query.contains("new SkillHybridRetrieval("));
        assertTrue(recall >= 0);
        assertTrue(semantic > recall);
        assertTrue(fusion > semantic);
        assertTrue(rerank > fusion);
        assertFalse(hybrid.contains("semantic.scores(projectId,query,words)"));
    }

    @Test
    void modelFacingRerankMustNeverReceiveSkillBody() throws Exception {
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/skill/OpsSkillLlmRerankAdapter.java");

        String projection = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/skill/service/SkillRouteProjectionIdentity.java");
        assertTrue(adapter.contains("SkillRouteProjectionIdentity.rerankDocument(c)"));
        assertTrue(projection.contains("routingProfile().searchDescription()"));
        assertTrue(projection.contains("routingProfile().exclusions()"));
        assertFalse(adapter.contains("candidate.content("));
        assertFalse(adapter.contains("SKILL.md content"));
        assertFalse(adapter.contains("OpsAgentLlmClient"));
        assertFalse(projection.contains(".content()"));
    }

    @Test
    void semanticEmbeddingMustExcludeNegativeRoutingText() throws Exception {
        String projection = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/skill/service/SkillRouteProjectionIdentity.java");
        String worker = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/skill/OpsSkillRouteProjectionWorker.java");
        assertTrue(projection.contains("bounded(c.retrievalDescriptor(),300)"));
        assertFalse(projection.contains("c.descriptor()"));
        assertTrue(worker.contains("model.embed(SkillRouteProjectionIdentity.document(candidate),false)"));
    }

    private String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }
}
