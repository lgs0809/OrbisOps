import { useQuery } from '@tanstack/react-query';

import { opsUserService, type UserAuditSummary } from '../../../services/ops-user-service';

export const userAuditQueryKeys = {
  all: ['my-audits'] as const,
  list: () => [...userAuditQueryKeys.all, 'list'] as const,
};

export const useMyAuditsQuery = () => useQuery({
  queryKey: userAuditQueryKeys.list(),
  queryFn: async (): Promise<UserAuditSummary[]> => {
    const response = await opsUserService.myAudits();
    if (response.code !== '0000') throw new Error(response.info || '我的审计加载失败');
    return response.data || [];
  },
  staleTime: 15_000,
});
