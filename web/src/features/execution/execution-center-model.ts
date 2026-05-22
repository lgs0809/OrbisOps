import type { OpsChangePackage } from '../../services/ops-change-package-types';

export type PackageCapability =
  | 'canView'
  | 'canPrepare'
  | 'canRevise'
  | 'canSubmitReview'
  | 'canApprove'
  | 'canReject'
  | 'canLand'
  | 'canCleanup';

export type ExecutionQueue = 'pending' | 'running' | 'intervention' | 'history';

const queueStatuses: Record<ExecutionQueue, string[]> = {
  pending: ['DRAFT', 'VALIDATING', 'READY_FOR_REVIEW', 'REVIEWING', 'REVISING'],
  running: ['APPROVED', 'LANDING', 'LANDING_RUNNING'],
  intervention: ['NEEDS_REPLAN', 'LANDING_FAILED', 'REJECTED', 'VALIDATION_FAILED'],
  history: ['LANDED', 'CLOSED'],
};

const productQueue: Record<ExecutionQueue, string> = {
  pending: 'PENDING',
  running: 'RUNNING',
  intervention: 'INTERVENTION',
  history: 'HISTORY',
};

export const packageCapability = (record: OpsChangePackage | null | undefined, capability: PackageCapability) => {
  if (!record) return false;
  const nested = record.capabilities?.[capability];
  return Boolean(nested === undefined ? record[capability] : nested);
};

export const packageInQueue = (item: OpsChangePackage, queue: ExecutionQueue) => (
  item.productQueue
    ? item.productQueue === productQueue[queue]
    : queueStatuses[queue].includes(String(item.status || '').toUpperCase())
);

export const packageNeedsActorAction = (item: OpsChangePackage) => {
  const status = String(item.status || '').toUpperCase();
  if (['DRAFT', 'VALIDATION_FAILED', 'REVISING'].includes(status)) return packageCapability(item, 'canRevise');
  if (status === 'READY_FOR_REVIEW') return packageCapability(item, 'canSubmitReview');
  if (status === 'REVIEWING') return packageCapability(item, 'canApprove') || packageCapability(item, 'canReject');
  if (['APPROVED', 'LANDING', 'LANDING_RUNNING'].includes(status)) return packageCapability(item, 'canLand');
  if (['NEEDS_REPLAN', 'LANDING_FAILED', 'REJECTED'].includes(status)) return packageCapability(item, 'canRevise');
  return false;
};

export const packagesForQueue = (
  packages: OpsChangePackage[],
  queue: ExecutionQueue,
  options: { platformAdmin: boolean; mineOnly: boolean },
) => packages
  .filter((item) => packageInQueue(item, queue))
  .filter((item) => options.platformAdmin || !options.mineOnly || queue === 'history' || packageNeedsActorAction(item));
