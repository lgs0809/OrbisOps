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
};

export const useSkillEvolverOverviewQuery = (filters: SkillEvolverFilters) => useQuery({
  queryKey: skillEvolverQueryKeys.overview(filters),
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
