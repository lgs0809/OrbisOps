import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsAdminService,
  type OpsAnalysisTaskDetail,
  type OpsAnalysisTaskSummary,
} from '../../../services/ops-admin-service';
import { opsProjectService } from '../../../services/ops-project-service';

export type AnalysisTaskScope = 'admin' | 'user';
export type AnalysisTaskProjectOption = { projectId: string; name: string };

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'ANALYSIS_TASK_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

const ACTIVE_TASK_STATUSES = new Set(['PENDING', 'RUNNING', 'WAITING_APPROVAL', 'RECOVERING']);

export const hasActiveAnalysisTasks = (tasks?: OpsAnalysisTaskSummary[]) => (
  Boolean(tasks?.some((task) => ACTIVE_TASK_STATUSES.has(String(task.status || '').toUpperCase())))
);

export const analysisTaskQueryKeys = {
  all: ['analysis-tasks'] as const,
  projects: (scope: AnalysisTaskScope) => [...analysisTaskQueryKeys.all, 'projects', scope] as const,
  tasks: (scope: AnalysisTaskScope, projectId: string, source = '', status = '') => (
    [...analysisTaskQueryKeys.all, 'tasks', scope, projectId, source, status] as const
  ),
  taskListPrefix: (scope: AnalysisTaskScope, projectId: string) => (
    [...analysisTaskQueryKeys.all, 'tasks', scope, projectId] as const
  ),
  detail: (scope: AnalysisTaskScope, projectId: string, runId: string) => (
    [...analysisTaskQueryKeys.all, 'detail', scope, projectId, runId] as const
  ),
};

export const useAnalysisTaskProjectsQuery = (scope: AnalysisTaskScope) => useQuery({
  queryKey: analysisTaskQueryKeys.projects(scope),
  queryFn: async (): Promise<AnalysisTaskProjectOption[]> => {
    const values = scope === 'admin'
      ? dataOf(await opsProjectService.snapshot()).projects || []
      : dataOf(await opsAdminService.listChatProjects(), []);
    return values.map((item: any) => ({
      projectId: String(item.projectId || ''),
      name: String(item.name || item.projectId || ''),
    })).filter((item) => item.projectId);
  },
});

export const useAnalysisTasksQuery = (
  scope: AnalysisTaskScope,
  projectId: string,
  source: string,
  status: string,
) => useQuery({
  queryKey: analysisTaskQueryKeys.tasks(scope, projectId, source, status),
  enabled: Boolean(projectId),
  queryFn: async () => dataOf(await opsAdminService.listAnalysisTasks({
    projectId,
    source,
    status,
    scope,
    limit: 200,
  }), []),
  refetchInterval: (query) => hasActiveAnalysisTasks(query.state.data) ? 3000 : false,
  refetchIntervalInBackground: false,
});

export const useAnalysisTaskDetailQuery = (
  scope: AnalysisTaskScope,
  projectId: string,
  runId: string,
  enabled: boolean,
) => useQuery({
  queryKey: analysisTaskQueryKeys.detail(scope, projectId, runId),
  enabled: Boolean(enabled && projectId && runId),
  queryFn: async (): Promise<OpsAnalysisTaskDetail> => (
    dataOf(await opsAdminService.getAnalysisTask(projectId, runId, scope))
  ),
});

export const useAnalysisTaskFeedbackMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      projectId,
      runId,
      feedbackType,
      comment,
      scope,
    }: {
      projectId: string;
      runId: string;
      feedbackType: 'HELPFUL' | 'INACCURATE' | 'INSUFFICIENT_EVIDENCE';
      comment: string;
      scope: AnalysisTaskScope;
    }) => dataOf(await opsAdminService.recordAnalysisTaskFeedback(
      projectId,
      runId,
      feedbackType,
      comment,
      scope,
    )),
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: analysisTaskQueryKeys.detail(variables.scope, variables.projectId, variables.runId),
        }),
        queryClient.invalidateQueries({
          queryKey: analysisTaskQueryKeys.taskListPrefix(variables.scope, variables.projectId),
        }),
      ]);
    },
  });
};
