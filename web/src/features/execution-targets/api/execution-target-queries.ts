import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsRepairService,
  type OpsExecutionAdapterTemplate,
} from '../../../services/ops-repair-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'EXECUTION_TARGET_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

export const executionTargetQueryKeys = {
  all: ['execution-targets'] as const,
  templates: () => [...executionTargetQueryKeys.all, 'templates'] as const,
};

export const useExecutionAdapterTemplatesQuery = () => useQuery({
  queryKey: executionTargetQueryKeys.templates(),
  queryFn: async () => dataOf(await opsRepairService.listExecutionAdapterTemplates(), []),
});

export const useSaveExecutionAdapterTemplateMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      editingId,
      payload,
    }: {
      editingId?: string;
      payload: Partial<OpsExecutionAdapterTemplate>;
    }) => (
      editingId
        ? dataOf(await opsRepairService.updateExecutionAdapterTemplate(editingId, payload))
        : dataOf(await opsRepairService.createExecutionAdapterTemplate(payload))
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: executionTargetQueryKeys.templates() }),
  });
};

export const useCopyExecutionAdapterTemplateMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (templateId: string) => dataOf(await opsRepairService.copyExecutionAdapterTemplate(templateId)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: executionTargetQueryKeys.templates() }),
  });
};

export const useToggleExecutionAdapterTemplateMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ templateId, status }: { templateId: string; status: string }) => (
      dataOf(await opsRepairService.updateExecutionAdapterTemplateStatus(templateId, status))
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: executionTargetQueryKeys.templates() }),
  });
};

export const useExecutionAdapterTemplateTargetsMutation = () => useMutation({
  mutationFn: async (templateId: string): Promise<Array<Record<string, unknown>>> => (
    dataOf(await opsRepairService.listExecutionAdapterTemplateTargets(templateId), [])
  ),
});
