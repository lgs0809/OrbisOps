import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsMcpTemplateService,
  type OpsMcpTemplateRequest,
} from '../../../services/ops-mcp-template-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'MCP_TEMPLATE_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

export const mcpTemplateQueryKeys = {
  all: ['mcp-templates'] as const,
  list: () => [...mcpTemplateQueryKeys.all, 'list'] as const,
  generatedTools: (templateId: string) => [...mcpTemplateQueryKeys.all, 'generated-tools', templateId] as const,
};

export const useMcpTemplatesQuery = () => useQuery({
  queryKey: mcpTemplateQueryKeys.list(),
  queryFn: async () => dataOf(await opsMcpTemplateService.listTemplates(), []),
});

export const useMcpGeneratedToolsQuery = (templateId: string, enabled = true) => useQuery({
  queryKey: mcpTemplateQueryKeys.generatedTools(templateId),
  enabled: Boolean(templateId && enabled),
  queryFn: async () => dataOf(await opsMcpTemplateService.generatedTools(templateId), []),
});

export const useSaveMcpTemplateMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      templateId,
      request,
    }: {
      templateId?: string;
      request: OpsMcpTemplateRequest;
    }) => (
      templateId
        ? dataOf(await opsMcpTemplateService.updateTemplate(templateId, request))
        : dataOf(await opsMcpTemplateService.createTemplate(request))
    ),
    onSuccess: async (_template, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: mcpTemplateQueryKeys.list() }),
        ...(variables.templateId
          ? [queryClient.invalidateQueries({ queryKey: mcpTemplateQueryKeys.generatedTools(variables.templateId) })]
          : []),
      ]);
    },
  });
};

export const useCopyMcpTemplateMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (templateId: string) => dataOf(await opsMcpTemplateService.copyTemplate(templateId)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: mcpTemplateQueryKeys.list() }),
  });
};

export const useToggleMcpTemplateMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ templateId, status }: { templateId: string; status: string }) => (
      dataOf(await opsMcpTemplateService.updateTemplateStatus(templateId, status))
    ),
    onSuccess: async (_template, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: mcpTemplateQueryKeys.list() }),
        queryClient.invalidateQueries({ queryKey: mcpTemplateQueryKeys.generatedTools(variables.templateId) }),
      ]);
    },
  });
};
