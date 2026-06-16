import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  aiClientApiAdminService,
  type AiClientApiHealthCheckResponseDTO,
  type AiClientApiResponseDTO,
} from '../../../services/ai-client-api-admin-service';
import {
  aiClientModelAdminService,
  type AiClientModelRequestDTO,
  type AiClientModelResponseDTO,
  type AiClientModelSyncResponseDTO,
  type ModelDefaultPolicy,
} from '../../../services/ai-client-model-admin-service';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') {
    throw new Error(response.info || 'MODEL_REQUEST_FAILED');
  }
  if ((response.data === undefined || response.data === null) && fallback !== undefined) {
    return fallback;
  }
  return response.data;
};

export type ModelCatalogData = {
  providers: AiClientApiResponseDTO[];
  models: AiClientModelResponseDTO[];
  defaultPolicy: ModelDefaultPolicy;
};

export const modelQueryKeys = {
  all: ['models'] as const,
  catalog: () => [...modelQueryKeys.all, 'catalog'] as const,
};

export const useModelCatalogQuery = (emptyDefaultPolicy: () => ModelDefaultPolicy) => useQuery({
  queryKey: modelQueryKeys.catalog(),
  queryFn: async (): Promise<ModelCatalogData> => {
    const [providerResponse, modelResponse, policyResponse] = await Promise.all([
      aiClientApiAdminService.queryAllAiClientApis(),
      aiClientModelAdminService.queryAllAiClientModels(),
      aiClientModelAdminService.getDefaultPolicy(),
    ]);
    const policy = dataOf(policyResponse, emptyDefaultPolicy());
    return {
      providers: dataOf(providerResponse, []),
      models: dataOf(modelResponse, []),
      defaultPolicy: policy && Object.keys(policy).length ? policy : emptyDefaultPolicy(),
    };
  },
});

export const useUpdateDefaultModelPolicyMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (policy: ModelDefaultPolicy): Promise<ModelDefaultPolicy> => (
      dataOf(await aiClientModelAdminService.updateDefaultPolicy(policy), policy)
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: modelQueryKeys.catalog() }),
  });
};

export const useProviderHealthCheckMutation = () => useMutation({
  mutationFn: async (apiId: string): Promise<AiClientApiHealthCheckResponseDTO> => (
    dataOf(await aiClientApiAdminService.healthCheckAiClientApi(apiId))
  ),
});

export const useProviderModelSyncMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (apiId: string): Promise<AiClientModelSyncResponseDTO> => (
      dataOf(await aiClientModelAdminService.syncFromProvider(apiId))
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: modelQueryKeys.catalog() }),
  });
};

export const useSaveModelMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ payload, editing }: { payload: AiClientModelRequestDTO; editing: boolean }): Promise<boolean> => (
      editing
        ? dataOf(await aiClientModelAdminService.updateAiClientModelById(payload), false)
        : dataOf(await aiClientModelAdminService.createAiClientModel(payload), false)
    ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: modelQueryKeys.catalog() }),
  });
};
