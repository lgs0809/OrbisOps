package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.CaptureMemoryUseCase;
import cn.lgs.orbisops.application.memory.ColdMemoryStoreApplicationService;
import cn.lgs.orbisops.application.memory.GovernedMemoryApplicationService;
import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;
import cn.lgs.orbisops.application.memory.ContextMemoryAdminApplicationService;
import cn.lgs.orbisops.application.memory.ContextMemoryApplicationFacade;
import cn.lgs.orbisops.application.memory.ContextMemoryQueryApplicationService;
import cn.lgs.orbisops.application.memory.ContextMemoryQueryPort;
import cn.lgs.orbisops.application.memory.ContextMemorySceneQueryApplicationService;
import cn.lgs.orbisops.application.memory.ContextMemoryStoreApplicationService;
import cn.lgs.orbisops.application.memory.ContextMemoryWritePort;
import cn.lgs.orbisops.application.memory.HotMemoryClearPort;
import cn.lgs.orbisops.application.memory.HotMemoryQueryPort;
import cn.lgs.orbisops.application.memory.HotMemoryReplacePort;
import cn.lgs.orbisops.application.memory.HotMemoryWritePort;
import cn.lgs.orbisops.application.memory.MemoryCaptureApplicationService;
import cn.lgs.orbisops.application.memory.MemoryCompressionApplicationService;
import cn.lgs.orbisops.application.memory.MemoryCompressionPort;
import cn.lgs.orbisops.application.memory.MemoryContextRenderingApplicationService;
import cn.lgs.orbisops.application.memory.MemoryExtractionApplicationService;
import cn.lgs.orbisops.application.memory.MemoryExtractionPort;
import cn.lgs.orbisops.application.memory.MemoryPostProcessingApplicationService;
import cn.lgs.orbisops.application.memory.MemoryQueryApplicationService;
import cn.lgs.orbisops.application.memory.MemoryRetrievalApplicationService;
import cn.lgs.orbisops.application.memory.MemorySelectionReferenceApplicationService;
import cn.lgs.orbisops.application.memory.MemorySessionClearApplicationService;
import cn.lgs.orbisops.application.memory.QueryRuntimeMemoryUseCase;
import cn.lgs.orbisops.application.memory.SemanticLexicalRecallPort;
import cn.lgs.orbisops.application.memory.SemanticLexicalWritePort;
import cn.lgs.orbisops.application.memory.SemanticMemoryApplicationFacade;
import cn.lgs.orbisops.application.memory.SemanticMemoryClearApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryClearPersistencePort;
import cn.lgs.orbisops.application.memory.SemanticMemoryClearPort;
import cn.lgs.orbisops.application.memory.SemanticMemoryQueryPort;
import cn.lgs.orbisops.application.memory.SemanticMemoryRetrievalApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryWriteApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryWritePort;
import cn.lgs.orbisops.application.memory.VerifyProjectFactUseCase;
import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.adapter.repository.IContextMemoryRepository;
import cn.lgs.orbisops.domain.memory.adapter.repository.IGovernedMemoryRepository;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryDefinitionPolicy;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryProjectionPolicy;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryHashPolicy;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryCapturePolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryCompressionPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryExtractionPolicy;
import cn.lgs.orbisops.domain.memory.service.MemorySceneClassificationPolicy;
import cn.lgs.orbisops.domain.memory.service.MemorySelectionPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryFusionPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.Executor;

@Configuration
public class OpsMemoryApplicationConfiguration {

    @Bean
    public CaptureMemoryUseCase captureMemoryUseCase(
            GovernedMemoryApplicationService memoryApplication) {
        return new CaptureMemoryUseCase(memoryApplication);
    }

    @Bean
    public QueryRuntimeMemoryUseCase queryRuntimeMemoryUseCase(
            GovernedMemoryApplicationService memoryApplication) {
        return new QueryRuntimeMemoryUseCase(memoryApplication);
    }

    @Bean
    public VerifyProjectFactUseCase verifyProjectFactUseCase(
            GovernedMemoryApplicationService memoryApplication) {
        return new VerifyProjectFactUseCase(memoryApplication);
    }

    @Bean
    public GovernedMemoryApplicationService governedMemoryApplicationService(
            IGovernedMemoryRepository repository,
            OpsGovernedMemoryExternalAuditAdapter externalAuditAdapter,
            @Value("${orbisops.memory.default-ttl:PT24H}") Duration defaultTtl) {
        return new GovernedMemoryApplicationService(
                repository,
                new GovernedMemoryPolicy(),
                new GovernedMemoryHashPolicy(),
                () -> "memory-" + UUID.randomUUID(),
                Clock.systemDefaultZone(),
                externalAuditAdapter,
                defaultTtl);
    }

    @Bean
    public ColdMemoryStoreApplicationService coldMemoryStoreApplicationService(
            ObjectProvider<IColdMemoryRepository> repositoryProvider,
            OpsColdMemoryStoreFailureAdapter failureAdapter,
            @Value("${orbisops.chat.memory.jdbc-enabled:true}") boolean jdbcEnabled) {
        IColdMemoryRepository repository = repositoryProvider == null
                ? null
                : repositoryProvider.getIfAvailable();
        return new ColdMemoryStoreApplicationService(repository, () -> jdbcEnabled, failureAdapter);
    }

    @Bean
    public ContextMemoryStoreApplicationService contextMemoryStoreApplicationService(
            ObjectProvider<IContextMemoryRepository> repositoryProvider,
            OpsContextMemoryAuditAdapter auditAdapter,
            OpsContextMemoryStoreFailureAdapter failureAdapter) {
        IContextMemoryRepository repository = repositoryProvider == null
                ? null
                : repositoryProvider.getIfAvailable();
        return new ContextMemoryStoreApplicationService(
                repository,
                new ContextMemoryProjectionPolicy(),
                auditAdapter,
                failureAdapter);
    }

    @Bean
    public ContextMemoryAdminApplicationService contextMemoryAdminApplicationService(
            ContextMemoryStoreApplicationService storeService) {
        return new ContextMemoryAdminApplicationService(
                storeService,
                new ContextMemoryDefinitionPolicy(),
                () -> UUID.randomUUID().toString());
    }

    @Bean
    public ContextMemoryQueryApplicationService contextMemoryQueryApplicationService(
            ContextMemoryStoreApplicationService storeService) {
        return new ContextMemoryQueryApplicationService(
                storeService,
                new ContextMemoryDefinitionPolicy());
    }

    @Bean
    public ContextMemorySceneQueryApplicationService contextMemorySceneQueryApplicationService(
            ContextMemoryStoreApplicationService storeService) {
        return new ContextMemorySceneQueryApplicationService(
                storeService,
                new ContextMemoryDefinitionPolicy());
    }

    @Bean
    public ContextMemoryApplicationFacade contextMemoryApplicationFacade(
            ContextMemoryStoreApplicationService storeService,
            ContextMemoryQueryApplicationService queryService,
            ContextMemoryAdminApplicationService adminService,
            ContextMemorySceneQueryApplicationService sceneQueryService) {
        return new ContextMemoryApplicationFacade(
                storeService,
                queryService,
                adminService,
                sceneQueryService);
    }

    @Bean
    public SemanticMemoryRetrievalApplicationService semanticMemoryRetrievalApplicationService(
            OpsSemanticVectorRecallAdapter vectorRecallAdapter,
            SemanticLexicalRecallPort lexicalRecallAdapter,
            OpsSemanticRetrievalFailureAdapter failureAdapter) {
        SemanticMemoryPolicy semanticPolicy = new SemanticMemoryPolicy(new MemoryContentHashPolicy());
        return new SemanticMemoryRetrievalApplicationService(
                vectorRecallAdapter,
                lexicalRecallAdapter,
                failureAdapter,
                new SemanticMemoryFusionPolicy(semanticPolicy));
    }

    @Bean
    public SemanticMemoryWriteApplicationService semanticMemoryWriteApplicationService(
            OpsSemanticVectorWriteAdapter vectorWriteAdapter,
            SemanticLexicalWritePort lexicalWriteAdapter,
            OpsSemanticRetrievalFailureAdapter failureAdapter) {
        return SemanticMemoryWriteApplicationService.withDefaultPolicy(
                vectorWriteAdapter,
                lexicalWriteAdapter,
                failureAdapter);
    }

    @Bean
    public SemanticMemoryClearApplicationService semanticMemoryClearApplicationService(
            SemanticMemoryClearPersistencePort persistenceAdapter,
            OpsSemanticRetrievalFailureAdapter failureAdapter) {
        return new SemanticMemoryClearApplicationService(persistenceAdapter, failureAdapter);
    }

    @Bean
    public SemanticMemoryApplicationFacade semanticMemoryApplicationFacade(
            SemanticMemoryWriteApplicationService writeService,
            SemanticMemoryRetrievalApplicationService retrievalService,
            SemanticMemoryClearApplicationService clearService) {
        return new SemanticMemoryApplicationFacade(writeService, retrievalService, clearService);
    }

    @Bean
    public MemoryExtractionApplicationService memoryExtractionApplicationService(
            OpsMemoryModelExtractionAdapter modelExtractionAdapter,
            OpsMemoryExtractionFailureAdapter failureAdapter) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return new MemoryExtractionApplicationService(
                new MemoryExtractionPolicy(),
                new MemoryContentHashPolicy(),
                modelExtractionAdapter,
                failureAdapter,
                () -> formatter.format(LocalDateTime.now()));
    }

    @Bean
    public MemoryCompressionApplicationService memoryCompressionApplicationService(
            OpsMemoryModelSummaryAdapter modelSummaryAdapter,
            HotMemoryReplacePort hotMemoryReplacePort,
            ColdMemoryStoreApplicationService coldMemoryStore,
            IConversationMemoryRepository conversationRepository) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return new MemoryCompressionApplicationService(
                new MemoryCompressionPolicy(new MemoryContentHashPolicy()),
                modelSummaryAdapter,
                conversationRepository,
                () -> formatter.format(LocalDateTime.now()));
    }

    @Bean
    public MemoryContextRenderingApplicationService memoryContextRenderingApplicationService() {
        return new MemoryContextRenderingApplicationService();
    }

    @Bean
    public MemorySelectionReferenceApplicationService memorySelectionReferenceApplicationService() {
        return new MemorySelectionReferenceApplicationService(
                new MemoryContentHashPolicy(),
                Clock.systemUTC());
    }

    @Bean
    public MemoryPostProcessingApplicationService memoryPostProcessingApplicationService(
            SemanticMemoryWritePort semanticMemoryWritePort,
            MemoryExtractionPort memoryExtractionPort,
            ColdMemoryStoreApplicationService coldMemoryStore,
            ContextMemoryWritePort contextMemoryWritePort,
            MemoryCompressionPort memoryCompressionPort,
            @Qualifier("opsMemoryExecutor") ObjectProvider<Executor> memoryExecutorProvider,
            OpsMemoryPostProcessingFailureAdapter failureAdapter,
            IConversationMemoryRepository conversationRepository) {
        return new MemoryPostProcessingApplicationService(
                semanticMemoryWritePort,
                memoryExtractionPort,
                coldMemoryStore,
                contextMemoryWritePort,
                memoryCompressionPort,
                memoryExecutorProvider == null ? () -> null : memoryExecutorProvider::getIfAvailable,
                failureAdapter,
                conversationRepository, Clock.systemUTC());
    }

    @Bean
    public MemoryCaptureApplicationService memoryCaptureApplicationService(
            HotMemoryWritePort hotMemoryWritePort,
            ColdMemoryStoreApplicationService coldMemoryStore,
            MemoryPostProcessingApplicationService postProcessingService,
            OpsMemoryCaptureFailureAdapter failureAdapter,
            IConversationMemoryRepository conversationRepository) {
        return new MemoryCaptureApplicationService(
                hotMemoryWritePort,
                coldMemoryStore,
                postProcessingService,
                new MemoryCapturePolicy(),
                Clock.systemDefaultZone(),
                failureAdapter, conversationRepository);
    }

    @Bean
    public MemorySessionClearApplicationService memorySessionClearApplicationService(
            HotMemoryClearPort hotMemoryClearPort,
            ColdMemoryStoreApplicationService coldMemoryStore,
            SemanticMemoryClearPort semanticMemoryClearPort,
            MemoryCaptureApplicationService captureService,
            OpsMemorySessionClearFailureAdapter failureAdapter) {
        return new MemorySessionClearApplicationService(
                hotMemoryClearPort,
                coldMemoryStore,
                semanticMemoryClearPort,
                captureService,
                failureAdapter);
    }

    @Bean
    public MemoryRetrievalApplicationService memoryRetrievalApplicationService(
            ColdMemoryStoreApplicationService coldMemoryStore,
            HotMemoryQueryPort hotMemoryQueryPort,
            SemanticMemoryQueryPort semanticMemoryQueryPort,
            ContextMemoryQueryPort contextMemoryQueryPort,
            @Qualifier("opsMemoryExecutor") ObjectProvider<Executor> memoryExecutorProvider,
            OpsMemoryRetrievalFailureAdapter failureAdapter,
            IConversationMemoryRepository conversationRepository) {
        return new MemoryRetrievalApplicationService(
                coldMemoryStore,
                hotMemoryQueryPort,
                semanticMemoryQueryPort,
                contextMemoryQueryPort,
                memoryExecutorProvider == null ? () -> null : memoryExecutorProvider::getIfAvailable,
                failureAdapter, conversationRepository);
    }

    @Bean
    public MemoryQueryApplicationService memoryQueryApplicationService(
            MemoryRetrievalApplicationService retrievalService,
            MemoryContextRenderingApplicationService renderingService,
            MemorySelectionReferenceApplicationService referenceService,
            OpsMemoryQueryFailureAdapter failureAdapter) {
        return new MemoryQueryApplicationService(
                retrievalService,
                renderingService,
                referenceService,
                new MemorySelectionPolicy(),
                new MemorySceneClassificationPolicy(),
                failureAdapter);
    }
}
