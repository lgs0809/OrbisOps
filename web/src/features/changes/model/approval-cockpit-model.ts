import type { OpsChangePackage } from '../../../services/ops-change-package-types';

export interface ApprovalCockpitView {
  why: string;
  what: string;
  where: string;
  evidence: string;
  risk: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  blastRadius: string;
  rollback: string;
  verify: string;
}

const text = (value: unknown, fallback: string): string => {
  if (value == null || value === '') return fallback;
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value);
      return typeof parsed === 'string' ? parsed : JSON.stringify(parsed);
    } catch {
      return value;
    }
  }
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
};

const risk = (value?: string): ApprovalCockpitView['risk'] => {
  const normalized = String(value || '').toUpperCase();
  return normalized === 'CRITICAL' || normalized === 'HIGH' || normalized === 'MEDIUM' || normalized === 'LOW'
    ? normalized
    : 'MEDIUM';
};

export const approvalCockpitView = (record: OpsChangePackage): ApprovalCockpitView => {
  const proofCount = Array.isArray(record.trustedProofRefs) ? record.trustedProofRefs.length : 0;
  const evidenceSignals = [
    record.evidenceJson ? 'evidence bundle' : '',
    proofCount ? `${proofCount} trusted proof ref${proofCount === 1 ? '' : 's'}` : '',
    record.testProofHash ? 'test proof' : '',
    record.preflightResultJson ? 'preflight' : '',
    record.dryRunResultJson ? 'validation proof' : '',
    record.ciResultJson ? 'CI proof' : '',
  ].filter(Boolean);
  const targetScope = text(record.targetScopeJson, record.serviceId || '未声明具体资源范围');

  return {
    why: record.objective || record.summary || '未明确处置目标',
    what: record.diffSummary || text(record.landingPlanJson || record.mcpStepsJson, '未声明具体操作'),
    where: `${record.targetEnvironment || '未声明环境'} · ${record.serviceId || record.repositoryId || '未声明服务/仓库'}`,
    evidence: evidenceSignals.length ? evidenceSignals.join(' · ') : '未记录可核验证据',
    risk: risk(record.riskLevel),
    blastRadius: targetScope,
    rollback: text(record.rollbackStepsJson, '未记录回滚计划'),
    verify: text(record.verificationCriteriaJson, '未记录落地后验证标准'),
  };
};
