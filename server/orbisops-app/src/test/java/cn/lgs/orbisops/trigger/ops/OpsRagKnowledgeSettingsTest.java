package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRagKnowledgeSettingsTest {

    @Test
    void mapsTypedSettingsIntoUnifiedRetrievalProtocol() {
        OpsRagKnowledgeSettings settings = new OpsRagKnowledgeSettings(
                true,
                " cohere ",
                " http://rerank ",
                " key ",
                " v1/rerank ",
                " reranker ",
                17,
                4,
                900,
                " llm ",
                true,
                " http://llm ",
                " llm-key ",
                " v1/chat/completions ",
                " model ",
                3,
                5,
                12,
                false,
                1,
                true);

        OpsRagKnowledgeRetrievalService.Settings retrieval = settings.retrievalSettings();

        assertTrue(retrieval.rerankEnabled());
        assertEquals("cohere", retrieval.rerankProvider());
        assertEquals("http://rerank", retrieval.rerankBaseUrl());
        assertEquals("key", retrieval.rerankApiKey());
        assertEquals(17, retrieval.rerankCandidateTopK());
        assertEquals(4, retrieval.rerankTopN());
        assertEquals("llm", retrieval.queryRewriteMode());
        assertTrue(retrieval.llmQueryRewriteEnabled());
        assertFalse(retrieval.llmQueryRewriteOnLowRecall());
        assertTrue(retrieval.failOnLlmDegradation());
    }

    @Test
    void defaultsRemainProviderNeutralUntilRerankIsConfigured() {
        OpsRagKnowledgeSettings defaults = OpsRagKnowledgeSettings.defaults();

        assertFalse(defaults.rerankEnabled());
        assertEquals("", defaults.rerankProvider());
        assertEquals("", defaults.rerankBaseUrl());
        assertEquals("", defaults.rerankApiKey());
        assertEquals("", defaults.rerankModel());
        assertEquals("rule", defaults.queryRewriteMode());
        assertFalse(defaults.llmQueryRewriteEnabled());
        assertFalse(defaults.failOnLlmDegradation());
    }
}
