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
    title: '审计可见性保留期',
    status: 'ENFORCED',
    summary: `${auditRetentionDays(policy)} 天 · 服务端可见性边界`,
  },
  {
    key: 'masking',
    title: '敏感字段脱敏',
    status: 'PLATFORM_INVARIANT',
    summary: '始终启用 · 请求无法关闭脱敏',
  },
  {
    key: 'export',
    title: '审计导出',
    status: 'NOT_ACTIVATED',
    summary: '导出操作会写入审计；导出审批门禁暂未启用',
  },
  {
    key: 'high-risk',
    title: '高风险执行确认',
    status: 'DELEGATED',
    summary: '由 ChangePackage → Approval → Landing 治理，不由审计策略直接控制',
  },
  {
    key: 'replay',
    title: '审计重放',
    status: 'UNAVAILABLE',
    summary: '当前产品未暴露审计重放执行器',
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
