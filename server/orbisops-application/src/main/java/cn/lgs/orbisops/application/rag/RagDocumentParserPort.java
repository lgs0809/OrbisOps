package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;

import java.util.List;

/** Parses one uploaded resource into framework-neutral RAG chunks. */
public interface RagDocumentParserPort {

    List<RagDocument> parse(String ragName,
                            String knowledgeTag,
                            RagFileResource file,
                            RagParsePolicy parsePolicy);
}
