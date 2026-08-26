import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PlatformUserService, type PlatformUserUpdateRequest } from '../../../services/platform-user-service';
import type { AdminUserCreateRequestDTO } from '../../../services/admin-user-service';
import { getStoredUserInfo } from '../../../services/auth-session';
import { accountCredentialService } from '../../../services/account-credential-service';

export const platformUsersQueryKey = ['platform-users'] as const;

export const usePlatformUsersQuery = () => useQuery({
  queryKey: [...platformUsersQueryKey, getStoredUserInfo().userId || 'current-account'],
  queryFn: () => PlatformUserService.list(),
});

export type SavePlatformUser = { kind: 'create'; payload: AdminUserCreateRequestDTO }
  | { kind: 'update'; payload: PlatformUserUpdateRequest };

export const useSavePlatformUserMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    retry: false,
    mutationFn: (request: SavePlatformUser) => request.kind === 'create'
      ? PlatformUserService.create(request.payload) : PlatformUserService.update(request.payload),
    // A submitted mutation may finish after its dialog closes. The list must still refresh.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: platformUsersQueryKey }),
  });
};

export const useResetPlatformPasswordMutation = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: accountCredentialService.reset, retry: false,
    onSuccess: () => client.invalidateQueries({ queryKey: platformUsersQueryKey }) });
};
