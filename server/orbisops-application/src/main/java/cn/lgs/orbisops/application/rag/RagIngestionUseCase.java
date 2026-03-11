package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;

import java.util.List;

/** Application use case coordinating parsing, vector persistence, metadata and multimodal writes. */
public final class RagIngestionUseCase implements RagIngestionCommandUseCase {

    private static final System.Logger LOG = System.getLogger(RagIngestionUseCase.class.getName());

    private final RagDocumentParserPort parserPort;
    private final RagVectorWriterPort vectorWriterPort;
    private final RagMultimodalWriterPort multimodalWriterPort;
    private final RagTagOrderPort tagOrderPort;
    private final IRagKnowledgeRepository knowledgeRepository;

    public RagIngestionUseCase(RagDocumentParserPort parserPort,
                               RagVectorWriterPort vectorWriterPort,
                               RagMultimodalWriterPort multimodalWriterPort,
                               RagTagOrderPort tagOrderPort,
                               IRagKnowledgeRepository knowledgeRepository) {
        if (parserPort == null) throw new IllegalArgumentException("RAG_DOCUMENT_PARSER_PORT_REQUIRED");
        if (vectorWriterPort == null) throw new IllegalArgumentException("RAG_VECTOR_WRITER_PORT_REQUIRED");
        if (multimodalWriterPort == null) throw new IllegalArgumentException("RAG_MULTIMODAL_WRITER_PORT_REQUIRED");
        if (tagOrderPort == null) throw new IllegalArgumentException("RAG_TAG_ORDER_PORT_REQUIRED");
        if (knowledgeRepository == null) throw new IllegalArgumentException("RAG_KNOWLEDGE_REPOSITORY_REQUIRED");
        this.parserPort = parserPort;
        this.vectorWriterPort = vectorWriterPort;
        this.multimodalWriterPort = multimodalWriterPort;
        this.tagOrderPort = tagOrderPort;
        this.knowledgeRepository = knowledgeRepository;
    }

    @Override
    public void storeRagFile(String name, String tag, List<RagFileResource> files) {
        storeRagFile(name, tag, files, RagParsePolicy.defaults());
    }

    @Override
    public void storeRagFile(String name,
                             String tag,
                             List<RagFileResource> files,
                             RagParsePolicy parsePolicy) {
        RagParsePolicy policy = parsePolicy == null ? RagParsePolicy.defaults() : parsePolicy;
        for (RagFileResource file : safe(files)) {
            if (file == null) continue;
            List<RagDocument> documents = parserPort.parse(name, tag, file, policy);
            if (documents == null || documents.isEmpty()) {
                LOG.log(System.Logger.Level.WARNING,
                        "RAG file parsed with no chunks; skip vector write. name={0} tag={1} file={2}",
                        name, tag, file.fileName());
                continue;
            }
            vectorWriterPort.write(documents);
            persistKnowledgeMetadata(name, tag, file, documents);
            multimodalWriterPort.write(documents, file);
            createTagOrder(name, tag);
        }
    }

    private void persistKnowledgeMetadata(String name,
                                          String tag,
                                          RagFileResource file,
                                          List<RagDocument> documents) {
        try {
            String fileName = file.originalFilename() == null ? file.name() : file.originalFilename();
            knowledgeRepository.persistParsedDocument(
                    name, tag, fileName, file.contentType(), file.size(), documents);
        } catch (RuntimeException error) {
            LOG.log(System.Logger.Level.WARNING,
                    "RAG metadata persistence failed after vector write. name={0} tag={1} file={2} reason={3}",
                    name, tag, file.fileName(), error.getMessage());
        }
    }

    private void createTagOrder(String name, String tag) {
        tagOrderPort.create(name, tag);
    }

    private List<RagFileResource> safe(List<RagFileResource> files) {
        return files == null ? List.of() : files;
    }
}
