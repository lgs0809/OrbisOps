import { useQuery } from '@tanstack/react-query';

import { AdminUserService, type AdminUserResponseDTO } from '../../../services/admin-user-service';
import { aiClientRagOrderAdminService, type RagKnowledgeBaseSummary } from '../../../services/ai-client-rag-order-admin-service';
import { opsAdminService, type OpsSkillSummary } from '../../../services/ops-admin-service';
import { opsProjectService, type OpsProjectMember } from '../../../services/ops-project-service';
import {
  opsRepairService,
  type OpsExecutionResource,
  type OpsProjectService,
  type OpsSourceRepository,
} from '../../../services/ops-repair-service';

export interface ProjectWorkspaceCapabilityData {
  projectSkills: OpsSkillSummary[];
  globalSkills: OpsSkillSummary[];
  knowledgeBases: RagKnowledgeBaseSummary[];
  members: OpsProjectMember[];
  accounts: AdminUserResponseDTO[];
}

export interface ProjectWorkspaceRuntimeData {
  repositories: OpsSourceRepository[];
  services: OpsProjectService[];
  executionResources: OpsExecutionResource[];
}

export const projectWorkspaceQueryKeys = {
  all: ['project-workspace'] as const,
  capabilities: (projectId: string) => [...projectWorkspaceQueryKeys.all, 'capabilities', projectId] as const,
  runtime: (projectId: string) => [...projectWorkspaceQueryKeys.all, 'runtime', projectId] as const,
};

export const useProjectWorkspaceCapabilitiesQuery = (projectId: string) => useQuery({
  queryKey: projectWorkspaceQueryKeys.capabilities(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<ProjectWorkspaceCapabilityData> => {
    const [skillResponse, globalSkillResponse, knowledgeResponse, memberResponse, accountResponse] = await Promise.all([
      opsAdminService.listProjectSkills(projectId),
      opsAdminService.listGlobalSkills(),
      aiClientRagOrderAdminService.listAuthorizedKnowledgeBases(projectId),
      opsProjectService.listProjectMembers(projectId),
      AdminUserService.queryEnabledUsers(),
    ]);
    return {
      projectSkills: skillResponse.data || [],
      globalSkills: globalSkillResponse.data || [],
      knowledgeBases: knowledgeResponse.data || [],
      members: memberResponse.data || [],
      accounts: accountResponse || [],
    };
  },
});

export const useProjectWorkspaceRuntimeQuery = (projectId: string) => useQuery({
  queryKey: projectWorkspaceQueryKeys.runtime(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<ProjectWorkspaceRuntimeData> => {
    const [repositoryResponse, serviceResponse, executionResourceResponse] = await Promise.all([
      opsRepairService.listRepositories(projectId),
      opsRepairService.listServices(projectId),
      opsRepairService.listExecutionResources(projectId),
    ]);
    return {
      repositories: repositoryResponse.data || [],
      services: serviceResponse.data || [],
      executionResources: executionResourceResponse.data || [],
    };
  },
});
