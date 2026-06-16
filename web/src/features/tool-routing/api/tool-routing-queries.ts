import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { opsAdminService } from '../../../services/ops-admin-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'TOOL_ROUTING_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

export interface ToolRoutingOverview {
  summary: Record<string, any> | null;
  remoteCatalogs: Record<string, any>[];
  decisions: Record<string, any>[];
  calls: Record<string, any>[];
  snapshots: Record<string, any>[];
  policies: Record<string, any>[];
  activations: Record<string, any>[];
}

export const toolRoutingQueryKeys = {
  all: ['tool-routing'] as const,
  overview: (projectId: string) => [...toolRoutingQueryKeys.all, 'overview', projectId] as const,
};

export const useToolRoutingOverviewQuery = (projectId: string) => useQuery({
  queryKey: toolRoutingQueryKeys.overview(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<ToolRoutingOverview> => {
    const [summaryResponse, decisionsResponse, callsResponse, snapshotsResponse, policiesResponse, activationsResponse, remoteCatalogsResponse] = await Promise.all([
      opsAdminService.getToolCatalogSummary(projectId),
      opsAdminService.listToolRoutingDecisions(projectId, { limit: 100 }),
      opsAdminService.listMcpToolCalls(projectId, { limit: 100 }),
      opsAdminService.listMcpToolSnapshots(projectId, { limit: 100 }),
      opsAdminService.listMcpToolPolicies(projectId, { limit: 100 }),
      opsAdminService.listMcpToolActivations(projectId, { limit: 100 }),
      opsAdminService.getRemoteMcpCatalogStatus(projectId),
    ]);
    return {
      summary: dataOf(summaryResponse, null),
      remoteCatalogs: dataOf(remoteCatalogsResponse, []),
      decisions: dataOf(decisionsResponse, []),
      calls: dataOf(callsResponse, []),
      snapshots: dataOf(snapshotsResponse, []),
      policies: dataOf(policiesResponse, []),
      activations: dataOf(activationsResponse, []),
    };
  },
});

export const useRebuildToolCatalogMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => dataOf(await opsAdminService.rebuildToolCatalogSummary(projectId), null),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: toolRoutingQueryKeys.overview(projectId) }),
  });
};

export const useSelectToolRouteMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: Record<string, any>) => dataOf(await opsAdminService.selectToolRoute(projectId, payload), null),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: toolRoutingQueryKeys.overview(projectId) }),
  });
};

export const useReviewToolPolicyMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      policyId,
      action,
      payload,
    }: {
      policyId: string;
      action: 'approve' | 'reject' | 'disable';
      payload: Record<string, any>;
    }) => {
      if (action === 'approve') {
        return dataOf(await opsAdminService.approveMcpToolPolicy(projectId, policyId, payload));
      }
      if (action === 'reject') {
        return dataOf(await opsAdminService.rejectMcpToolPolicy(projectId, policyId, payload));
      }
      return dataOf(await opsAdminService.disableMcpToolPolicy(projectId, policyId, payload));
    },
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: toolRoutingQueryKeys.overview(projectId) }),
  });
};
