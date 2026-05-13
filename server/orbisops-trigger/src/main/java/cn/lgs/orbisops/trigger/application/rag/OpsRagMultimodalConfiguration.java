package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalRuntimeComponents;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalRuntimeFactory;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRagMultimodalConfiguration {

    @Bean
    public RagMultimodalSettings ragMultimodalSettings(
            @Value("${orbisops.rag.multimodal.enabled:false}") boolean enabled,
            @Value("${orbisops.rag.multimodal.provider:}") String provider,
            @Value("${orbisops.rag.multimodal.base-url:}") String baseUrl,
            @Value("${orbisops.rag.multimodal.api-key:}") String apiKey,
            @Value("${orbisops.rag.multimodal.path:v1/multimodalembeddings}") String path,
            @Value("${orbisops.rag.multimodal.model:}") String model,
            @Value("${orbisops.rag.multimodal.table-name:orbisops_multimodal_vectors}") String tableName,
            @Value("${orbisops.rag.multimodal.dimension:2048}") int dimension,
            @Value("${orbisops.rag.multimodal.auto-init:true}") boolean autoInit,
            @Value("${orbisops.rag.multimodal.index-text-documents:false}") boolean indexTextDocuments,
            @Value("${orbisops.rag.multimodal.index-original-media:true}") boolean indexOriginalMedia,
            @Value("${orbisops.rag.multimodal.index-pdf-page-images:true}") boolean indexPdfPageImages,
            @Value("${orbisops.rag.multimodal.max-pdf-pages:3}") int maxPdfPages,
            @Value("${orbisops.rag.multimodal.pdf-render-dpi:144}") int pdfRenderDpi,
            @Value("${orbisops.rag.multimodal.max-image-bytes:20971520}") long maxImageBytes,
            @Value("${orbisops.rag.multimodal.max-text-chars:3000}") int maxTextChars,
            @Value("${orbisops.rag.multimodal.search-top-k:8}") int defaultSearchTopK,
            @Value("${orbisops.rag.multimodal.timeout-seconds:30}") int timeoutSeconds,
            @Value("${orbisops.rag.multimodal.max-retries:1}") int maxRetries) {
        return new RagMultimodalSettings(
                enabled,
                provider,
                baseUrl,
                apiKey,
                path,
                model,
                tableName,
                dimension,
                autoInit,
                indexTextDocuments,
                indexOriginalMedia,
                indexPdfPageImages,
                maxPdfPages,
                pdfRenderDpi,
                maxImageBytes,
                maxTextChars,
                defaultSearchTopK,
                timeoutSeconds,
                maxRetries);
    }

    @Bean
    public RagMultimodalRuntimeComponents ragMultimodalRuntimeComponents(
            IRagMultimodalRepository repository,
            RagMultimodalSettings settings) {
        return RagMultimodalRuntimeFactory.create(repository, settings);
    }
}
