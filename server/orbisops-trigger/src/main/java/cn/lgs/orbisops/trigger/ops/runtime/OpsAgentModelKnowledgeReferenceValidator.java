package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.agentdefinition.AgentModelKnowledgeReferenceValidationPort;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentReferenceSyntaxPolicy;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/** Validates configured Model and Knowledge Base references. */
public final class OpsAgentModelKnowledgeReferenceValidator
        implements AgentModelKnowledgeReferenceValidationPort {

    private final AgentReferenceSyntaxPolicy referenceSyntaxPolicy =
            new AgentReferenceSyntaxPolicy();
    private final Supplier<IAiClientModelConfigRepository> modelRepositorySupplier;
    private final Supplier<IAiClientRagOrderConfigRepository> ragRepositorySupplier;
    private final Supplier<ProjectKnowledgeAuthorizationApplicationService> authorizationSupplier;

    public OpsAgentModelKnowledgeReferenceValidator(
            Supplier<IAiClientModelConfigRepository> modelRepositorySupplier,
            Supplier<IAiClientRagOrderConfigRepository> ragRepositorySupplier,
            Supplier<ProjectKnowledgeAuthorizationApplicationService> authorizationSupplier) {
        this.modelRepositorySupplier = required(
                modelRepositorySupplier, "MODEL_REPOSITORY_SUPPLIER_REQUIRED");
        this.ragRepositorySupplier = required(
                ragRepositorySupplier, "RAG_REPOSITORY_SUPPLIER_REQUIRED");
        this.authorizationSupplier = required(
                authorizationSupplier, "KNOWLEDGE_AUTHORIZATION_SUPPLIER_REQUIRED");
    }

    public void validateModel(String id, String owner) {
        validateReference(id, owner);
        IAiClientModelConfigRepository repository = modelRepositorySupplier.get();
        if (!StringUtils.hasText(id) || repository == null) {
            return;
        }
        AiClientConfigRecord model = repository.queryByModelId(id);
        if (model == null || !enabled(model.getStatus())) {
            throw new IllegalArgumentException(
                    owner + " 引用了不存在或未启用的 Model：" + id);
        }
    }

    public void validateKnowledge(
            String id,
            String owner,
            String projectId) {
        validateReference(id, owner);
        ProjectKnowledgeAuthorizationApplicationService authorization =
                authorizationSupplier.get();
        if (StringUtils.hasText(id)
                && StringUtils.hasText(projectId)
                && authorization != null
                && !authorization.allows(projectId, id)) {
            throw new IllegalArgumentException(
                    owner + " 引用了未授权给项目 " + projectId
                            + " 的知识库：" + id);
        }
        IAiClientRagOrderConfigRepository repository = ragRepositorySupplier.get();
        if (!StringUtils.hasText(id) || repository == null) {
            return;
        }
        AiClientConfigRecord order = repository.queryByRagId(id);
        boolean found = order != null && enabled(order.getStatus());
        if (!found) {
            found = repository.queryByKnowledgeTag(id).stream()
                    .anyMatch(item -> enabled(item.getStatus()));
        }
        if (!found) {
            throw new IllegalArgumentException(
                    owner + " 引用了不存在或未启用的知识库：" + id);
        }
    }

    private void validateReference(String id, String owner) {
        referenceSyntaxPolicy.validateResourceId(id, owner);
    }

    private boolean enabled(Integer status) {
        return status != null && status == 1;
    }

    private <T> Supplier<T> required(Supplier<T> supplier, String code) {
        if (supplier == null) {
            throw new IllegalArgumentException(code);
        }
        return supplier;
    }
}
