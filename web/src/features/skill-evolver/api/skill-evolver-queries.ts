import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { opsAdminService } from '../../../services/ops-admin-service';

export interface SkillEvolverFilters {
  status: string;
  projectId: string;
}

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'SKILL_EVOLVER_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

export const skillEvolverQueryKeys = {
  all: ['skill-evolver'] as const,
  overviews: () => [...skillEvolverQueryKeys.all, 'overview'] as const,
  overview: (filters: SkillEvolverFilters) => [...skillEvolverQueryKeys.overviews(), filters] as const,
  job: (jobId: string) => [...skillEvolverQueryKeys.all, 'job', jobId] as const,
};

export const useSkillEvolverJobQuery = (jobId: string) => useQuery({
  queryKey: skillEvolverQueryKeys.job(jobId),
  enabled: Boolean(jobId),
  refetchInterval: (query) => ['PENDING', 'RUNNING'].includes(String(query.state.data?.status || '')) ? 5000 : false,
  refetchIntervalInBackground: false,
  queryFn: async () => {
    const job = dataOf(await opsAdminService.getSkillEvolverJob(jobId));
    if (!job || String(job.jobId || job.job_id || '') !== jobId) {
      throw new Error('SKILL_EVOLVER_JOB_IDENTITY_MISMATCH');
    }
    return job;
  },
});

export const useSkillEvolverOverviewQuery = (filters: SkillEvolverFilters) => useQuery({
  queryKey: skillEvolverQueryKeys.overview(filters),
  // This workspace observes independently running workers; React Query pauses hidden-tab polling.
  refetchInterval: 10000,
  refetchIntervalInBackground: false,
  queryFn: async () => {
    const [jobResponse, patchResponse] = await Promise.all([
      opsAdminService.listSkillEvolverJobs({ status: filters.status, projectId: filters.projectId, limit: 100 }),
      opsAdminService.listSkillEvolverPatches({ projectId: filters.projectId, limit: 100 }),
    ]);
    const jobs = dataOf(jobResponse, []).filter((job) => (
      !filters.projectId || String(job.projectId || job.project_id || '') === filters.projectId
    ));
    const patches = dataOf(patchResponse, []).filter((patch) => (
      !filters.projectId || String(patch.projectId || patch.project_id || '') === filters.projectId
    ));
    return { jobs, patches };
  },
});

export const useRunSkillEvolverMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => dataOf(await opsAdminService.runSkillEvolverOnce(5)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: skillEvolverQueryKeys.overviews() }),
  });
};

export type RetryableSkillJob = {
  jobId: string; runId: string; sessionId: string; projectId: string; agentId: string;
  status: string; acceptedSourceId: string;
  savedExperience?: { currentSource?: boolean };
  authoredPublication?: unknown;
};

export const canRetrySkillJob = (job?: RetryableSkillJob): job is RetryableSkillJob => Boolean(
  job?.status === 'FAILED' && !job.authoredPublication && job.savedExperience?.currentSource !== false
  && [job.jobId, job.runId, job.sessionId, job.projectId, job.agentId, job.acceptedSourceId]
    .every((value) => typeof value === 'string' && value.trim()),
);

export const useRetrySkillEvolverMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    retry: false,
    mutationFn: async (job: RetryableSkillJob) => {
      if (!canRetrySkillJob(job)) throw new Error('SKILL_RETRY_NOT_AVAILABLE');
      const { runId, sessionId, projectId, agentId } = job;
      const queued = dataOf(await opsAdminService.createSkillEvolverJob({
        runId, sessionId, projectId, agentId, triggerReason: 'MANUAL_RETRY',
      }));
      if (queued.jobId !== job.jobId) throw new Error('SKILL_EVOLVER_JOB_IDENTITY_MISMATCH');
      const current = dataOf(await opsAdminService.getSkillEvolverJob(job.jobId));
      if ([job.jobId, runId, sessionId, projectId, agentId, job.acceptedSourceId].some(
        (value, index) => value !== [current.jobId, current.runId, current.sessionId,
          current.projectId, current.agentId, current.acceptedSourceId][index],
      )) throw new Error('SKILL_EVOLVER_JOB_IDENTITY_MISMATCH');
      if (!['PENDING', 'RUNNING', 'SKIPPED', 'NO_CHANGE', 'COMPLETED'].includes(current.status)) {
        throw new Error('SKILL_RETRY_NOT_AVAILABLE');
      }
      return current;
    },
    onSettled: async () => queryClient.invalidateQueries({ queryKey: skillEvolverQueryKeys.all }),
  });
};
