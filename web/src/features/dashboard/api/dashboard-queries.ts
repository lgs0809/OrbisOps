import { useQuery } from '@tanstack/react-query';

import { opsAdminService, type OpsAdminDashboardOverview } from '../../../services/ops-admin-service';
import { opsUserService, type UserDashboardOverview } from '../../../services/ops-user-service';

export interface DashboardOverviewData {
  adminOverview: OpsAdminDashboardOverview | null;
  userOverview: UserDashboardOverview | null;
  productMetrics: Record<string, any> | null;
}

export const dashboardQueryKeys = {
  all: ['dashboard-overview'] as const,
  overview: (admin: boolean) => [...dashboardQueryKeys.all, admin ? 'admin' : 'user'] as const,
};

export const useDashboardOverviewQuery = (admin: boolean) => useQuery({
  queryKey: dashboardQueryKeys.overview(admin),
  queryFn: async (): Promise<DashboardOverviewData> => {
    if (admin) {
      const [overviewResponse, metricsResult] = await Promise.all([
        opsAdminService.adminDashboardOverview(),
        opsAdminService.productMetrics().catch(() => null),
      ]);
      if (overviewResponse.code !== '0000') throw new Error(overviewResponse.info || '管理员工作台加载失败');
      const productMetrics = metricsResult?.code === '0000' && metricsResult.data && !Array.isArray(metricsResult.data)
        ? metricsResult.data
        : null;
      return { adminOverview: overviewResponse.data, userOverview: null, productMetrics };
    }
    const response = await opsUserService.dashboardOverview();
    if (response.code !== '0000') throw new Error(response.info || '工作台数据加载失败');
    return { adminOverview: null, userOverview: response.data, productMetrics: null };
  },
  staleTime: 15_000,
});
