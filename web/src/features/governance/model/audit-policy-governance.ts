import type { OpsAuditPolicy } from '../../../services/ops-admin-service';

export interface EffectiveAuditControl {
  key: 'retention' | 'masking' | 'export' | 'high-risk' | 'replay';
  title: string;
  status: 'ENFORCED' | 'PLATFORM_INVARIANT' | 'DELEGATED' | 'NOT_ACTIVATED' | 'UNAVAILABLE';
  summary: string;
}

export const auditRetentionDays = (policy: OpsAuditPolicy): number => {
  const value = Number(policy.retentionDays ?? policy.retention_days ?? 180);
  return Number.isFinite(value) ? Math.max(7, Math.min(3650, Math.trunc(value))) : 180;
};

const booleanValue = (value: unknown, fallback: boolean): boolean => {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value === 1;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['1', 'true', 'yes', 'enabled'].includes(normalized)) return true;
    if (['0', 'false', 'no', 'disabled'].includes(normalized)) return false;
  }
  return fallback;
};

export const effectiveAuditControls = (policy: OpsAuditPolicy): EffectiveAuditControl[] => [
  {
    key: 'retention',
    title: 'Audit visibility retention',
    status: 'ENFORCED',
    summary: `${auditRetentionDays(policy)} days · server-side visibility boundary`,
  },
  {
    key: 'masking',
    title: 'Sensitive field masking',
    status: 'PLATFORM_INVARIANT',
    summary: 'Always on · requests cannot disable masking',
  },
  {
    key: 'export',
    title: 'Audit export',
    status: 'NOT_ACTIVATED',
    summary: 'Exports are audit-logged; approval gating is not activated yet',
  },
  {
    key: 'high-risk',
    title: 'High-risk execution confirmation',
    status: 'DELEGATED',
    summary: 'Governed by ChangePackage → Approval → Landing, not Audit Policy',
  },
  {
    key: 'replay',
    title: 'Audit replay',
    status: 'UNAVAILABLE',
    summary: 'No replay executor is exposed in the current product',
  },
];

export const auditPolicySavePayload = (
  policy: OpsAuditPolicy,
  projectId: string,
  retentionDays: number,
): OpsAuditPolicy => ({
  ...policy,
  projectId: projectId || 'GLOBAL',
  retentionDays: Math.max(7, Math.min(3650, Math.trunc(retentionDays || 180))),
  maskingEnabled: true,
  exportApprovalRequired: booleanValue(policy.exportApprovalRequired ?? policy.export_approval_required, true),
  highRiskConfirmationRequired: booleanValue(
    policy.highRiskConfirmationRequired ?? policy.high_risk_confirmation_required,
    true,
  ),
  replayEnabled: booleanValue(policy.replayEnabled ?? policy.replay_enabled, true),
  status: policy.status || 'ENABLED',
});

export const auditRetentionDirty = (serverPolicy: OpsAuditPolicy, draftPolicy: OpsAuditPolicy): boolean => (
  auditRetentionDays(serverPolicy) !== auditRetentionDays(draftPolicy)
);
