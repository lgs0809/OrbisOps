import { OpsAgentDefinition } from '../services/ops-admin-service';

export const isOpsCapableAgent = (agent: OpsAgentDefinition) => {
  const agentId = String(agent.agentId || '').toLowerCase();
  const description = `${agent.name || ''} ${agent.description || ''}`.toLowerCase();
  const nodes = agent.nodes || [];
  const nodeText = nodes
    .map((node) => `${node.nodeId || ''} ${node.agent || ''} ${node.description || ''} ${node.instruction || ''}`)
    .join(' ')
    .toLowerCase();
  return (
    agentId.includes('ops') ||
    description.includes('运维') ||
    description.includes('巡检') ||
    description.includes('告警') ||
    description.includes('ops') ||
    nodeText.includes('elasticsearch') ||
    nodeText.includes('prometheus') ||
    nodeText.includes('slow sql') ||
    nodes.some((node) => Boolean(node.ragEnabled) || Boolean(node.mcpIds?.length) || Boolean(node.mcpServers?.length))
  );
};

export const agentDisplayName = (agent?: OpsAgentDefinition, fallbackId?: string) => {
  if (!agent) {
    return fallbackId || '-';
  }
  return agent.name ? `${agent.name} · ${agent.agentId}` : agent.agentId;
};
