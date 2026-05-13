package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagIngestionUseCaseTest {

    @Test
    void coordinatesParseVectorMetadataMultimodalAndTagOrder() {
        RagDocumentParserPort parser = mock(RagDocumentParserPort.class);
        RagVectorWriterPort vector = mock(RagVectorWriterPort.class);
        RagMultimodalWriterPort multimodal = mock(RagMultimodalWriterPort.class);
        RagTagOrderPort tagOrders = mock(RagTagOrderPort.class);
        IRagKnowledgeRepository knowledge = mock(IRagKnowledgeRepository.class);
        RagFileResource file = file("runbook.md", "text/markdown", "# Runbook");
        List<RagDocument> documents = List.of(new RagDocument(
                "chunk-1", "# Runbook", Map.of("knowledge", "ops")));
        when(parser.parse("Ops", "ops", file, RagParsePolicy.defaults())).thenReturn(documents);
        RagIngestionUseCase useCase = new RagIngestionUseCase(
                parser, vector, multimodal, tagOrders, knowledge);

        useCase.storeRagFile("Ops", "ops", List.of(file), RagParsePolicy.defaults());

        verify(vector).write(documents);
        verify(knowledge).persistParsedDocument(
                "Ops", "ops", "runbook.md", "text/markdown", 9L, documents);
        verify(multimodal).write(documents, file);
        verify(tagOrders).create("Ops", "ops");
    }

    @Test
    void metadataFailureDoesNotRollbackVectorOrSkipLaterSideEffects() {
        RagDocumentParserPort parser = mock(RagDocumentParserPort.class);
        RagVectorWriterPort vector = mock(RagVectorWriterPort.class);
        RagMultimodalWriterPort multimodal = mock(RagMultimodalWriterPort.class);
        RagTagOrderPort tagOrders = mock(RagTagOrderPort.class);
        IRagKnowledgeRepository knowledge = mock(IRagKnowledgeRepository.class);
        RagFileResource file = file("runbook.md", "text/markdown", "# Runbook");
        List<RagDocument> documents = List.of(new RagDocument("chunk-1", "text", Map.of()));
        when(parser.parse("Ops", "ops", file, RagParsePolicy.defaults())).thenReturn(documents);
        doThrow(new IllegalStateException("metadata unavailable"))
                .when(knowledge).persistParsedDocument(
                        "Ops", "ops", "runbook.md", "text/markdown", 9L, documents);
        RagIngestionUseCase useCase = new RagIngestionUseCase(
                parser, vector, multimodal, tagOrders, knowledge);

        useCase.storeRagFile("Ops", "ops", List.of(file), RagParsePolicy.defaults());

        verify(vector).write(documents);
        verify(multimodal).write(documents, file);
        verify(tagOrders).create("Ops", "ops");
    }

    @Test
    void emptyParseSkipsAllPersistenceAndNullDependenciesFailFast() {
        RagDocumentParserPort parser = mock(RagDocumentParserPort.class);
        RagVectorWriterPort vector = mock(RagVectorWriterPort.class);
        RagMultimodalWriterPort multimodal = mock(RagMultimodalWriterPort.class);
        RagTagOrderPort tagOrders = mock(RagTagOrderPort.class);
        IRagKnowledgeRepository knowledge = mock(IRagKnowledgeRepository.class);
        RagFileResource file = file("empty.md", "text/markdown", "");
        when(parser.parse("Ops", "ops", file, RagParsePolicy.defaults())).thenReturn(List.of());
        RagIngestionUseCase useCase = new RagIngestionUseCase(
                parser, vector, multimodal, tagOrders, knowledge);

        useCase.storeRagFile("Ops", "ops", List.of(file), null);

        verify(vector, never()).write(org.mockito.ArgumentMatchers.anyList());
        verify(multimodal, never()).write(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        verify(tagOrders, never()).create(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        assertThrows(IllegalArgumentException.class,
                () -> new RagIngestionUseCase(null, vector, multimodal, tagOrders, knowledge));
        assertThrows(IllegalArgumentException.class,
                () -> new RagIngestionUseCase(parser, vector, multimodal, null, knowledge));
    }

    private RagFileResource file(String filename, String contentType, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new RagFileResource() {
            @Override
            public String name() {
                return "files";
            }

            @Override
            public String originalFilename() {
                return filename;
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public long size() {
                return bytes.length;
            }

            @Override
            public ByteArrayInputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }
}
