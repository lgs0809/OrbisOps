package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagDocumentParserPort;
import cn.lgs.orbisops.application.rag.RagIngestionCommandUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionJobApplicationService;
import cn.lgs.orbisops.application.rag.RagIngestionJobSettings;
import cn.lgs.orbisops.application.rag.RagIngestionJobUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionUseCase;
import cn.lgs.orbisops.application.rag.RagMultimodalWriterPort;
import cn.lgs.orbisops.application.rag.RagTagOrderPort;
import cn.lgs.orbisops.application.rag.RagVectorWriterPort;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagIngestionJobRepository;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class OpsRagIngestionApplicationConfiguration {

    @Bean
    public RagIngestionCommandUseCase ragIngestionUseCase(RagDocumentParserPort parserPort,
                                                           RagVectorWriterPort vectorWriterPort,
                                                           RagMultimodalWriterPort multimodalWriterPort,
                                                           RagTagOrderPort tagOrderPort,
                                                           IRagKnowledgeRepository knowledgeRepository) {
        return new RagIngestionUseCase(
                parserPort, vectorWriterPort, multimodalWriterPort,
                tagOrderPort, knowledgeRepository);
    }

    @Bean
    public RagIngestionJobUseCase ragIngestionJobUseCase(
            RagIngestionCommandUseCase ingestionUseCase,
            @Qualifier("ragIngestionExecutor") ThreadPoolExecutor executor,
            IRagIngestionJobRepository repository,
            @Value("${orbisops.rag.ingestion.max-file-count:20}") int maxFileCount,
            @Value("${orbisops.rag.ingestion.max-file-bytes:52428800}") long maxFileBytes,
            @Value("${orbisops.rag.ingestion.max-total-bytes:209715200}") long maxTotalBytes,
            @Value("${orbisops.rag.ingestion.allowed-extensions:md,markdown,pdf}") String allowedExtensions,
            @Value("${orbisops.rag.ingestion.allowed-content-types:text/markdown,text/plain,application/pdf,application/octet-stream}") String allowedContentTypes) {
        RagIngestionJobSettings settings = RagIngestionJobSettings.fromCsv(
                maxFileCount, maxFileBytes, maxTotalBytes, allowedExtensions, allowedContentTypes);
        return new RagIngestionJobApplicationService(ingestionUseCase, executor, repository, settings);
    }
}
