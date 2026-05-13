package cn.lgs.orbisops.domain.knowledge.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyState;

import java.util.Optional;

public interface IKnowledgeRetrievalPolicyRepository {

    Optional<KnowledgeRetrievalPolicyState> find(KnowledgeRetrievalPolicyKey key);

    KnowledgeRetrievalPolicyState save(KnowledgeRetrievalPolicyKey key,
                                       KnowledgeRetrievalPolicy policy);
}
