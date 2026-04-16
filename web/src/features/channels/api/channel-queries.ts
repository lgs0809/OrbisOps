import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { AdminUserService } from '../../../services/admin-user-service';
import { opsAdminService } from '../../../services/ops-admin-service';
import { opsChannelService } from '../../../services/ops-channel-service';
import { opsProjectService } from '../../../services/ops-project-service';

export const channelQueryKeys = {
  all: ['channels'] as const,
  list: (projectId: string) => [...channelQueryKeys.all, 'list', projectId] as const,
  types: () => [...channelQueryKeys.all, 'types'] as const,
  workflowOptions: (projectId: string) => [...channelQueryKeys.all, 'workflow-options', projectId] as const,
  enabledUsers: () => [...channelQueryKeys.all, 'enabled-users'] as const,
  projectMembers: (projectId: string) => [...channelQueryKeys.all, 'project-members', projectId] as const,
  readiness: (projectId: string, channelId: string) => [...channelQueryKeys.all, 'readiness', projectId, channelId] as const,
  messageLists: (projectId: string, channelId: string) => [...channelQueryKeys.all, 'messages', projectId, channelId] as const,
  messages: (projectId: string, channelId: string, limit: number) => [...channelQueryKeys.messageLists(projectId, channelId), limit] as const,
  identities: (projectId: string, channelId: string) => [...channelQueryKeys.all, 'identities', projectId, channelId] as const,
  outboxes: () => [...channelQueryKeys.all, 'outbox'] as const,
  outbox: (projectId: string) => [...channelQueryKeys.outboxes(), projectId] as const,
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

export const useChannelWorkflowOptionsQuery = (projectId: string) => useQuery({
  queryKey: channelQueryKeys.workflowOptions(projectId),
  enabled: Boolean(projectId),
  queryFn: async () => ((await opsAdminService.listAgents(projectId)).data || [])
    .filter((agent) => agent.definitionKind === 'SPECIALIZED_WORKFLOW'),
});

export const useChannelEnabledUsersQuery = (enabled = true) => useQuery({
  queryKey: channelQueryKeys.enabledUsers(),
  enabled,
  queryFn: () => AdminUserService.queryEnabledUsers(),
  staleTime: 30_000,
});

export const useChannelProjectMembersQuery = (projectId: string, enabled = true) => useQuery({
  queryKey: channelQueryKeys.projectMembers(projectId),
  enabled: Boolean(projectId && enabled),
  queryFn: async () => (await opsProjectService.listProjectMembers(projectId)).data || [],
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

export const useChannelMessagesQuery = (projectId: string, channelId: string, limit = 100, enabled = true) => useQuery({
  queryKey: channelQueryKeys.messages(projectId, channelId, limit),
  enabled: Boolean(projectId && channelId && enabled),
  queryFn: async () => (await opsChannelService.messages(projectId, channelId, limit)).data || [],
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

export const useSendChannelTestMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ channelId, target, content }: { channelId: string; target: string; content: string }) => (
      opsChannelService.send(projectId, channelId, target, content)
    ),
    onSuccess: async (_result, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: channelQueryKeys.list(projectId) }),
        queryClient.invalidateQueries({ queryKey: channelQueryKeys.messageLists(projectId, variables.channelId) }),
        queryClient.invalidateQueries({ queryKey: channelQueryKeys.readiness(projectId, variables.channelId) }),
      ]);
    },
  });
};

export const useRecoverInboundMutation = (projectId: string, channelId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ externalMessageId, action }: { externalMessageId: string; action: 'requeue' | 'cancel' }) => (
      action === 'requeue'
        ? opsChannelService.requeueInboundRecovery(projectId, channelId, externalMessageId)
        : opsChannelService.cancelInboundRecovery(projectId, channelId, externalMessageId)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: channelQueryKeys.messageLists(projectId, channelId) });
    },
  });
};

export const useBindChannelIdentityMutation = (projectId: string, channelId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: {
      externalSenderId: string;
      platformUserId: string;
      username: string;
      status: 'ACTIVE' | 'DISABLED';
      expectedVersion?: number;
    }) => opsChannelService.bindIdentity(projectId, channelId, payload),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: channelQueryKeys.identities(projectId, channelId) }),
        queryClient.invalidateQueries({ queryKey: channelQueryKeys.readiness(projectId, channelId) }),
      ]);
    },
  });
};

export const useProcessChannelOutboxMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => opsChannelService.processOutbox(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: channelQueryKeys.outboxes() });
    },
  });
};

export const useUpdateChannelOutboxMutation = (projectId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'requeue' | 'cancel' }) => (
      action === 'requeue'
        ? opsChannelService.requeueOutbox(projectId, id)
        : opsChannelService.cancelOutbox(projectId, id)
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: channelQueryKeys.outbox(projectId) });
    },
  });
};
