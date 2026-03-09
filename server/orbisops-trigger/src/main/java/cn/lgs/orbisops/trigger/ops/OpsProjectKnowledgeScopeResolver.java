package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;

import java.util.function.Function;

/** ACL from project definitions to the validated knowledge scope used by RAG. */
public final class OpsProjectKnowledgeScopeResolver {

    private static final String SAFE_KNOWLEDGE_ID = "[A-Za-z0-9_.:-]+";

    private final Function<String, String> knowledgeBaseLookup;

    public OpsProjectKnowledgeScopeResolver(ProjectKnowledgeAuthorizationApplicationService authorizationService) {
        this(projectKnowledgeAuthorizationLookup(authorizationService));
    }

    OpsProjectKnowledgeScopeResolver(Function<String, String> knowledgeBaseLookup) {
        this.knowledgeBaseLookup = knowledgeBaseLookup == null ? projectId -> "" : knowledgeBaseLookup;
    }

    public static OpsProjectKnowledgeScopeResolver unavailable() {
        return new OpsProjectKnowledgeScopeResolver(projectId -> "");
    }

    public String resolveKnowledgeBaseId(OpsAgentRunRequestDTO request) {
        if (request == null || !hasText(request.getProjectId())) {
            return "";
        }
        String knowledgeBaseId = knowledgeBaseLookup.apply(request.getProjectId().trim());
        String normalized = knowledgeBaseId == null ? "" : knowledgeBaseId.trim();
        return normalized.matches(SAFE_KNOWLEDGE_ID) ? normalized : "";
    }

    public String filterFor(String knowledgeBaseId) {
        if (!hasText(knowledgeBaseId) || !knowledgeBaseId.matches(SAFE_KNOWLEDGE_ID)) {
            throw new IllegalArgumentException("KNOWLEDGE_BASE_SCOPE_INVALID");
        }
        return "knowledge == '" + knowledgeBaseId + "'";
    }

    private static Function<String, String> projectKnowledgeAuthorizationLookup(
            ProjectKnowledgeAuthorizationApplicationService authorizationService) {
        return authorizationService == null
                ? projectId -> ""
                : authorizationService::defaultId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
