import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsAdminService,
  type OpsIncident,
  type OpsIncidentDetail,
} from '../../../services/ops-admin-service';
import { opsProjectService } from '../../../services/ops-project-service';

export type IncidentScope = 'admin' | 'user';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') throw new Error(response.info || 'INCIDENT_REQUEST_FAILED');
  if ((response.data === undefined || response.data === null) && fallback !== undefined) return fallback;
  return response.data;
};

export const incidentQueryKeys = {
  all: ['incidents'] as const,
  lists: () => [...incidentQueryKeys.all, 'list'] as const,
  list: (projectId: string, scope: IncidentScope) => [...incidentQueryKeys.lists(), projectId, scope] as const,
  details: () => [...incidentQueryKeys.all, 'detail'] as const,
  detail: (incidentId: string, scope: IncidentScope) => [...incidentQueryKeys.details(), incidentId, scope] as const,
  members: (projectId: string) => [...incidentQueryKeys.all, 'members', projectId] as const,
};

export const useIncidentsQuery = (projectId: string, scope: IncidentScope) => useQuery({
  queryKey: incidentQueryKeys.list(projectId, scope),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<OpsIncident[]> => dataOf(
    await opsAdminService.listIncidents({ projectId, limit: 200, scope }),
    [],
  ),
});

export const useIncidentDetailQuery = (incidentId: string, scope: IncidentScope) => useQuery({
  queryKey: incidentQueryKeys.detail(incidentId, scope),
  enabled: Boolean(incidentId),
  queryFn: async (): Promise<OpsIncidentDetail | null> => dataOf(
    await opsAdminService.getIncidentDetail(incidentId, scope),
    null,
  ),
});

export const useIncidentMembersQuery = (projectId: string, enabled = true) => useQuery({
  queryKey: incidentQueryKeys.members(projectId),
  enabled: Boolean(projectId && enabled),
  queryFn: async () => {
    const response = await opsProjectService.listProjectMembers(projectId);
    return dataOf(response, []).map((member) => {
      const value = String(member.userId || member.user_id || member.username || '').trim();
      const role = String(member.memberRole || member.member_role || '').trim();
      return { value, label: `${member.username || value}${role ? ` · ${role}` : ''}` };
    }).filter((item) => item.value);
  },
});

export type IncidentCommand =
  | { kind: 'close'; incidentId: string; helpful: boolean }
  | { kind: 'reopen'; incidentId: string }
  | { kind: 'feedback'; incidentId: string; projectId: string; runId: string; feedbackType: 'HELPFUL' | 'INACCURATE' | 'INSUFFICIENT_EVIDENCE' }
  | { kind: 'assign-owner'; incidentId: string; ownerUserId: string }
  | { kind: 'comment'; incidentId: string; content: string }
  | { kind: 'watcher'; incidentId: string; action: 'add' | 'remove'; userId: string; adminOverride?: boolean }
  | { kind: 'relation'; incidentId: string; action: 'add' | 'remove'; relatedIncidentId: string }
  | { kind: 'verify'; incidentId: string; packageId: string };

export const useIncidentCommandMutation = (scope: IncidentScope) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (command: IncidentCommand): Promise<any> => {
      if (command.kind === 'close') {
        if (command.helpful) await opsAdminService.confirmIncidentHelpful(command.incidentId, scope);
        return dataOf(await opsAdminService.updateIncidentStatus(
          command.incidentId,
          'CLOSED',
          'user',
          command.helpful ? '用户确认有帮助并关闭事件' : '用户确认恢复结果并关闭事件',
          scope,
        ));
      }
      if (command.kind === 'reopen') {
        return dataOf(await opsAdminService.updateIncidentStatus(
          command.incidentId,
          'OPEN',
          'user',
          '用户确认问题再次发生，重新打开事件',
          scope,
        ));
      }
      if (command.kind === 'feedback') {
        return dataOf(await opsAdminService.recordAnalysisTaskFeedback(
          command.projectId,
          command.runId,
          command.feedbackType,
          '',
          scope,
        ));
      }
      if (command.kind === 'assign-owner') {
        return dataOf(await opsAdminService.assignIncidentOwner(command.incidentId, command.ownerUserId, scope));
      }
      if (command.kind === 'comment') {
        return dataOf(await opsAdminService.addIncidentComment(command.incidentId, command.content, scope));
      }
      if (command.kind === 'watcher') {
        const commandScope: IncidentScope = command.adminOverride ? 'admin' : scope;
        return command.action === 'add'
          ? dataOf(await opsAdminService.addIncidentWatcher(command.incidentId, command.userId, commandScope))
          : dataOf(await opsAdminService.removeIncidentWatcher(command.incidentId, command.userId, commandScope));
      }
      if (command.kind === 'relation') {
        return command.action === 'add'
          ? dataOf(await opsAdminService.relateIncident(command.incidentId, command.relatedIncidentId, scope))
          : dataOf(await opsAdminService.unrelateIncident(command.incidentId, command.relatedIncidentId, scope));
      }
      return dataOf(await opsAdminService.verifyIncident(command.incidentId, command.packageId, scope));
    },
    onSuccess: async (_result, command) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: incidentQueryKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: incidentQueryKeys.detail(command.incidentId, scope) }),
      ]);
    },
  });
};
