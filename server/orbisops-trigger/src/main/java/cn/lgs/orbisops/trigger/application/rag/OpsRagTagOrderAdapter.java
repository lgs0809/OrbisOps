package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagTagOrderPort;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAgentRepository;
import cn.lgs.orbisops.domain.agent.model.valobj.AiRagOrderVO;
import org.springframework.stereotype.Component;

/** ACL isolating the legacy Agent repository/tag-order DTO from the RAG application. */
@Component
public final class OpsRagTagOrderAdapter implements RagTagOrderPort {

    private final IAgentRepository repository;

    public OpsRagTagOrderAdapter(IAgentRepository repository) {
        if (repository == null) throw new IllegalArgumentException("AGENT_REPOSITORY_REQUIRED");
        this.repository = repository;
    }

    @Override
    public void create(String ragName, String knowledgeTag) {
        AiRagOrderVO order = new AiRagOrderVO();
        order.setRagName(required(ragName, "RAG_NAME_REQUIRED"));
        order.setKnowledgeTag(required(knowledgeTag, "RAG_KNOWLEDGE_TAG_REQUIRED"));
        repository.createTagOrder(order);
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
