import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  opsChangePackageService,
  type ChangePackageListParams,
  type OpsExecutionScope,
} from '../../../services/ops-change-package-service';
import type {
  OpsChangePackage,
  OpsChangePackageEvent,
  OpsLandingOperationRun,
} from '../../../services/ops-change-package-types';

const dataOf = <T,>(response: { code: string; info?: string; data: T }, fallback?: T): T => {
  if (response.code !== '0000') throw new Error(response.info || 'CHANGE_PACKAGE_REQUEST_FAILED');
  if ((response.data === undefined || response.data === null) && fallback !== undefined) return fallback;
  return response.data;
};

export interface ChangePackageListInput {
  principalKey: string;
  scope: OpsExecutionScope;
  params: ChangePackageListParams;
  enabled: boolean;
}

export interface ChangePackageDetailData {
  package: OpsChangePackage;
  events: OpsChangePackageEvent[];
  landingOperations: OpsLandingOperationRun[];
}

export const changePackageQueryKeys = {
  all: ['change-packages'] as const,
  lists: () => [...changePackageQueryKeys.all, 'list'] as const,
  list: (input: ChangePackageListInput) => [
    ...changePackageQueryKeys.lists(),
    input.principalKey,
    input.scope,
    input.params.projectId || '',
    input.params.status || '',
    input.params.incidentId || '',
    input.params.sessionId || '',
  ] as const,
  details: () => [...changePackageQueryKeys.all, 'detail'] as const,
  detail: (packageId: string, scope: OpsExecutionScope) => [...changePackageQueryKeys.details(), packageId, scope] as const,
};

export const useChangePackagesQuery = (input: ChangePackageListInput) => useQuery({
  queryKey: changePackageQueryKeys.list(input),
  enabled: input.enabled,
  queryFn: async (): Promise<OpsChangePackage[]> => {
    const response = await opsChangePackageService.list(input.params);
    const items = dataOf(response, []);
    const requestedIncidentId = input.params.incidentId;
    return items.filter((item) => (
      !requestedIncidentId || input.scope === 'admin' || item.incidentId === requestedIncidentId
    ));
  },
});

export const useChangePackageDetailQuery = (
  packageId: string,
  scope: OpsExecutionScope,
  enabled = true,
) => useQuery({
  queryKey: changePackageQueryKeys.detail(packageId, scope),
  enabled: Boolean(packageId && enabled),
  refetchInterval: enabled ? 5000 : false,
  queryFn: async (): Promise<ChangePackageDetailData> => {
    const [detailResponse, eventsResponse, operationResponse] = await Promise.all([
      opsChangePackageService.get(packageId, scope),
      opsChangePackageService.listEvents(packageId, scope, 100),
      opsChangePackageService.listLandingOperationRuns(packageId, scope, 200),
    ]);
    return {
      package: dataOf(detailResponse),
      events: dataOf(eventsResponse, []),
      landingOperations: dataOf(operationResponse, []),
    };
  },
});

export type ChangePackageAction =
  | { kind: 'validate'; packageId: string; reason: string }
  | { kind: 'submit-review'; packageId: string; summary: string }
  | { kind: 'approve'; packageId: string; version: number; packageHash: string }
  | { kind: 'reject'; packageId: string; reason: string }
  | {
      kind: 'land';
      packageId: string;
      version: number;
      packageHash: string;
      approvedPackageHash: string;
      idempotencyKey?: string;
    }
  | { kind: 'cleanup'; packageId: string; reason: string };

export const useChangePackageActionMutation = (scope: OpsExecutionScope) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (action: ChangePackageAction) => {
      if (action.kind === 'validate') {
        return dataOf(await opsChangePackageService.validate(action.packageId, { reason: action.reason }, scope));
      }
      if (action.kind === 'submit-review') {
        return dataOf(await opsChangePackageService.submitReview(action.packageId, { summary: action.summary }, scope));
      }
      if (action.kind === 'approve') {
        return dataOf(await opsChangePackageService.approve(action.packageId, action.version, action.packageHash, scope));
      }
      if (action.kind === 'reject') {
        return dataOf(await opsChangePackageService.reject(
          action.packageId,
          { reason: action.reason, comment: action.reason },
          scope,
        ));
      }
      if (action.kind === 'land') {
        return dataOf(await opsChangePackageService.land(action.packageId, {
          version: action.version,
          packageHash: action.packageHash,
          approvedPackageHash: action.approvedPackageHash,
          ...(action.idempotencyKey ? { idempotencyKey: action.idempotencyKey } : {}),
        }, scope));
      }
      return dataOf(await opsChangePackageService.cleanup(action.packageId, { reason: action.reason }, scope));
    },
    onSettled: async (_result, _error, action) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: changePackageQueryKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: changePackageQueryKeys.detail(action.packageId, scope) }),
      ]);
    },
  });
};
