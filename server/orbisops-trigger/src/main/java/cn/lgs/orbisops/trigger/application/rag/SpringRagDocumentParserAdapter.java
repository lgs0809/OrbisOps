package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagDocumentParserPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.trigger.ops.rag.RagDocumentParser;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class SpringRagDocumentParserAdapter implements RagDocumentParserPort {

    private final RagDocumentParser parser;

    public SpringRagDocumentParserAdapter(RagDocumentParser parser) {
        if (parser == null) throw new IllegalArgumentException("RAG_DOCUMENT_PARSER_REQUIRED");
        this.parser = parser;
    }

    @Override
    public List<RagDocument> parse(String ragName,
                                   String knowledgeTag,
                                   RagFileResource file,
                                   RagParsePolicy parsePolicy) {
        return parser.parse(ragName, knowledgeTag, file, parsePolicy).stream()
                .map(document -> new RagDocument(
                        document.getId(), document.getText(), document.getMetadata()))
                .toList();
    }
}
