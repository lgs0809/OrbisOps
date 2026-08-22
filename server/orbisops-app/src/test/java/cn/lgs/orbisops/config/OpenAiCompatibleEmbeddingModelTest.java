package cn.lgs.orbisops.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiCompatibleEmbeddingModelTest {

    @Test
    void keepsNormalBatchRequestBatched() {
        OpenAiEmbeddingModel delegate = mock(OpenAiEmbeddingModel.class);
        EmbeddingRequest request = new EmbeddingRequest(List.of("alpha", "beta"), null);
        EmbeddingResponse expected = response(embedding(1.0f, 0), embedding(2.0f, 1));
        when(delegate.call(request)).thenReturn(expected);

        EmbeddingResponse actual = new OpenAiCompatibleEmbeddingModel(delegate).call(request);

        assertEquals(2, actual.getResults().size());
        verify(delegate, times(1)).call(request);
    }

    @Test
    void retriesIndividuallyWhenBatchCardinalityDoesNotMatch() {
        OpenAiEmbeddingModel delegate = mock(OpenAiEmbeddingModel.class);
        EmbeddingRequest request = new EmbeddingRequest(List.of("alpha", "beta"), null);
        when(delegate.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest actualRequest = invocation.getArgument(0);
            return switch (actualRequest.getInstructions().size()) {
                case 2 -> response(embedding(9.0f, 0));
                case 1 -> "alpha".equals(actualRequest.getInstructions().get(0))
                        ? response(embedding(1.0f, 0))
                        : response(embedding(2.0f, 0));
                default -> new EmbeddingResponse(List.of());
            };
        });

        EmbeddingResponse actual = new OpenAiCompatibleEmbeddingModel(delegate).call(request);

        assertEquals(2, actual.getResults().size());
        assertEquals(0, actual.getResults().get(0).getIndex());
        assertEquals(1, actual.getResults().get(1).getIndex());
        assertArrayEquals(new float[]{1.0f}, actual.getResults().get(0).getOutput());
        assertArrayEquals(new float[]{2.0f}, actual.getResults().get(1).getOutput());
        verify(delegate, times(3)).call(any(EmbeddingRequest.class));
    }

    @Test
    void failsClosedWhenSingleResponseStillHasWrongCardinality() {
        OpenAiEmbeddingModel delegate = mock(OpenAiEmbeddingModel.class);
        EmbeddingRequest request = new EmbeddingRequest(List.of("alpha"), null);
        when(delegate.call(request)).thenReturn(new EmbeddingResponse(List.of()));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new OpenAiCompatibleEmbeddingModel(delegate).call(request));

        assertEquals(
                "EMBEDDING_RESPONSE_CARDINALITY_MISMATCH: expected=1, actual=0",
                error.getMessage());
    }

    private EmbeddingResponse response(Embedding... embeddings) {
        return new EmbeddingResponse(List.of(embeddings));
    }

    private Embedding embedding(float value, int index) {
        return new Embedding(new float[]{value}, index);
    }
}
