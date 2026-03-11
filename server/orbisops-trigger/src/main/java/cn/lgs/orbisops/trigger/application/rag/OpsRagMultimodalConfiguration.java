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
            @Value("${ops.rag.multimodal.enabled:false}") boolean enabled,
            @Value("${ops.rag.multimodal.provider:qwen-vl}") String provider,
            @Value("${ops.rag.multimodal.base-url:http://127.0.0.1:8110}") String baseUrl,
            @Value("${ops.rag.multimodal.api-key:local-rag-models}") String apiKey,
            @Value("${ops.rag.multimodal.path:v1/multimodalembeddings}") String path,
            @Value("${ops.rag.multimodal.model:Qwen/Qwen3-VL-Embedding-2B}") String model,
            @Value("${ops.rag.multimodal.table-name:vector_store_qwen_vl_multimodal}") String tableName,
            @Value("${ops.rag.multimodal.dimension:2048}") int dimension,
            @Value("${ops.rag.multimodal.auto-init:true}") boolean autoInit,
            @Value("${ops.rag.multimodal.index-text-documents:false}") boolean indexTextDocuments,
            @Value("${ops.rag.multimodal.index-original-media:true}") boolean indexOriginalMedia,
            @Value("${ops.rag.multimodal.index-pdf-page-images:true}") boolean indexPdfPageImages,
            @Value("${ops.rag.multimodal.max-pdf-pages:3}") int maxPdfPages,
            @Value("${ops.rag.multimodal.pdf-render-dpi:144}") int pdfRenderDpi,
            @Value("${ops.rag.multimodal.max-image-bytes:20971520}") long maxImageBytes,
            @Value("${ops.rag.multimodal.max-text-chars:3000}") int maxTextChars,
            @Value("${ops.rag.multimodal.search-top-k:8}") int defaultSearchTopK,
            @Value("${ops.rag.multimodal.timeout-seconds:30}") int timeoutSeconds,
            @Value("${ops.rag.multimodal.max-retries:1}") int maxRetries) {
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
