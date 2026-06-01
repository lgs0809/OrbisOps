import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { opsAdminService } from '../../../services/ops-admin-service';

export interface ContextMemoryFilters {
  scopeType?: string;
  scopeId?: string;
  memoryType?: string;
  status?: string;
  keyword?: string;
  limit?: number;
}

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'CONTEXT_MEMORY_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

export const memoryQueryKeys = {
  all: ['context-memories'] as const,
  lists: () => [...memoryQueryKeys.all, 'list'] as const,
  list: (filters: ContextMemoryFilters) => [...memoryQueryKeys.lists(), filters] as const,
};

export const useContextMemoriesQuery = (filters: ContextMemoryFilters) => useQuery({
  queryKey: memoryQueryKeys.list(filters),
  queryFn: async () => dataOf(await opsAdminService.listContextMemories(filters), []),
});

export const useSaveContextMemoryMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ memoryId, payload }: { memoryId?: string; payload: Record<string, any> }) => (
      memoryId
        ? dataOf(await opsAdminService.updateContextMemory(memoryId, payload))
        : dataOf(await opsAdminService.createContextMemory(payload))
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: memoryQueryKeys.lists() }),
  });
};

export const useArchiveContextMemoryMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (memoryId: string) => dataOf(await opsAdminService.updateContextMemoryStatus(memoryId, 'ARCHIVED')),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: memoryQueryKeys.lists() }),
  });
};
