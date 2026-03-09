package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.agent.model.entity.RagDocument;
import cn.lgs.orbisops.domain.agent.model.entity.RagFileResource;
import cn.lgs.orbisops.domain.agent.service.rag.RagParsePolicy;

import java.util.List;

/** Parses one uploaded resource into framework-neutral RAG chunks. */
public interface RagDocumentParserPort {

    List<RagDocument> parse(String ragName,
                            String knowledgeTag,
                            RagFileResource file,
                            RagParsePolicy parsePolicy);
}
