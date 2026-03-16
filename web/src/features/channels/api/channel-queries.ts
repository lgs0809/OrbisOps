import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { opsChannelService } from '../../../services/ops-channel-service';

export const channelQueryKeys = {
  all: ['channels'] as const,
  list: (projectId: string) => [...channelQueryKeys.all, 'list', projectId] as const,
  types: () => [...channelQueryKeys.all, 'types'] as const,
  readiness: (projectId: string, channelId: string) => [...channelQueryKeys.all, 'readiness', projectId, channelId] as const,
  messages: (projectId: string, channelId: string) => [...channelQueryKeys.all, 'messages', projectId, channelId] as const,
  identities: (projectId: string, channelId: string) => [...channelQueryKeys.all, 'identities', projectId, channelId] as const,
  outbox: (projectId: string) => [...channelQueryKeys.all, 'outbox', projectId] as const,
};

export const useChannelsQuery = (projectId: string) => useQuery({
  queryKey: channelQueryKeys.list(projectId),
  enabled: Boolean(projectId),
  queryFn: async () => (await opsChannelService.list(projectId)).data || [],
});

export const useChannelTypesQuery = () => useQuery({
  queryKey: channelQueryKeys.types(),
  queryFn: async () => (await opsChannelService.supportedTypes()).data || [],
  staleTime: 5 * 60_000,
});

export const useChannelReadinessQuery = (projectId: string, channelId: string, enabled = true) => useQuery({
  queryKey: channelQueryKeys.readiness(projectId, channelId),
  enabled: Boolean(projectId && channelId && enabled),
  queryFn: async () => (await opsChannelService.readiness(projectId, channelId)).data,
  refetchOnWindowFocus: false,
});

export const useSaveChannelMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ channelId, payload }: { channelId?: string; payload: Record<string, unknown> }) => (
      channelId
        ? opsChannelService.update(projectId, channelId, payload)
        : opsChannelService.create(payload)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: channelQueryKeys.list(projectId) });
    },
  });
};

export const useChannelMessagesQuery = (projectId: string, channelId: string, enabled = true) => useQuery({
  queryKey: channelQueryKeys.messages(projectId, channelId),
  enabled: Boolean(projectId && channelId && enabled),
  queryFn: async () => (await opsChannelService.messages(projectId, channelId, 100)).data || [],
});

export const useChannelIdentitiesQuery = (projectId: string, channelId: string, enabled = true) => useQuery({
  queryKey: channelQueryKeys.identities(projectId, channelId),
  enabled: Boolean(projectId && channelId && enabled),
  queryFn: async () => (await opsChannelService.identities(projectId, channelId)).data || [],
});

export const useChannelOutboxQuery = (projectId: string, enabled = true) => useQuery({
  queryKey: channelQueryKeys.outbox(projectId),
  enabled: Boolean(projectId && enabled),
  queryFn: async () => (await opsChannelService.outbox(projectId)).data || [],
});
