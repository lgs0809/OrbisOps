import type { OpsChangePackage } from '../../../services/ops-change-package-types';
import {
  changePackageReasonLabel,
  changePackageRiskColor,
  changePackageRiskLabel,
  changePackageStatusColor,
  changePackageStatusHint,
  changePackageStatusLabel,
} from './change-package-display';

export type ChangePackageSummarySource = Pick<OpsChangePackage, 'packageId' | 'status' | 'version'> & Partial<Pick<
  OpsChangePackage,
  | 'projectId'
  | 'incidentId'
  | 'packageHash'
  | 'approvedPackageHash'
  | 'objective'
  | 'summary'
  | 'riskLevel'
  | 'targetEnvironment'
  | 'reasonCode'
  | 'updateTime'
>> & {
  landingRunId?: string;
};

export interface ChangePackageSummary {
  packageId: string;
  projectId?: string;
  incidentId?: string;
  title: string;
  status: string;
  statusLabel: string;
  statusColor: string;
  statusHint: string;
  version: number;
  packageHash?: string;
  landingRunId?: string;
  riskLevel?: string;
  riskLabel: string;
  riskColor: string;
  targetEnvironment?: string;
  reasonCode?: string;
  reasonLabel: string;
  updateTime?: string;
}

export const toChangePackageSummary = (source: ChangePackageSummarySource): ChangePackageSummary => {
  // Validation evidence is historical after submission; it must not replace the current lifecycle status.
  const staleValidationReason = ['READY_FOR_REVIEW', 'VALIDATION_PASSED'].includes(source.reasonCode || '')
    && !['DRAFT', 'VALIDATING', 'VALIDATION_FAILED', 'REVISING', 'READY_FOR_REVIEW'].includes(source.status);
  const reasonCode = staleValidationReason ? undefined : source.reasonCode;
  return ({
  packageId: source.packageId,
  projectId: source.projectId,
  incidentId: source.incidentId,
  title: source.objective?.trim() || source.summary?.trim() || source.packageId,
  status: source.status,
  statusLabel: changePackageStatusLabel(source.status),
  statusColor: changePackageStatusColor(source.status),
  statusHint: changePackageStatusHint(source.status, reasonCode),
  version: source.version,
  packageHash: source.packageHash || source.approvedPackageHash,
  landingRunId: source.landingRunId,
  riskLevel: source.riskLevel,
  riskLabel: changePackageRiskLabel(source.riskLevel),
  riskColor: changePackageRiskColor(source.riskLevel),
  targetEnvironment: source.targetEnvironment,
  reasonCode,
  reasonLabel: changePackageReasonLabel(reasonCode),
  updateTime: source.updateTime,
  });
};
