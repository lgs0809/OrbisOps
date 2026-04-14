import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsAdminService,
  type OpsAuditPolicy,
  type OpsConfigAuditRecord,
} from '../../../services/ops-admin-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'GOVERNANCE_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

const mysqlDateTime = (time: number) => new Date(time).toISOString().slice(0, 19).replace('T', ' ');

export type GovernanceAuditFilters = {
  projectId?: string;
  userId?: string;
  agentId?: string;
  module?: string;
  riskLevel?: string;
  timeFilter?: '' | '24h' | '7d' | string;
};

export const governanceQueryKeys = {
  all: ['governance'] as const,
  audits: (filters: GovernanceAuditFilters) => [...governanceQueryKeys.all, 'audits', filters] as const,
  auditLists: () => [...governanceQueryKeys.all, 'audits'] as const,
  detail: (auditId: string) => [...governanceQueryKeys.all, 'detail', auditId] as const,
  policy: (projectId?: string) => [...governanceQueryKeys.all, 'policy', projectId || 'GLOBAL'] as const,
};

export const useGovernanceAuditsQuery = (filters: GovernanceAuditFilters, enabled = true) => useQuery({
  queryKey: governanceQueryKeys.audits(filters),
  enabled,
  queryFn: async (): Promise<OpsConfigAuditRecord[]> => {
    const now = Date.now();
    const startTime = filters.timeFilter === '24h'
      ? mysqlDateTime(now - 24 * 60 * 60 * 1000)
      : filters.timeFilter === '7d'
        ? mysqlDateTime(now - 7 * 24 * 60 * 60 * 1000)
        : undefined;
    return dataOf(await opsAdminService.listConfigAudits({
      projectId: filters.projectId || undefined,
      userId: filters.userId?.trim() || undefined,
      agentId: filters.agentId?.trim() || undefined,
      module: filters.module || undefined,
      riskLevel: filters.riskLevel || undefined,
      startTime,
      limit: 200,
    }), []);
  },
});

export const useGovernanceAuditDetailQuery = (auditId: string, enabled: boolean) => useQuery({
  queryKey: governanceQueryKeys.detail(auditId),
  enabled: Boolean(enabled && auditId),
  queryFn: async () => dataOf(await opsAdminService.getConfigAudit(auditId)),
});

export const useAuditPolicyQuery = (projectId: string | undefined, enabled: boolean) => useQuery({
  queryKey: governanceQueryKeys.policy(projectId),
  enabled,
  queryFn: async (): Promise<OpsAuditPolicy> => dataOf(await opsAdminService.getAuditPolicy(projectId)),
});

export const useSaveAuditPolicyMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (policy: OpsAuditPolicy) => dataOf(await opsAdminService.updateAuditPolicy(policy)),
    onSuccess: async (saved) => {
      const projectId = saved.projectId || saved.project_id || undefined;
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: governanceQueryKeys.policy(projectId) }),
        queryClient.invalidateQueries({ queryKey: governanceQueryKeys.auditLists() }),
      ]);
    },
  });
};

export const useExportAuditMutation = () => useMutation({
  mutationFn: async (auditId: string): Promise<Record<string, any>> => (
    dataOf(await opsAdminService.exportConfigAudit(auditId), {})
  ),
});
