package cn.lgs.orbisops.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible embedding model with fail-safe response-cardinality handling.
 * Normal requests remain batched; only malformed batch responses fall back to one request per input.
 */
public final class OpenAiCompatibleEmbeddingModel extends OpenAiEmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingModel.class);

    private final OpenAiEmbeddingModel delegate;

    public OpenAiCompatibleEmbeddingModel(OpenAiEmbeddingModel delegate) {
        super(OpenAiApi.builder()
                .baseUrl("http://127.0.0.1")
                .apiKey("not-used")
                .build(), MetadataMode.NONE);
        if (delegate == null) {
            throw new IllegalArgumentException("EMBEDDING_MODEL_DELEGATE_REQUIRED");
        }
        this.delegate = delegate;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        EmbeddingResponse response = delegate.call(request);
        int inputCount = request == null || request.getInstructions() == null
                ? 0
                : request.getInstructions().size();
        int resultCount = resultCount(response);
        if (inputCount == resultCount) {
            return response;
        }
        if (inputCount <= 1) {
            throw mismatch(inputCount, resultCount);
        }

        log.warn(
                "Embedding provider returned unexpected batch cardinality, inputCount={}, resultCount={}; retrying individually",
                inputCount,
                resultCount);
        List<Embedding> recovered = new ArrayList<>(inputCount);
        for (int i = 0; i < inputCount; i++) {
            EmbeddingResponse single = delegate.call(new EmbeddingRequest(
                    List.of(request.getInstructions().get(i)),
                    request.getOptions()));
            int singleCount = resultCount(single);
            if (singleCount != 1) {
                throw mismatch(1, singleCount);
            }
            Embedding value = single.getResults().get(0);
            recovered.add(new Embedding(value.getOutput(), i, value.getMetadata()));
        }
        return response != null && response.getMetadata() != null
                ? new EmbeddingResponse(recovered, response.getMetadata())
                : new EmbeddingResponse(recovered);
    }

    @Override
    public String getEmbeddingContent(Document document) {
        return delegate.getEmbeddingContent(document);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    private int resultCount(EmbeddingResponse response) {
        return response == null || response.getResults() == null ? 0 : response.getResults().size();
    }

    private IllegalStateException mismatch(int expected, int actual) {
        return new IllegalStateException(
                "EMBEDDING_RESPONSE_CARDINALITY_MISMATCH: expected=" + expected + ", actual=" + actual);
    }
}
