import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsAdminService,
  type OpsSkillArtifact,
  type OpsSkillDetail,
  type OpsSkillReference,
  type OpsSkillSummary,
} from '../../../services/ops-admin-service';
import { opsProjectService, type OpsProjectWorkspace } from '../../../services/ops-project-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'SKILL_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) return fallback;
  return response.data;
};

export interface SkillCatalogData {
  catalogSkills: OpsSkillSummary[];
  builtInSkills: OpsSkillSummary[];
}

export interface SkillDetailInput {
  skillId: string;
  sourceName: string;
  catalogManaged: boolean;
  summary?: OpsSkillSummary;
}

export const skillQueryKeys = {
  all: ['skills'] as const,
  catalog: () => [...skillQueryKeys.all, 'catalog'] as const,
  details: () => [...skillQueryKeys.all, 'detail'] as const,
  detail: (skillId: string, catalogManaged: boolean) => [...skillQueryKeys.details(), skillId, catalogManaged] as const,
  contexts: () => [...skillQueryKeys.all, 'context'] as const,
  context: (sourceName: string) => [...skillQueryKeys.contexts(), sourceName] as const,
  usages: () => [...skillQueryKeys.all, 'usage'] as const,
  usage: (skillId: string) => [...skillQueryKeys.usages(), skillId] as const,
  versions: () => [...skillQueryKeys.all, 'versions'] as const,
  versionList: (skillId: string) => [...skillQueryKeys.versions(), skillId] as const,
};

export const useSkillCatalogQuery = (enabled = true) => useQuery({
  queryKey: skillQueryKeys.catalog(),
  enabled,
  queryFn: async (): Promise<SkillCatalogData> => {
    const [catalogResult, builtInResult] = await Promise.all([
      opsAdminService.listGlobalSkills(),
      opsAdminService.listSkills(),
    ]);
    return {
      catalogSkills: dataOf(catalogResult, []),
      builtInSkills: dataOf(builtInResult, []),
    };
  },
  staleTime: 30_000,
  refetchOnWindowFocus: false,
});

export const useReloadSkillsMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<SkillCatalogData> => {
      const [catalogResult, builtInResult] = await Promise.all([
        opsAdminService.listGlobalSkills(),
        opsAdminService.reloadSkills(),
      ]);
      return {
        catalogSkills: dataOf(catalogResult, []),
        builtInSkills: dataOf(builtInResult, []),
      };
    },
    onSuccess: async (data) => {
      queryClient.setQueryData(skillQueryKeys.catalog(), data);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: skillQueryKeys.details() }),
        queryClient.invalidateQueries({ queryKey: skillQueryKeys.contexts() }),
      ]);
    },
  });
};

export const useSkillDetailQuery = (input?: SkillDetailInput) => useQuery({
  queryKey: skillQueryKeys.detail(input?.skillId || '', Boolean(input?.catalogManaged)),
  enabled: Boolean(input?.skillId && input?.sourceName),
  queryFn: async (): Promise<OpsSkillDetail | null> => {
    if (!input) return null;
    const detailResult = input.catalogManaged
      ? await opsAdminService.getGlobalSkill(input.skillId)
      : await opsAdminService.getSkill(input.sourceName);
    const detail = dataOf(detailResult, null);
    if (!detail) return null;
    const artifacts: OpsSkillArtifact[] = input.catalogManaged
      ? dataOf(await opsAdminService.listGlobalSkillArtifacts(input.skillId), [])
      : [];
    return {
      ...input.summary,
      ...detail,
      catalogManaged: input.catalogManaged,
      builtIn: !input.catalogManaged,
      origin: input.catalogManaged ? detail.origin : 'BUILT_IN',
      updateMode: input.catalogManaged ? detail.updateMode : 'MANUAL_ONLY',
      autoUpdateEnabled: input.catalogManaged ? detail.autoUpdateEnabled : false,
      autoMergeEnabled: input.catalogManaged ? detail.autoMergeEnabled : false,
      artifacts,
    };
  },
  staleTime: 30_000,
  refetchOnWindowFocus: false,
});

export const useSkillContextQuery = (sourceName: string, enabled = true) => useQuery({
  queryKey: skillQueryKeys.context(sourceName),
  enabled: Boolean(sourceName && enabled),
  queryFn: async () => dataOf(await opsAdminService.renderSkillContext([sourceName], 12000), { names: [], content: '', length: 0 }).content || '',
  staleTime: 30_000,
  refetchOnWindowFocus: false,
});

export const useSkillUsageQuery = (skillId: string, enabled = true) => useQuery({
  queryKey: skillQueryKeys.usage(skillId),
  enabled: Boolean(skillId && enabled),
  queryFn: async (): Promise<OpsSkillReference[]> => dataOf(await opsAdminService.listGlobalSkillUsage(skillId), []),
});

export const useSkillVersionsQuery = (skillId: string, enabled = true) => useQuery({
  queryKey: skillQueryKeys.versionList(skillId),
  enabled: Boolean(skillId && enabled),
  queryFn: async (): Promise<Record<string, any>[]> => dataOf(await opsAdminService.listGlobalSkillVersions(skillId), []),
});

export const useSkillCopyProjectsQuery = (enabled = true) => useQuery({
  queryKey: [...skillQueryKeys.all, 'copy-projects'] as const,
  enabled,
  queryFn: async (): Promise<OpsProjectWorkspace[]> => dataOf(await opsProjectService.snapshot(), { projects: [], templates: [] }).projects || [],
  staleTime: 30_000,
});

const invalidateSkill = async (queryClient: ReturnType<typeof useQueryClient>, skillId?: string, sourceName?: string) => {
  const tasks: Promise<unknown>[] = [queryClient.invalidateQueries({ queryKey: skillQueryKeys.catalog() })];
  if (skillId) {
    tasks.push(queryClient.invalidateQueries({ queryKey: skillQueryKeys.details() }));
    tasks.push(queryClient.invalidateQueries({ queryKey: skillQueryKeys.versionList(skillId) }));
    tasks.push(queryClient.invalidateQueries({ queryKey: skillQueryKeys.usage(skillId) }));
  }
  if (sourceName) tasks.push(queryClient.invalidateQueries({ queryKey: skillQueryKeys.context(sourceName) }));
  await Promise.all(tasks);
};

export const useSaveGlobalSkillMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ skillId, payload }: { skillId: string; payload: Record<string, any> }) => (
      dataOf(await opsAdminService.updateGlobalSkill(skillId, payload))
    ),
    onSuccess: async (skill, variables) => invalidateSkill(queryClient, variables.skillId, skill?.name || variables.skillId),
  });
};

export interface CreateGlobalSkillPayload {
  skillId: string;
  name?: string;
  description?: string;
  category: string;
  subcategory?: string;
  whenToUse: string[];
  whenNotToUse: string[];
  keywords?: string[];
  content?: string;
  status?: string;
  artifacts?: OpsSkillArtifact[];
  evalSuites?: string[];
}

export const useCreateGlobalSkillMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: CreateGlobalSkillPayload) => dataOf(await opsAdminService.createGlobalSkill(payload)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: skillQueryKeys.catalog() }),
  });
};

export const useUpdateGlobalSkillStatusMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ skillId, status }: { skillId: string; status: string }) => (
      dataOf(await opsAdminService.updateGlobalSkillStatus(skillId, status))
    ),
    onSuccess: async (skill, variables) => invalidateSkill(queryClient, variables.skillId, skill?.name || variables.skillId),
  });
};

export const useUpdateGlobalSkillModeMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ skillId, payload }: { skillId: string; payload: Record<string, any> }) => (
      dataOf(await opsAdminService.updateGlobalSkillUpdateMode(skillId, payload))
    ),
    onSuccess: async (skill, variables) => invalidateSkill(queryClient, variables.skillId, skill?.name || variables.skillId),
  });
};

export const useRollbackGlobalSkillMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ skillId, version }: { skillId: string; version: number }) => (
      dataOf(await opsAdminService.rollbackGlobalSkillVersion(skillId, version))
    ),
    onSuccess: async (skill, variables) => invalidateSkill(queryClient, variables.skillId, skill?.name || variables.skillId),
  });
};

export interface CopyGlobalSkillPayload {
  globalSkillId: string;
  skillId?: string;
  name?: string;
  description?: string;
}

export const useCopyGlobalSkillMutation = () => useMutation({
  mutationFn: async ({ projectId, payload }: { projectId: string; payload: CopyGlobalSkillPayload }) => (
    dataOf(await opsAdminService.copyGlobalSkillToProject(projectId, payload))
  ),
});
