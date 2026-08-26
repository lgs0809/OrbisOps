import { useQuery, useQueryClient } from '@tanstack/react-query';
import { projectSkillCatalogOptions } from './project-skill-catalog';
import { getStoredUserInfo, isAdminUser } from '../../../services/auth-session';

import { AdminUserService, type AdminUserResponseDTO } from '../../../services/admin-user-service';
import { aiClientRagOrderAdminService, type RagKnowledgeBaseSummary } from '../../../services/ai-client-rag-order-admin-service';
import { opsAdminService, type OpsSkillSummary } from '../../../services/ops-admin-service';
import { opsProjectService, type OpsProjectMember, type OpsProjectSnapshot } from '../../../services/ops-project-service';
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
  availability: {
    repositories: boolean;
    services: boolean;
    executionResources: boolean;
  };
}

export const projectWorkspaceQueryKeys = {
  all: ['project-workspace'] as const,
  capabilities: (projectId: string) => [...projectWorkspaceQueryKeys.all, 'capabilities', projectId] as const,
  runtime: (projectId: string) => [...projectWorkspaceQueryKeys.all, 'runtime', projectId] as const,
};

/** Full workspace projections are read only on the admin Project page, never by the global scope picker. */
export const loadProjectWorkspaceSnapshot = async (): Promise<OpsProjectSnapshot> => {
  const response = await opsProjectService.snapshot();
  if (response.code !== '0000' || !Array.isArray(response.data?.projects)) {
    throw new Error('项目详情暂时无法读取，请重试。');
  }
  return response.data;
};

export const useProjectWorkspaceSnapshotQuery = () => {
  const principal = getStoredUserInfo();
  return useQuery({
    queryKey: [...projectWorkspaceQueryKeys.all, 'snapshot', principal.userId || principal.username],
    enabled: isAdminUser(),
    queryFn: loadProjectWorkspaceSnapshot,
    staleTime: 15_000,
  });
};

export const useProjectWorkspaceCapabilitiesQuery = (projectId: string) => {
  const client = useQueryClient();
  return useQuery({
    queryKey: projectWorkspaceQueryKeys.capabilities(projectId),
    enabled: Boolean(projectId),
    queryFn: async (): Promise<ProjectWorkspaceCapabilityData> => {
      const [skillResponse, globalSkillResponse, knowledgeResponse, memberResponse, accountResponse] = await Promise.all([
        client.fetchQuery(projectSkillCatalogOptions(projectId)),
        opsAdminService.listGlobalSkills(),
        aiClientRagOrderAdminService.listAuthorizedKnowledgeBases(projectId),
        opsProjectService.listProjectMembers(projectId),
        AdminUserService.queryEnabledUsers(),
      ]);
      return {
        projectSkills: skillResponse || [],
        globalSkills: globalSkillResponse.data || [],
        knowledgeBases: knowledgeResponse.data || [],
        members: memberResponse.data || [],
        accounts: accountResponse || [],
      };
    },
  });
};

export const useProjectWorkspaceRuntimeQuery = (projectId: string) => useQuery({
  queryKey: projectWorkspaceQueryKeys.runtime(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<ProjectWorkspaceRuntimeData> => {
    // Read capability switches before listing. Disabled source/service
    // adapters intentionally reject list calls; querying them unconditionally
    // made the Project page turn a controlled capability boundary into a
    // misleading zero count and noisy 500s.
    const [repositoryCapability, serviceCapability, executionCapability] = await Promise.all([
      opsRepairService.getRepositoryCapabilities(),
      opsRepairService.getServiceCapabilities(),
      opsRepairService.getExecutionResourceCapabilities(),
    ]);
    const repositoriesEnabled = Boolean(repositoryCapability.data?.enabled);
    const servicesEnabled = Boolean(serviceCapability.data?.enabled);
    const executionResourcesEnabled = Boolean(executionCapability.data?.enabled);
    const [repositoryResponse, serviceResponse, executionResourceResponse] = await Promise.all([
      repositoriesEnabled ? opsRepairService.listRepositories(projectId) : Promise.resolve({ data: [] as OpsSourceRepository[] }),
      servicesEnabled ? opsRepairService.listServices(projectId) : Promise.resolve({ data: [] as OpsProjectService[] }),
      executionResourcesEnabled ? opsRepairService.listExecutionResources(projectId) : Promise.resolve({ data: [] as OpsExecutionResource[] }),
    ]);
    return {
      repositories: repositoryResponse.data || [],
      services: serviceResponse.data || [],
      executionResources: executionResourceResponse.data || [],
      availability: {
        repositories: repositoriesEnabled,
        services: servicesEnabled,
        executionResources: executionResourcesEnabled,
      },
    };
  },
});
