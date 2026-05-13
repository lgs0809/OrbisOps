package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeAggregateCatalogPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeAuditPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeBaseCatalogApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeCatalogApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeCatalogPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeDocumentCatalogApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeDocumentIngestionApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeProjectDefaultPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeProjectExistencePort;
import cn.lgs.orbisops.application.knowledge.KnowledgeRagDocumentApplicationService;
import cn.lgs.orbisops.application.knowledge.KnowledgeRagDocumentPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeRetrievalPolicyApplicationService;
import cn.lgs.orbisops.application.rag.RagIngestionJobUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionJobView;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeBaseCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeDocumentCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeRetrievalPolicyRepository;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IProjectKnowledgeAuthorizationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;

@Configuration
public class OpsKnowledgeApplicationConfiguration {

    @Bean
    public KnowledgeCatalogApplicationService<RagIngestionJobView, MultipartFile>
    knowledgeCatalogApplicationService(
            KnowledgeCatalogPort<RagIngestionJobView, MultipartFile> port,
            KnowledgeAuditPort auditPort,
            KnowledgeBaseCatalogApplicationService catalogService,
            KnowledgeRagDocumentApplicationService ragDocumentService,
            KnowledgeRetrievalPolicyApplicationService retrievalPolicyService,
            KnowledgeAuthorizationApplicationService authorizationService,
            KnowledgeProjectDefaultPort projectDefaultPort) {
        return new KnowledgeCatalogApplicationService<>(
                port, auditPort, catalogService, ragDocumentService,
                retrievalPolicyService, authorizationService, projectDefaultPort);
    }

    @Bean
    public KnowledgeBaseCatalogApplicationService knowledgeBaseCatalogApplicationService(
            IKnowledgeBaseCatalogRepository repository,
            KnowledgeAggregateCatalogPort aggregatePort,
            KnowledgeProjectExistencePort projectExistencePort) {
        return new KnowledgeBaseCatalogApplicationService(
                repository, aggregatePort, projectExistencePort);
    }

    @Bean
    public KnowledgeDocumentCatalogApplicationService knowledgeDocumentCatalogApplicationService(
            IKnowledgeDocumentCatalogRepository repository) {
        return new KnowledgeDocumentCatalogApplicationService(repository);
    }

    @Bean
    public KnowledgeDocumentIngestionApplicationService knowledgeDocumentIngestionApplicationService(
            RagIngestionJobUseCase ingestionJobUseCase,
            KnowledgeRetrievalPolicyApplicationService retrievalPolicyService,
            KnowledgeDocumentCatalogApplicationService documentCatalogService,
            KnowledgeProjectExistencePort projectExistencePort) {
        return new KnowledgeDocumentIngestionApplicationService(
                ingestionJobUseCase, retrievalPolicyService, documentCatalogService,
                projectExistencePort);
    }

    @Bean
    public KnowledgeRagDocumentApplicationService knowledgeRagDocumentApplicationService(
            KnowledgeRagDocumentPort port,
            KnowledgeDocumentCatalogApplicationService documentCatalogService,
            KnowledgeProjectExistencePort projectExistencePort) {
        return new KnowledgeRagDocumentApplicationService(
                port, documentCatalogService, projectExistencePort);
    }

    @Bean
    public KnowledgeRetrievalPolicyApplicationService knowledgeRetrievalPolicyApplicationService(
            IKnowledgeRetrievalPolicyRepository repository,
            KnowledgeBaseCatalogApplicationService catalogService) {
        return new KnowledgeRetrievalPolicyApplicationService(repository, catalogService);
    }

    @Bean
    public KnowledgeAuthorizationApplicationService knowledgeAuthorizationApplicationService(
            IProjectKnowledgeAuthorizationRepository repository) {
        return new KnowledgeAuthorizationApplicationService(repository);
    }
}
