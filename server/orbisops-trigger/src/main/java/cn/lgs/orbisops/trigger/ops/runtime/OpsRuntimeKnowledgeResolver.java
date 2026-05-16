package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/** Resolves the runtime knowledge base selection and project authorization scope. */
public final class OpsRuntimeKnowledgeResolver {

    private final Supplier<ProjectKnowledgeAuthorizationApplicationService> authorizationSupplier;

    public OpsRuntimeKnowledgeResolver(
            Supplier<ProjectKnowledgeAuthorizationApplicationService> authorizationSupplier) {
        if (authorizationSupplier == null) {
            throw new IllegalArgumentException("PROJECT_KNOWLEDGE_AUTHORIZATION_SUPPLIER_REQUIRED");
        }
        this.authorizationSupplier = authorizationSupplier;
    }

    public void resolve(OpsRuntimeResourceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        }
        ProjectKnowledgeAuthorizationApplicationService authorization =
                authorizationSupplier.get();
        if (Boolean.TRUE.equals(context.getRagEnabled())
                && !StringUtils.hasText(context.getKnowledgeBaseId())
                && authorization != null) {
            context.setKnowledgeBaseId(authorization.defaultId(context.getProjectId()));
        }
        String scope = authorization == null
                ? ""
                : authorization.scope(context.getProjectId(), context.getKnowledgeBaseId());
        if (StringUtils.hasText(context.getKnowledgeBaseId())
                && authorization != null
                && !StringUtils.hasText(scope)) {
            throw new IllegalArgumentException(
                    "知识库 " + context.getKnowledgeBaseId()
                            + " 未授权给项目 " + context.getProjectId());
        }
        context.getMetadata().put(
                "ragEnabled", Boolean.TRUE.equals(context.getRagEnabled()));
        context.getMetadata().put(
                "knowledgeBaseId", value(context.getKnowledgeBaseId()));
        context.getMetadata().put("knowledgeBaseScope", scope);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
