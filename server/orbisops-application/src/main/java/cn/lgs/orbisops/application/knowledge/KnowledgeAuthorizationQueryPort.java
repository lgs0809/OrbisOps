package cn.lgs.orbisops.application.knowledge;

import java.util.List;

/** Global knowledge authorization facts published to Project Workspace. */
public interface KnowledgeAuthorizationQueryPort {

    List<String> enabledKnowledgeBaseIds(String projectId);
}
