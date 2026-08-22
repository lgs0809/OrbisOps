package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.trigger.ops.rag.RagVisualAnalysisSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRagVisualAnalysisConfiguration {

    @Bean
    public RagVisualAnalysisSettings ragVisualAnalysisSettings(
            @Value("${orbisops.rag.parse.visual.enabled:false}") boolean enabled,
            @Value("${orbisops.rag.parse.visual.high-value-only:true}") boolean highValueOnly,
            @Value("${orbisops.rag.parse.visual.provider:openai}") String provider,
            @Value("${orbisops.rag.parse.visual.base-url:${spring.ai.openai.base-url:}}") String baseUrl,
            @Value("${orbisops.rag.parse.visual.api-key:${spring.ai.openai.api-key:}}") String apiKey,
            @Value("${orbisops.rag.parse.visual.path:v1/chat/completions}") String path,
            @Value("${orbisops.rag.parse.visual.model:}") String model,
            @Value("${orbisops.rag.parse.visual.detail:low}") String detail,
            @Value("${orbisops.rag.parse.visual.timeout-seconds:30}") int timeoutSeconds,
            @Value("${orbisops.rag.parse.visual.max-completion-tokens:1200}") int maxCompletionTokens,
            @Value("${orbisops.rag.parse.visual.token-limit-field:max_completion_tokens}") String tokenLimitField,
            @Value("${orbisops.rag.parse.visual.response-format:json_schema}") String responseFormatMode,
            @Value("${orbisops.rag.parse.visual.max-retries:1}") int maxRetries,
            @Value("${orbisops.rag.parse.visual.max-images-per-document:3}") int maxImagesPerDocument,
            @Value("${orbisops.rag.parse.visual.max-image-bytes:4194304}") long maxImageBytes,
            @Value("${orbisops.rag.parse.visual.pdf-render-dpi:144}") int pdfRenderDpi) {
        return new RagVisualAnalysisSettings(
                enabled,
                highValueOnly,
                provider,
                baseUrl,
                apiKey,
                path,
                model,
                detail,
                timeoutSeconds,
                maxCompletionTokens,
                tokenLimitField,
                responseFormatMode,
                maxRetries,
                maxImagesPerDocument,
                maxImageBytes,
                pdfRenderDpi);
    }
}
