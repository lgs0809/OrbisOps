import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  aiClientRagOrderAdminService,
  ApiResponse,
  RagEvalCase,
  RagFeedbackRecord,
  RagKnowledgeBaseSummary,
  RagQualityProbeRequest,
} from '../../../services/ai-client-rag-order-admin-service';
import { activeIngestionJobCount, RetrievalPolicyPayload } from '../model/knowledge-model';

const dataOf = <T,>(response: ApiResponse<T>, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'KNOWLEDGE_REQUEST_FAILED');
  }
  if (response.data === undefined || response.data === null) {
    if (fallback !== undefined) return fallback;
  }
  return response.data;
};

export const knowledgeQueryKeys = {
  all: ['knowledge'] as const,
  globalBases: () => [...knowledgeQueryKeys.all, 'global-bases'] as const,
  stats: (kbId: string) => [...knowledgeQueryKeys.all, 'stats', kbId || 'all'] as const,
  chunks: (kbId: string) => [...knowledgeQueryKeys.all, 'chunks', kbId] as const,
  usage: (kbId: string) => [...knowledgeQueryKeys.all, 'usage', kbId] as const,
  jobs: () => [...knowledgeQueryKeys.all, 'ingestion-jobs'] as const,
  policy: (kbId: string) => [...knowledgeQueryKeys.all, 'policy', kbId] as const,
  evalCases: () => [...knowledgeQueryKeys.all, 'eval-cases'] as const,
  feedback: (kbId: string) => [...knowledgeQueryKeys.all, 'feedback', kbId || 'all'] as const,
  gaps: (kbId: string) => [...knowledgeQueryKeys.all, 'gaps', kbId || 'all'] as const,
};

export const useGlobalKnowledgeBasesQuery = () => useQuery({
  queryKey: knowledgeQueryKeys.globalBases(),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.listGlobalKnowledgeBases(), []),
});

export const useKnowledgeStatsQuery = (kbId: string) => useQuery({
  queryKey: knowledgeQueryKeys.stats(kbId),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.getGlobalKnowledgeStats(kbId || undefined)),
});

export const useKnowledgeChunksQuery = (kbId: string) => useQuery({
  queryKey: knowledgeQueryKeys.chunks(kbId),
  enabled: Boolean(kbId),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.listGlobalKnowledgeChunks(kbId, 200), []),
});

export const useKnowledgeUsageProjectsQuery = (kbId: string) => useQuery({
  queryKey: knowledgeQueryKeys.usage(kbId),
  enabled: Boolean(kbId),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.listGlobalKnowledgeBaseUsageProjects(kbId), []),
});

export const useKnowledgeIngestionJobsQuery = () => useQuery({
  queryKey: knowledgeQueryKeys.jobs(),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.listIngestionJobs(20), []),
  refetchInterval: (query) => activeIngestionJobCount(query.state.data || []) > 0 ? 3000 : false,
  refetchIntervalInBackground: false,
});

export const useLoadKnowledgePolicyMutation = () => useMutation({
  mutationFn: async (kbId: string) => dataOf(await aiClientRagOrderAdminService.getGlobalRetrievalPolicy(kbId)),
});

export const useLoadKnowledgeDocumentContentMutation = () => useMutation({
  mutationFn: async (fileName: string) => dataOf(await aiClientRagOrderAdminService.queryRagDocumentContent(fileName)),
});

export const useRagEvalCasesQuery = () => useQuery({
  queryKey: knowledgeQueryKeys.evalCases(),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.listRagEvalCases(), []),
});

export const useRagFeedbackQuery = (kbId: string) => useQuery({
  queryKey: knowledgeQueryKeys.feedback(kbId),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.listRagFeedback({ knowledgeTag: kbId || undefined, limit: 30 }), []),
});

export const useRagKnowledgeGapsQuery = (kbId: string) => useQuery({
  queryKey: knowledgeQueryKeys.gaps(kbId),
  queryFn: async () => dataOf(await aiClientRagOrderAdminService.listRagKnowledgeGaps({ knowledgeTag: kbId || undefined, limit: 50 }), []),
});

export const useSaveGlobalKnowledgeMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ editingKbId, payload }: { editingKbId?: string; payload: Partial<RagKnowledgeBaseSummary> }) => (
      editingKbId
        ? dataOf(await aiClientRagOrderAdminService.updateGlobalKnowledgeBase(editingKbId, payload))
        : dataOf(await aiClientRagOrderAdminService.createGlobalKnowledgeBase(payload))
    ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.globalBases() }),
        queryClient.invalidateQueries({ queryKey: [...knowledgeQueryKeys.all, 'stats'] }),
      ]);
    },
  });
};

export const useToggleGlobalKnowledgeMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ kbId, status }: { kbId: string; status: string }) => (
      dataOf(await aiClientRagOrderAdminService.updateGlobalKnowledgeBaseStatus(kbId, status))
    ),
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.globalBases() }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.stats(variables.kbId) }),
      ]);
    },
  });
};

export const useUploadKnowledgeDocumentsMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ kbId, name, files }: { kbId: string; name: string; files: File[] }) => (
      dataOf(await aiClientRagOrderAdminService.uploadGlobalKnowledgeDocuments(kbId, name, files))
    ),
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.globalBases() }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.jobs() }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.chunks(variables.kbId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.stats(variables.kbId) }),
      ]);
    },
  });
};

export const useDeleteKnowledgeChunkMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ kbId, chunkId }: { kbId: string; chunkId: string }) => (
      dataOf(await aiClientRagOrderAdminService.deleteGlobalKnowledgeChunk(kbId, chunkId))
    ),
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.chunks(variables.kbId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.stats(variables.kbId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.globalBases() }),
      ]);
    },
  });
};

export const useDeleteKnowledgeChunksMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (kbId: string) => dataOf(await aiClientRagOrderAdminService.deleteGlobalKnowledgeChunks(kbId)),
    onSuccess: async (_data, kbId) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.chunks(kbId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.stats(kbId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.globalBases() }),
      ]);
    },
  });
};

export const useUpdateKnowledgePolicyMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ kbId, payload }: { kbId: string; payload: RetrievalPolicyPayload }) => (
      dataOf(await aiClientRagOrderAdminService.updateGlobalRetrievalPolicy(kbId, payload))
    ),
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.policy(variables.kbId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.globalBases() }),
      ]);
    },
  });
};

export const useProbeRagQualityMutation = () => useMutation({
  mutationFn: async (request: RagQualityProbeRequest) => dataOf(await aiClientRagOrderAdminService.probeRagQuality(request)),
});

export const useRunRagEvalMutation = () => useMutation({
  mutationFn: async () => dataOf(await aiClientRagOrderAdminService.runRagEval()),
});

export const useSaveRagEvalCaseMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: RagEvalCase) => dataOf(await aiClientRagOrderAdminService.saveRagEvalCase(request)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.evalCases() }),
  });
};

export const useDeleteRagEvalCaseMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => dataOf(await aiClientRagOrderAdminService.deleteRagEvalCase(id)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.evalCases() }),
  });
};

export const useSubmitRagFeedbackMutation = (kbId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: RagFeedbackRecord) => dataOf(await aiClientRagOrderAdminService.submitRagFeedback(request)),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.feedback(kbId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.gaps(kbId) }),
      ]);
    },
  });
};

export const useUpdateKnowledgeGapMutation = (kbId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, status }: { id: number; status: string }) => dataOf(await aiClientRagOrderAdminService.updateRagKnowledgeGapStatus(id, status)),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.gaps(kbId) }),
  });
};

export const useGapToEvalCaseMutation = (kbId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => dataOf(await aiClientRagOrderAdminService.saveRagKnowledgeGapAsEvalCase(id)),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.evalCases() }),
        queryClient.invalidateQueries({ queryKey: knowledgeQueryKeys.gaps(kbId) }),
      ]);
    },
  });
};

