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

const parsed = (value: unknown): any => {
  if (value == null || value === '') return null;
  if (typeof value !== 'string') return value;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
};

const meaningful = (value: unknown): boolean => {
  const candidate = parsed(value);
  if (candidate == null || candidate === '') return false;
  if (Array.isArray(candidate)) return candidate.length > 0;
  if (typeof candidate === 'object') return Object.keys(candidate).length > 0;
  return true;
};

const operationsFrom = (value: unknown): Array<Record<string, any>> => {
  const candidate = parsed(value);
  if (Array.isArray(candidate)) return candidate.filter((item) => item && typeof item === 'object');
  if (!candidate || typeof candidate !== 'object') return [];
  for (const key of ['steps', 'operations', 'mcpSteps']) {
    if (Array.isArray(candidate[key])) return operationsFrom(candidate[key]);
  }
  if (candidate.preferredPlan) return operationsFrom(candidate.preferredPlan);
  return [];
};

const recordOperations = (record: OpsChangePackage): Array<Record<string, any>> => {
  const snapshot = parsed(record.approvedSnapshot);
  const candidates = [
    record.mcpSteps,
    record.mcpStepsJson,
    record.landingPlan,
    record.landingPlanJson,
    record.preferredPlanJson,
    snapshot?.mcpSteps,
    snapshot?.mcpStepsJson,
    snapshot?.landingPlan,
    snapshot?.landingPlanJson,
  ];
  const seen = new Set<string>();
  return candidates.flatMap(operationsFrom).filter((operation, index) => {
    const key = String(operation.operationId || operation.operation_id || operation.id || `${operation.toolName || operation.tool_name || 'operation'}-${index}`);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

export const operationSafetyGaps = (record: OpsChangePackage): string[] => {
  const gaps: string[] = [];
  for (const operation of recordOperations(record)) {
    const writes = operation.writesTargetResource === true
      || ['MUTATE_TARGET_RESOURCE', 'EXECUTE_EXTERNAL_ACTION', 'DELETE_TARGET_RESOURCE']
        .includes(String(operation.effectType || '').toUpperCase())
      || ['PRODUCTION', 'TARGET_RESOURCE_WRITE'].includes(String(operation.effectScope || '').toUpperCase());
    if (!writes) continue;
    const label = text(operation.toolName, '生产操作');
    const recovery = parsed(operation.rollbackPlan || operation.rollbackSteps);
    if ('additionalChecks' in operation) gaps.push(`${label}：附加检查尚未纳入执行，需重新准备方案`);
    if (!meaningful(operation.postCheck || operation.postCheckPlan)) gaps.push(`${label}：缺少执行后的检查`);
    if (!meaningful(recovery)) gaps.push(`${label}：缺少该操作的恢复方案`);
    if (!meaningful(operation.rollbackPrecondition || recovery?.rollbackPrecondition)) gaps.push(`${label}：缺少允许恢复的条件`);
    if (!meaningful(operation.manualFallback || recovery?.manualFallback)) gaps.push(`${label}：缺少人工处置安排`);
  }
  return gaps;
};

const operationText = (operation: Record<string, any>): string => {
  const tool = operation.toolName || operation.tool_name || operation.remoteToolName || operation.remote_tool_name || operation.action || operation.type;
  const resource = operation.resourceKey || operation.resource_key || operation.resourceScope || operation.resource_scope || operation.targetObject || operation.target_object;
  const args = parsed(operation.arguments || operation.args || operation.input || operation.parameters);
  const argumentText = args && typeof args === 'object' && !Array.isArray(args)
    ? Object.entries(args).slice(0, 3).map(([key, value]) => `${key}=${typeof value === 'object' ? JSON.stringify(value) : String(value)}`).join(', ')
    : '';
  return [tool, resource, argumentText].filter(Boolean).join(' · ') || '未声明具体操作';
};

const readableStructured = (value: unknown, fallback: string): string => {
  const candidate = parsed(value);
  if (!meaningful(candidate)) return fallback;
  if (Array.isArray(candidate)) return candidate.map((item) => text(item, '')).filter(Boolean).join('；') || fallback;
  if (candidate && typeof candidate === 'object') {
    if (candidate.required === true && candidate.status) return `需要人工确认（${String(candidate.status)}）`;
    return Object.entries(candidate).slice(0, 4).map(([key, item]) => `${key}: ${text(item, '')}`).join('；') || fallback;
  }
  return text(candidate, fallback);
};

const risk = (value?: string): ApprovalCockpitView['risk'] => {
  const normalized = String(value || '').toUpperCase();
  return normalized === 'CRITICAL' || normalized === 'HIGH' || normalized === 'MEDIUM' || normalized === 'LOW'
    ? normalized
    : 'MEDIUM';
};

export const approvalCockpitView = (record: OpsChangePackage): ApprovalCockpitView => {
  const proofCount = Array.isArray(record.trustedProofRefs) ? record.trustedProofRefs.length : 0;
  const operations = recordOperations(record);
  const evidenceSignals = [
    meaningful(record.evidenceJson) ? 'evidence bundle' : '',
    proofCount ? `${proofCount} trusted proof ref${proofCount === 1 ? '' : 's'}` : '',
    meaningful(record.testProofHash) ? 'test proof' : '',
    meaningful(record.preflightResultJson) ? 'preflight' : '',
    meaningful(record.dryRunResultJson) ? 'validation proof' : '',
    meaningful(record.ciResultJson) ? 'CI proof' : '',
  ].filter(Boolean);
  const targetScopeValue = parsed(record.targetScopeJson);
  const targetWrites = operations.filter((operation) => operation.writesTargetResource === true
    || ['MUTATE_TARGET_RESOURCE', 'EXECUTE_EXTERNAL_ACTION', 'DELETE_TARGET_RESOURCE', 'WRITE']
      .includes(String(operation.effectType || '').toUpperCase()));
  const operationResource = [...new Set((targetWrites.length ? targetWrites : operations)
    .map((operation) => operation.resourceKey || operation.resource_key || operation.resourceScope || operation.resource_scope || operation.targetObject || operation.target_object)
    .filter(Boolean))].join('、');
  const targetScope = meaningful(targetScopeValue)
    ? text(targetScopeValue, record.serviceId || '未声明具体资源范围')
    : operationResource || record.serviceId || '未声明具体资源范围';
  const landingPlan = parsed(record.landingPlanJson);
  const approvalBoundary = parsed(record.approvalBoundaryJson) || landingPlan?.approvalBoundary || {};
  const operationScope = operationResource;
  const where = `${record.targetEnvironment || '未声明环境'} · ${record.serviceId || record.repositoryId || operationScope || '未声明服务/仓库'}`;
  const scope = approvalBoundary.scope || approvalBoundary.resourceScope || approvalBoundary.description || targetScope;
  const what = record.diffSummary || operations.map(operationText).join('；') || '未声明具体操作';
  const postChecks = targetWrites.flatMap((operation) => {
    const contract = parsed(operation.postCheck);
    if (!contract || typeof contract !== 'object' || Array.isArray(contract)) return [];
    return [contract, ...(Array.isArray(contract.additionalChecks) ? contract.additionalChecks : [])];
  });
  const actualVerification = postChecks.map((check) => {
    if (!check || typeof check !== 'object' || !meaningful(check.expectedValues)) return '';
    return `${text(check.toolName, '只读检查')}：${readableStructured(check.expectedValues, '未声明预期值')}`;
  }).filter(Boolean).join('；');

  return {
    why: record.objective || record.summary || '未明确处置目标',
    what,
    where,
    evidence: evidenceSignals.length ? evidenceSignals.join(' · ') : '未记录可核验证据',
    risk: risk(record.riskLevel),
    blastRadius: readableStructured(scope, '未声明具体资源范围'),
    rollback: readableStructured(record.rollbackStepsJson, '未记录回滚计划'),
    verify: actualVerification || readableStructured(record.verificationCriteriaJson, '未记录落地后验证标准'),
  };
};
