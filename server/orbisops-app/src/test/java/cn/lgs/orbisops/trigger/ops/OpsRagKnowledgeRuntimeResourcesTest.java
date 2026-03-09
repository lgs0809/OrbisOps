package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpsRagKnowledgeRuntimeResourcesTest {

    @Test
    void unavailableResourcesRemainExplicitAndFailClosed() {
        OpsRagKnowledgeRuntimeResources resources = OpsRagKnowledgeRuntimeResources.unavailable();

        assertFalse(resources.vectorStoreAvailable());
        assertNull(resources.vectorStore());
        assertNull(resources.multimodalEmbeddingService());
        assertNull(resources.embeddingModel());
    }
}
