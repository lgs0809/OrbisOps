import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsAdminService,
  type OpsAgentDefinition,
  type OpsAlertTriggerEvent,
  type OpsAlertTriggerRule,
  type OpsIncident,
} from '../../../services/ops-admin-service';
import { opsChannelService, type OpsChannel } from '../../../services/ops-channel-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') throw new Error(response.info || 'ALERT_TRIGGER_REQUEST_FAILED');
  if ((response.data === undefined || response.data === null) && fallback !== undefined) return fallback;
  return response.data;
};

export interface AlertTriggerCatalog {
  rules: OpsAlertTriggerRule[];
  events: OpsAlertTriggerEvent[];
}

export interface AlertTriggerProjectOptions {
  agents: OpsAgentDefinition[];
  channels: OpsChannel[];
  incidents: OpsIncident[];
  incidentLoadFailed: boolean;
}

export const alertTriggerQueryKeys = {
  all: ['alert-triggers'] as const,
  catalog: () => [...alertTriggerQueryKeys.all, 'catalog'] as const,
  options: (projectId: string) => [...alertTriggerQueryKeys.all, 'options', projectId] as const,
};

export const useAlertTriggerCatalogQuery = () => useQuery({
  queryKey: alertTriggerQueryKeys.catalog(),
  queryFn: async (): Promise<AlertTriggerCatalog> => {
    const [rulesResponse, eventsResponse] = await Promise.all([
      opsAdminService.listAlertTriggerRules(),
      opsAdminService.listAlertTriggerEvents(50),
    ]);
    return {
      rules: dataOf(rulesResponse, []),
      events: dataOf(eventsResponse, []),
    };
  },
});

export const useAlertTriggerProjectOptionsQuery = (projectId: string) => useQuery({
  queryKey: alertTriggerQueryKeys.options(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<AlertTriggerProjectOptions> => {
    const [agentResult, channelResult, incidentResult] = await Promise.allSettled([
      opsAdminService.listAgents(projectId),
      opsChannelService.list(projectId),
      opsAdminService.listIncidents({ projectId, limit: 200 }),
    ]);
    return {
      agents: agentResult.status === 'fulfilled' && agentResult.value.code === '0000' ? (agentResult.value.data || []) : [],
      channels: channelResult.status === 'fulfilled' && channelResult.value.code === '0000' ? (channelResult.value.data || []) : [],
      incidents: incidentResult.status === 'fulfilled' && incidentResult.value.code === '0000' ? (incidentResult.value.data || []) : [],
      incidentLoadFailed: incidentResult.status === 'rejected' || (incidentResult.status === 'fulfilled' && incidentResult.value.code !== '0000'),
    };
  },
  staleTime: 15_000,
});

export const useSaveAlertTriggerRuleMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (rule: OpsAlertTriggerRule) => dataOf(await opsAdminService.saveAlertTriggerRule(rule)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: alertTriggerQueryKeys.catalog() }),
  });
};

export const useToggleAlertTriggerRuleMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status }: { id: number; status: number }) => (
      dataOf(await opsAdminService.updateAlertTriggerRuleStatus(id, status))
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: alertTriggerQueryKeys.catalog() }),
  });
};

export const useDeleteAlertTriggerRuleMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => dataOf(await opsAdminService.deleteAlertTriggerRule(id)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: alertTriggerQueryKeys.catalog() }),
  });
};
