package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.trigger.ops.rag.RagVisualAnalysisSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRagVisualAnalysisConfiguration {

    @Bean
    public RagVisualAnalysisSettings ragVisualAnalysisSettings(
            @Value("${ops.rag.parse.visual.enabled:false}") boolean enabled,
            @Value("${ops.rag.parse.visual.high-value-only:true}") boolean highValueOnly,
            @Value("${ops.rag.parse.visual.provider:openai}") String provider,
            @Value("${ops.rag.parse.visual.base-url:${spring.ai.openai.base-url:}}") String baseUrl,
            @Value("${ops.rag.parse.visual.api-key:${spring.ai.openai.api-key:}}") String apiKey,
            @Value("${ops.rag.parse.visual.path:v1/chat/completions}") String path,
            @Value("${ops.rag.parse.visual.model:gpt-5.4-mini}") String model,
            @Value("${ops.rag.parse.visual.detail:low}") String detail,
            @Value("${ops.rag.parse.visual.timeout-seconds:30}") int timeoutSeconds,
            @Value("${ops.rag.parse.visual.max-completion-tokens:1200}") int maxCompletionTokens,
            @Value("${ops.rag.parse.visual.token-limit-field:max_completion_tokens}") String tokenLimitField,
            @Value("${ops.rag.parse.visual.response-format:json_schema}") String responseFormatMode,
            @Value("${ops.rag.parse.visual.max-retries:1}") int maxRetries,
            @Value("${ops.rag.parse.visual.max-images-per-document:3}") int maxImagesPerDocument,
            @Value("${ops.rag.parse.visual.max-image-bytes:4194304}") long maxImageBytes,
            @Value("${ops.rag.parse.visual.pdf-render-dpi:144}") int pdfRenderDpi) {
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
