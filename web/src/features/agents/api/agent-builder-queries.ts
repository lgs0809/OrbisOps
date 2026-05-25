import { useQuery } from '@tanstack/react-query';

import { AiClientModelService, type AiClientModelResponseDTO } from '../../../services/ai-client-model-service';
import { aiClientRagOrderAdminService, type RagKnowledgeBaseSummary } from '../../../services/ai-client-rag-order-admin-service';
import { aiClientToolMcpAdminService, type AiClientToolMcpResponseDTO } from '../../../services/ai-client-tool-mcp-admin-service';
import { opsAdminService, type OpsAgentCapabilitySet, type OpsSkillSummary } from '../../../services/ops-admin-service';
import { opsAgentDefinitionService } from '../../../services/ops-agent-definition-service';

export interface AgentBuilderLibraries {
  mcps: AiClientToolMcpResponseDTO[];
  knowledgeBases: RagKnowledgeBaseSummary[];
  models: AiClientModelResponseDTO[];
  skills: OpsSkillSummary[];
}

export const agentBuilderQueryKeys = {
  all: ['agent-builder'] as const,
  libraries: () => [...agentBuilderQueryKeys.all, 'libraries'] as const,
  capabilities: (projectId: string) => [...agentBuilderQueryKeys.all, 'capabilities', projectId] as const,
};

export const useAgentBuilderLibrariesQuery = (enabled = true) => useQuery({
  queryKey: agentBuilderQueryKeys.libraries(),
  enabled,
  queryFn: async (): Promise<AgentBuilderLibraries> => {
    const [mcpResult, knowledgeResult, modelResult, skillResult] = await Promise.allSettled([
      aiClientToolMcpAdminService.queryEnabledAiClientToolMcps(),
      aiClientRagOrderAdminService.listGlobalKnowledgeBases(),
      AiClientModelService.queryEnabledAiClientModels(),
      opsAdminService.listGlobalSkills(),
    ]);
    return {
      mcps: mcpResult.status === 'fulfilled' ? (mcpResult.value.data || []) : [],
      knowledgeBases: knowledgeResult.status === 'fulfilled' ? (knowledgeResult.value.data || []) : [],
      models: modelResult.status === 'fulfilled' ? (modelResult.value || []) : [],
      skills: skillResult.status === 'fulfilled' ? (skillResult.value.data || []) : [],
    };
  },
  staleTime: 30_000,
});

export const useAgentBuilderCapabilitiesQuery = (projectId: string, enabled = true) => useQuery({
  queryKey: agentBuilderQueryKeys.capabilities(projectId),
  enabled: Boolean(projectId && enabled),
  queryFn: async (): Promise<OpsAgentCapabilitySet | null> => {
    const response = await opsAgentDefinitionService.getProjectAgentCapabilities(projectId);
    return response.data || null;
  },
});
