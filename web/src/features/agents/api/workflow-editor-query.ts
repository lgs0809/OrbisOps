import { useQuery } from '@tanstack/react-query';
import { opsAdminService, type OpsAgentDefinition } from '../../../services/ops-admin-service';

const dataOf = <T,>(response: { code: string; info?: string; data?: T }): T => {
  if (response.code !== '0000' || response.data == null) throw new Error(response.info || '加载工作流失败');
  return response.data;
};

/** Scope-keyed server state stays separate from the user's editable draft. */
export function useWorkflowEditorQuery(projectId: string, agentId: string) {
  return useQuery({
    queryKey: ['workflow-editor', projectId, agentId],
    enabled: Boolean(projectId),
    retry: false,
    refetchOnWindowFocus: false,
    queryFn: async () => {
      const [capabilities, workflows, definition, versions] = await Promise.allSettled([
        opsAdminService.getProjectAgentCapabilities(projectId),
        opsAdminService.listAgents(projectId),
        agentId ? opsAdminService.getAgent(agentId).then(dataOf) : Promise.resolve(undefined),
        agentId ? opsAdminService.listAgentVersions(agentId).then(dataOf) : Promise.resolve([] as OpsAgentDefinition[]),
      ]);
      if (definition.status === 'rejected') throw definition.reason;
      const loaded = definition.value;
      if (loaded && (loaded.agentId !== agentId || loaded.projectId !== projectId)) {
        throw new Error('工作流与当前项目不匹配，请返回列表重新选择。');
      }
      return {
        projectId, agentId, definition: loaded,
        capabilities: capabilities.status === 'fulfilled' && capabilities.value.code === '0000' ? capabilities.value.data || null : null,
        workflows: workflows.status === 'fulfilled' && workflows.value.code === '0000'
          ? (workflows.value.data || []).filter(item => item.projectId === projectId && item.definitionKind === 'SPECIALIZED_WORKFLOW'
            && String(item.lifecycle || '').toUpperCase() === 'PUBLISHED') : [],
        versions: versions.status === 'fulfilled' ? versions.value.filter(item => item.agentId === agentId && item.projectId === projectId) : [],
      };
    },
  });
}
