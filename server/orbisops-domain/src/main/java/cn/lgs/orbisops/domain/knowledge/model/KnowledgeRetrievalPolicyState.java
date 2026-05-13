package cn.lgs.orbisops.domain.knowledge.model;

public record KnowledgeRetrievalPolicyState(
        Long id,
        KnowledgeRetrievalPolicyKey key,
        KnowledgeRetrievalPolicy policy,
        String updatedAt
) {

    public KnowledgeRetrievalPolicyState {
        if (key == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_POLICY_KEY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_POLICY_REQUIRED");
        updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public static KnowledgeRetrievalPolicyState transientState(
            KnowledgeRetrievalPolicyKey key,
            KnowledgeRetrievalPolicy policy) {
        return new KnowledgeRetrievalPolicyState(null, key, policy, "");
    }
}
