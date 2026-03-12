package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRagDocumentBoundaryArchitectureTest {

    private static final String APPLICATION_KNOWLEDGE =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/knowledge/";
    private static final String TRIGGER_KNOWLEDGE =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/knowledge/";
    private static final String TRIGGER_RAG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/rag/";
    private static final String CONTROLLER =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/http/admin/AiClientRagOrderAdminController.java";

    @Test
    void documentRepositoryAndAggregateResponsibilitiesMustNotReturnToRagOrderFacade() throws IOException {
        String port = read(APPLICATION_KNOWLEDGE + "KnowledgeRagDocumentPort.java");
        String applicationService = read(APPLICATION_KNOWLEDGE + "KnowledgeRagDocumentApplicationService.java");
        String documentAdapter = read(TRIGGER_KNOWLEDGE + "OpsKnowledgeRagDocumentAdapter.java");
        String aggregateAdapter = read(TRIGGER_KNOWLEDGE + "OpsKnowledgeAggregateCatalogAdapter.java");
        String legacyFacade = read(TRIGGER_RAG + "LegacyRagDocumentApplicationService.java");
        String ragOrderFacade = read(TRIGGER_RAG + "AiClientRagOrderApplicationService.java");
        String controller = read(CONTROLLER);

        assertAll(
                () -> assertTrue(port.contains("boolean deleteChunk(String chunkId);")),
                () -> assertTrue(port.contains("KnowledgeRagChunk content(String chunkId);")),
                () -> assertTrue(applicationService.contains("return port.deleteChunk(policy.requiredChunk(chunkId))")),
                () -> assertTrue(applicationService.contains("KnowledgeRagChunk chunk = port.content")),
                () -> assertTrue(documentAdapter.contains("implements KnowledgeRagDocumentPort")),
                () -> assertTrue(documentAdapter.contains("private final IRagKnowledgeRepository repository")),
                () -> assertTrue(documentAdapter.contains("repository.listDocuments")),
                () -> assertTrue(documentAdapter.contains("repository.documentContent")),
                () -> assertTrue(documentAdapter.contains("repository.deleteChunksByTag")),
                () -> assertFalse(documentAdapter.contains("AiClientRagOrderApplicationService")),
                () -> assertFalse(documentAdapter.contains("RagDocumentResponseDTO")),
                () -> assertTrue(aggregateAdapter.contains("private final RagOrderCatalogUseCase ragOrderCatalog")),
                () -> assertTrue(aggregateAdapter.contains("private final IRagKnowledgeRepository repository")),
                () -> assertTrue(aggregateAdapter.contains("ragOrderCatalog.queryAll()")),
                () -> assertTrue(aggregateAdapter.contains("repository.listKnowledgeStats()")),
                () -> assertTrue(aggregateAdapter.contains("KnowledgeAggregateSnapshot snapshot()")),
                () -> assertTrue(aggregateAdapter.contains("catch (RuntimeException ignored)")),
                () -> assertFalse(aggregateAdapter.contains("AiClientRagOrderApplicationService")),
                () -> assertTrue(legacyFacade.contains("private final KnowledgeRagDocumentApplicationService documentService")),
                () -> assertTrue(legacyFacade.contains("documentService.statistics(GLOBAL_SCOPE")),
                () -> assertTrue(legacyFacade.contains("documentService.content(fileName)")),
                () -> assertFalse(legacyFacade.contains("IRagKnowledgeRepository")),
                () -> assertFalse(legacyFacade.contains("RagKnowledgeDocumentRecord")),
                () -> assertFalse(legacyFacade.contains("com.alibaba.fastjson")),
                () -> assertTrue(ragOrderFacade.contains("private final RagOrderCatalogUseCase ragOrderCatalog")),
                () -> assertFalse(ragOrderFacade.contains("IRagKnowledgeRepository")),
                () -> assertFalse(ragOrderFacade.contains("RagDocumentResponseDTO")),
                () -> assertFalse(ragOrderFacade.contains("RagKnowledgeDocumentRecord")),
                () -> assertFalse(ragOrderFacade.contains("com.alibaba.fastjson")),
                () -> assertFalse(ragOrderFacade.contains("listKnowledgeBases")),
                () -> assertFalse(ragOrderFacade.contains("documentStats")),
                () -> assertFalse(ragOrderFacade.contains("listDocuments")),
                () -> assertFalse(ragOrderFacade.contains("documentContent")),
                () -> assertTrue(ragOrderFacade.lines().count() < 150),
                () -> assertTrue(controller.contains("KnowledgeAggregateCatalogPort knowledgeAggregateCatalogPort")),
                () -> assertTrue(controller.contains("LegacyRagDocumentApplicationService legacyRagDocumentApplicationService")),
                () -> assertTrue(controller.contains("knowledgeAggregateCatalogPort.listAggregates().stream()")),
                () -> assertTrue(controller.contains("aggregateView(KnowledgeAggregateSnapshot aggregate)")),
                () -> assertTrue(controller.contains("legacyRagDocumentApplicationService.documentStats")),
                () -> assertTrue(controller.contains("legacyRagDocumentApplicationService.listDocuments")),
                () -> assertTrue(controller.contains("legacyRagDocumentApplicationService.documentContent")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
