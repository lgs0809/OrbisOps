package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalAvailabilityTest {

    @Test
    void disabledConfigurationMustRemainUnavailableWithoutWarning() {
        FakeRepository repository = new FakeRepository(true);
        RagMultimodalAvailability availability = new RagMultimodalAvailability(
                settings(false, "qwen-vl", "http://localhost", "secret"), repository);

        RagMultimodalAvailability.Decision decision = availability.evaluate();

        assertFalse(decision.available());
        assertFalse(decision.warnUnavailable());
        assertFalse(decision.repositoryAvailable());
    }

    @Test
    void repositoryUnavailableMustRequestWarningOnlyOnce() {
        FakeRepository repository = new FakeRepository(false);
        RagMultimodalAvailability availability = new RagMultimodalAvailability(
                settings(true, "qwen-vl", "http://localhost", "secret"), repository);

        RagMultimodalAvailability.Decision first = availability.evaluate();
        RagMultimodalAvailability.Decision second = availability.evaluate();

        assertFalse(first.available());
        assertTrue(first.warnUnavailable());
        assertFalse(first.repositoryAvailable());
        assertFalse(second.available());
        assertFalse(second.warnUnavailable());
    }

    @Test
    void unsupportedProviderMustBeUnavailableAndConfiguredProviderMustBeAvailable() {
        FakeRepository repository = new FakeRepository(true);
        RagMultimodalAvailability unsupported = new RagMultimodalAvailability(
                settings(true, "unsupported", "http://localhost", "secret"), repository);
        RagMultimodalAvailability configured = new RagMultimodalAvailability(
                settings(true, " voyage ", "http://localhost", "secret"), repository);

        RagMultimodalAvailability.Decision unsupportedDecision = unsupported.evaluate();
        RagMultimodalAvailability.Decision configuredDecision = configured.evaluate();

        assertFalse(unsupportedDecision.available());
        assertTrue(unsupportedDecision.warnUnavailable());
        assertTrue(configuredDecision.available());
        assertFalse(configuredDecision.warnUnavailable());
    }

    @Test
    void blankConnectionConfigurationMustExposeExactDecisionFacts() {
        RagMultimodalAvailability availability = new RagMultimodalAvailability(
                settings(true, "qwen-vl", "   ", " "), new FakeRepository(true));

        RagMultimodalAvailability.Decision decision = availability.evaluate();

        assertFalse(decision.available());
        assertTrue(decision.warnUnavailable());
        assertFalse(decision.baseUrlConfigured());
        assertFalse(decision.apiKeyConfigured());
    }

    private RagMultimodalSettings settings(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey) {
        return new RagMultimodalSettings(
                enabled,
                provider,
                baseUrl,
                apiKey,
                "v1/embed",
                "model",
                "table_name",
                2048,
                true,
                false,
                true,
                true,
                3,
                144,
                20_971_520L,
                3000,
                8,
                30,
                1);
    }

    private static final class FakeRepository implements IRagMultimodalRepository {

        private final boolean available;

        private FakeRepository(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean ensureTable(String tableName, int dimension) {
            return true;
        }

        @Override
        public List<RagDocument> search(
                String tableName,
                String vectorLiteral,
                int dimension,
                String filterExpression,
                int topK,
                String provider,
                String model) {
            return List.of();
        }

        @Override
        public void upsert(
                String tableName,
                int dimension,
                String id,
                String content,
                Map<String, Object> metadata,
                String vectorLiteral) {
        }
    }
}
