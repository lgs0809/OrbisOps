import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { opsAdminService, type OpsAgentDefinition } from '../../../services/ops-admin-service';
import { opsChannelService, type OpsChannel } from '../../../services/ops-channel-service';
import {
  taskScheduleAdminService,
  type TaskExecutionResponseDTO,
  type TaskScheduleRequestDTO,
  type TaskScheduleResponseDTO,
} from '../../../services/task-schedule-admin-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') throw new Error(response.info || 'TASK_SCHEDULE_REQUEST_FAILED');
  if ((response.data === undefined || response.data === null) && fallback !== undefined) return fallback;
  return response.data;
};

export interface TaskScheduleOptions {
  agents: OpsAgentDefinition[];
  channels: OpsChannel[];
}

export const taskScheduleQueryKeys = {
  all: ['task-schedules'] as const,
  list: (projectId: string) => [...taskScheduleQueryKeys.all, 'list', projectId] as const,
  options: (projectId: string) => [...taskScheduleQueryKeys.all, 'options', projectId] as const,
  executions: (projectId: string, scheduleId: number) => [...taskScheduleQueryKeys.all, 'executions', projectId, scheduleId] as const,
};

export const useTaskSchedulesQuery = (projectId: string) => useQuery({
  queryKey: taskScheduleQueryKeys.list(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<TaskScheduleResponseDTO[]> => dataOf(await taskScheduleAdminService.list(projectId), []),
});

export const useTaskScheduleOptionsQuery = (projectId: string) => useQuery({
  queryKey: taskScheduleQueryKeys.options(projectId),
  enabled: Boolean(projectId),
  queryFn: async (): Promise<TaskScheduleOptions> => {
    const [agentResult, channelResult] = await Promise.allSettled([
      opsAdminService.listAgents(projectId),
      opsChannelService.list(projectId),
    ]);
    return {
      agents: agentResult.status === 'fulfilled' && agentResult.value.code === '0000' ? (agentResult.value.data || []) : [],
      channels: channelResult.status === 'fulfilled' && channelResult.value.code === '0000' ? (channelResult.value.data || []) : [],
    };
  },
  staleTime: 30_000,
});

export const useTaskExecutionsQuery = (projectId: string, scheduleId?: number, enabled = true) => useQuery({
  queryKey: taskScheduleQueryKeys.executions(projectId, scheduleId || 0),
  enabled: Boolean(projectId && scheduleId && enabled),
  queryFn: async (): Promise<TaskExecutionResponseDTO[]> => (
    dataOf(await taskScheduleAdminService.listExecutions(projectId, scheduleId as number, 30), [])
  ),
  refetchInterval: enabled ? 1500 : false,
  refetchIntervalInBackground: false,
});

export const useSaveTaskScheduleMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ editing, request }: { editing: boolean; request: TaskScheduleRequestDTO }) => {
      const response = editing
        ? await taskScheduleAdminService.update(request)
        : await taskScheduleAdminService.create(request);
      return dataOf(response, false);
    },
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: taskScheduleQueryKeys.list(projectId) }),
  });
};

export const useToggleTaskScheduleMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status }: { id: number; status: number }) => dataOf(
      await taskScheduleAdminService.updateStatus(projectId, id, status),
      false,
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: taskScheduleQueryKeys.list(projectId) }),
  });
};

export const useDeleteTaskScheduleMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => dataOf(await taskScheduleAdminService.delete(projectId, id), false),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: taskScheduleQueryKeys.list(projectId) }),
  });
};

export const useRunTaskScheduleMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (scheduleId: number) => dataOf(await taskScheduleAdminService.runNow(projectId, scheduleId)),
    onSuccess: (_result, scheduleId) => {
      setTimeout(() => {
        void queryClient.invalidateQueries({ queryKey: taskScheduleQueryKeys.executions(projectId, scheduleId) });
      }, 1200);
    },
  });
};
