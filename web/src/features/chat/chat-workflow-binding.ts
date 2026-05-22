import type { OpsAgentDefinition, OpsChatSession } from '../../services/ops-admin-service';

/** Existing conversations retain their published version when the workflow head moves. */
export const chatWorkflowBinding = (session?: OpsChatSession, workflow?: OpsAgentDefinition) => {
  if (session?.metadata?.executionType === 'WORKFLOW' && session.agentId) {
    return { agentDefinitionId: session.agentId, agentVersion: session.agentVersion, engine: session.engine || 'GRAPH' };
  }
  return { agentDefinitionId: workflow?.agentId, agentVersion: workflow?.version, engine: workflow?.engine || 'GRAPH' };
};

/** Refresh the authorized catalog before pinning a new session; existing sessions stay frozen. */
export const latestWorkflowForNewChat = async (
  workflowId: string,
  load: () => Promise<{ code?: string; data?: OpsAgentDefinition[] }>,
): Promise<OpsAgentDefinition> => {
  const response = await load();
  if (response.code !== '0000') throw new Error('刷新工作流目录失败，请重试。');
  const workflow = response.data?.find((item) => item.agentId === workflowId
    && item.definitionKind === 'SPECIALIZED_WORKFLOW' && item.lifecycle === 'PUBLISHED');
  if (!workflow || !Number.isInteger(workflow.version) || (workflow.version ?? 0) <= 0) {
    throw new Error('该工作流当前没有可用的发布版本，请重新选择。');
  }
  return workflow;
};
