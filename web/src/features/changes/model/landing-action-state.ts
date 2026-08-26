import type { OpsChangePackage, OpsLandingOperationRun } from '../../../services/ops-change-package-types';

export interface LandingActionState {
  canSubmit: boolean;
  hint: string;
}

/** A UI projection of durable facts; the server still rechecks authority and the ledger. */
export const landingActionState = (
  record: OpsChangePackage | null,
  operations: OpsLandingOperationRun[],
): LandingActionState => {
  if (!record) return { canSubmit: false, hint: '' };

  const current = operations.filter((operation) => operation.landingRunId === record.landingRunId);
  const matchesApproval = (operation: OpsLandingOperationRun) => (
    operation.packageId === record.packageId
    && operation.projectId === record.projectId
    && operation.approvedVersion === record.approvedVersion
    && Boolean(record.approvedPackageHash)
    && operation.approvedPackageHash === record.approvedPackageHash
  );
  const executionHint = '上次操作已执行或结果尚未确认，不能再次提交。请先复核落地结果；需要恢复时准备独立的恢复方案。';
  // Parallel detail/ledger reads can straddle a state transition. Current approved
  // execution facts take precedence over an older APPROVED status read.
  if (record.status === 'APPROVED') {
    const executed = Boolean(record.landingRunId) && current.some((operation) => (
      matchesApproval(operation) && (operation.factStatus !== 'NONE' || operation.status !== 'PENDING')
    ));
    return { canSubmit: !executed, hint: executed ? executionHint : '' };
  }
  if (record.status !== 'LANDING_FAILED') return { canSubmit: false, hint: '' };

  if (!record.landingRunId || !current.length || current.some((operation) => (
    !matchesApproval(operation)
  ))) {
    return { canSubmit: false, hint: '尚未确认上次执行结果。请先查看操作记录并完成复核或对账。' };
  }

  if (current.every((operation) => operation.factStatus === 'NONE' && operation.status === 'PENDING')) {
    return { canSubmit: true, hint: '当前记录显示上次未派发操作；提交时平台会再次核验执行账本与批准版本。' };
  }
  return {
    canSubmit: false,
    hint: executionHint,
  };
};
