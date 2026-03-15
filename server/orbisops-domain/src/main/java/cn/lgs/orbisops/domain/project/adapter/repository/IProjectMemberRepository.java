package cn.lgs.orbisops.domain.project.adapter.repository;

import cn.lgs.orbisops.domain.project.model.ProjectMember;
import cn.lgs.orbisops.domain.project.model.ProjectMemberIdentity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IProjectMemberRepository {

    List<ProjectMember> list(String projectId);

    Optional<ProjectMember> findEnabled(String projectId, ProjectMemberIdentity identity);

    Set<String> findEnabledProjectIds(ProjectMemberIdentity identity);

    ProjectMember grantIfAbsent(ProjectMember member);

    List<ProjectMember> replace(String projectId, List<ProjectMember> members);
}
