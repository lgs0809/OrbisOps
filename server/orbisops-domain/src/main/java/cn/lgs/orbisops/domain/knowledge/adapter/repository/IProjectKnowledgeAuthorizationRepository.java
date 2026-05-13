package cn.lgs.orbisops.domain.knowledge.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeAuthorizationUsageCount;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorizationUsage;

import java.util.List;

public interface IProjectKnowledgeAuthorizationRepository {

    ProjectKnowledgeAuthorization save(ProjectKnowledgeAuthorization authorization);

    List<String> listEnabledKnowledgeBaseIds(String projectId);

    List<ProjectKnowledgeAuthorizationUsage> listUsageProjects(String globalKbId);

    List<KnowledgeAuthorizationUsageCount> listEnabledUsageCounts();
}
