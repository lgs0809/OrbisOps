import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { opsAdminService, type OpsAgentDefinition } from '../../../services/ops-admin-service';
import {
  loadAgentReferenceImpacts,
  type AgentReferenceImpact,
} from '../../../services/ops-agent-reference-impact-service';

export interface AgentListData {
  workflows: OpsAgentDefinition[];
  referenceImpacts: Record<string, AgentReferenceImpact>;
}

export const agentListQueryKeys = {
  all: ['specialized-workflows'] as const,
  project: (projectId: string) => [...agentListQueryKeys.all, projectId] as const,
  impact: (projectId: string, agentId: string) => [...agentListQueryKeys.all, 'impact', projectId, agentId] as const,
};

export const useAgentListQuery = (projectId: string) => useQuery({
  queryKey: agentListQueryKeys.project(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<AgentListData> => {
    const response = await opsAdminService.listAgents(projectId);
    if (response.code !== '0000') throw new Error(response.info || '加载专项 Workflow 失败');
    const workflows = (response.data || []).filter((agent) => agent.definitionKind === 'SPECIALIZED_WORKFLOW');
    const referenceImpacts = await loadAgentReferenceImpacts(
      projectId,
      workflows.map((agent) => agent.agentId || '').filter(Boolean),
    );
    return { workflows, referenceImpacts };
  },
});

export const useAgentReferenceImpactQuery = (projectId: string, agentId: string) => useQuery({
  queryKey: agentListQueryKeys.impact(projectId, agentId),
  enabled: Boolean(projectId && agentId),
  queryFn: async (): Promise<AgentReferenceImpact | undefined> => {
    const impacts = await loadAgentReferenceImpacts(projectId, [agentId]);
    return impacts[agentId];
  },
});

export const useCloneAgentMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      sourceAgentId,
      projectId,
      agentId,
      name,
    }: {
      sourceAgentId: string;
      projectId: string;
      agentId: string;
      name: string;
    }) => {
      const response = await opsAdminService.cloneAgent(sourceAgentId, { projectId, agentId, name });
      if (response.code !== '0000' || !response.data) throw new Error(response.info || '复制失败');
      return response.data;
    },
    onSuccess: async (_agent, variables) => {
      await queryClient.invalidateQueries({ queryKey: agentListQueryKeys.project(variables.projectId) });
    },
  });
};

export const useDisableAgentMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (agentId: string) => {
      const response = await opsAdminService.deleteAgent(agentId);
      if (response.code !== '0000' || !response.data) throw new Error(response.info || '停用配置失败');
      return response.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: agentListQueryKeys.project(projectId) });
    },
  });
};
