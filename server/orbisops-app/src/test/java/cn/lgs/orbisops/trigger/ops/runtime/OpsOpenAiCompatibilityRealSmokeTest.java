package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsOpenAiCompatibilityRealSmokeTest {

    @Test
    void localRelayUsesTheSameSpringAiUrlCompositionWithoutHiddenRetry() {
        String baseUrl = System.getProperty("real.model.relay.url");
        Assumptions.assumeTrue(baseUrl != null && !baseUrl.isBlank(),
                "REAL_STAGING only: set -Dreal.model.relay.url to opt in");
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey("local-evaluation-relay")
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .restClientBuilder(OpsModelHttpClientFactory.restClientBuilder(5, 45))
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-5.4-mini").build())
                .build();

        String content = model.call(new Prompt("Reply exactly SPRING_AI_REAL_OK"))
                .getResult().getOutput().getText();

        assertTrue(content.contains("SPRING_AI_REAL_OK"));
    }
}
