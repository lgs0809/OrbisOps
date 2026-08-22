import type { OpsAdminDashboardOverview, OpsChangePackage, OpsIncident } from '../../services/ops-admin-service';
import type { OpsProjectWorkspace } from '../../services/ops-project-service';
import type { UserDashboardOverview, UserIncidentSummary } from '../../services/ops-user-service';

export type AttentionTone = 'danger' | 'warning' | 'info';
export type AttentionKind =
  | 'ACTION_REQUIRED'
  | 'CRITICAL_INCIDENT'
  | 'PENDING_DECISION'
  | 'FAILED_CHANGE'
  | 'READINESS_BLOCKER'
  | 'PENDING_OPERATION'
  | 'AUDIT_REMINDER';

export interface AttentionItem {
  key: string;
  priority: number;
  kind: AttentionKind;
  tone: AttentionTone;
  title: string;
  detail: string;
  actionLabel: string;
  href: string;
}

const CLOSED_INCIDENT = new Set(['RESOLVED', 'CLOSED']);
const CRITICAL_SEVERITY = new Set(['P0', 'P1', 'CRITICAL', 'HIGH', 'SEV0', 'SEV1']);
const PENDING_CHANGE = new Set(['READY_FOR_REVIEW', 'REVIEWING', 'REVISING']);
const FAILED_CHANGE = new Set(['LANDING_FAILED', 'VALIDATION_FAILED', 'VERIFICATION_FAILED', 'NEEDS_REPLAN']);

const value = (input: unknown) => String(input ?? '').trim();
const upper = (input: unknown) => value(input).toUpperCase();
const encoded = (input: unknown) => encodeURIComponent(value(input));

export const projectNeedsAttention = (project: OpsProjectWorkspace) =>
  project.readyForInvestigation === false ||
  project.onboarding?.some((item) => !item.optional && !item.completed) ||
  !project.defaultAgentId;

const incidentHref = (incident: { projectId?: string; incidentId: string }) =>
  `/workbench${incident.projectId ? `?projectId=${encoded(incident.projectId)}` : ''}`;

const incidentItem = (
  incident: OpsIncident | UserIncidentSummary,
  priority: number,
  kind: 'ACTION_REQUIRED' | 'CRITICAL_INCIDENT',
): AttentionItem => ({
  key: `incident:${incident.incidentId}`,
  priority,
  kind,
  tone: kind === 'ACTION_REQUIRED' ? 'danger' : 'warning',
  title: value(incident.title) || incident.incidentId,
  detail: [value(incident.projectId) || '未知 Project', value(incident.severity), value(incident.serviceName)]
    .filter(Boolean)
    .join(' · '),
  actionLabel: '前往工作台',
  href: incidentHref(incident),
});

const packageTitle = (pkg: OpsChangePackage) => value(pkg.objective) || value(pkg.summary) || pkg.packageId;
const packageHref = (pkg: { projectId?: string; packageId?: string }) => {
  const params = new URLSearchParams();
  if (pkg.projectId) params.set('projectId', pkg.projectId);
  if (pkg.packageId) params.set('packageId', pkg.packageId);
  return `/changes${params.toString() ? `?${params.toString()}` : ''}`;
};
const changeStatusLabel = (status: unknown) => ({
  READY_FOR_REVIEW: '待提交审批',
  REVIEWING: '审批中',
  REVISING: '修订中',
  VALIDATION_FAILED: '校验失败',
  LANDING_FAILED: '执行失败',
  VERIFICATION_FAILED: '验证失败',
  NEEDS_REPLAN: '需要重新规划',
}[upper(status)] || '状态已更新');

export const buildAdminAttentionItems = (
  overview: OpsAdminDashboardOverview | null,
  projects: OpsProjectWorkspace[],
  changes: OpsChangePackage[],
): AttentionItem[] => {
  if (!overview) return [];
  const result: AttentionItem[] = [];
  const seenIncidents = new Set<string>();

  for (const incident of overview.recentIncidents || []) {
    if (upper(incident.status) !== 'ACTION_REQUIRED') continue;
    seenIncidents.add(incident.incidentId);
    result.push(incidentItem(incident, 10, 'ACTION_REQUIRED'));
  }

  for (const incident of overview.recentIncidents || []) {
    if (seenIncidents.has(incident.incidentId)) continue;
    if (CLOSED_INCIDENT.has(upper(incident.status))) continue;
    if (!CRITICAL_SEVERITY.has(upper(incident.severity))) continue;
    seenIncidents.add(incident.incidentId);
    result.push(incidentItem(incident, 20, 'CRITICAL_INCIDENT'));
  }

  for (const pkg of changes) {
    if (!PENDING_CHANGE.has(upper(pkg.status))) continue;
    result.push({
      key: `change-decision:${pkg.packageId}`,
      priority: 30,
      kind: 'PENDING_DECISION',
      tone: 'warning',
      title: packageTitle(pkg),
      detail: `${value(pkg.projectId) || '未知 Project'} · ${changeStatusLabel(pkg.status)}`,
      actionLabel: '审批方案',
      href: packageHref(pkg),
    });
  }

  for (const decision of overview.pendingWorkflowDecisions || []) {
    result.push({
      key: `workflow-decision:${decision.runId}`,
      priority: 31,
      kind: 'PENDING_DECISION',
      tone: 'warning',
      title: value(decision.goal) || 'Workflow 需要人工决策',
      detail: `${decision.projectId} · Workflow 步骤 · ${value(decision.owner) || '未分配负责人'}`,
      actionLabel: '处理 Workflow',
      href: `/workbench?projectId=${encoded(decision.projectId)}&runId=${encoded(decision.runId)}`,
    });
  }

  for (const pkg of changes) {
    if (!FAILED_CHANGE.has(upper(pkg.status))) continue;
    result.push({
      key: `failed-change:${pkg.packageId}`,
      priority: 40,
      kind: 'FAILED_CHANGE',
      tone: 'danger',
      title: packageTitle(pkg),
      detail: `${value(pkg.projectId) || '未知 Project'} · ${changeStatusLabel(pkg.status)} · 需要人工介入`,
      actionLabel: '介入执行',
      href: packageHref(pkg),
    });
  }

  for (const project of projects) {
    if (!projectNeedsAttention(project)) continue;
    result.push({
      key: `project:${project.projectId}`,
      priority: 50,
      kind: 'READINESS_BLOCKER',
      tone: 'warning',
      title: project.name || project.projectId,
      detail: '项目配置尚未完成：请检查默认执行能力、资源连接和必需的初始化项。',
      actionLabel: '完善项目配置',
      href: `/projects?projectId=${encoded(project.projectId)}`,
    });
  }

  return result.sort((left, right) => left.priority - right.priority || left.key.localeCompare(right.key));
};

export const buildUserAttentionItems = (overview: UserDashboardOverview | null): AttentionItem[] => {
  if (!overview) return [];
  const result: AttentionItem[] = [];
  const seenIncidents = new Set<string>();

  for (const incident of overview.actionRequiredIncidents || []) {
    seenIncidents.add(incident.incidentId);
    result.push(incidentItem(incident, 10, 'ACTION_REQUIRED'));
  }
  for (const incident of overview.currentIncidents || []) {
    if (seenIncidents.has(incident.incidentId)) continue;
    if (CLOSED_INCIDENT.has(upper(incident.status)) || !CRITICAL_SEVERITY.has(upper(incident.severity))) continue;
    seenIncidents.add(incident.incidentId);
    result.push(incidentItem(incident, 20, 'CRITICAL_INCIDENT'));
  }
  for (const pkg of overview.pendingApprovals || []) {
    result.push({
      key: `user-approval:${pkg.packageId}`,
      priority: 30,
      kind: 'PENDING_DECISION',
      tone: 'warning',
      title: value(pkg.title) || pkg.packageId,
      detail: `${value(pkg.projectId) || '未知 Project'} · 需要审批`,
      actionLabel: '审批',
      href: packageHref(pkg),
    });
  }
  for (const decision of overview.pendingWorkflowDecisions || []) {
    result.push({
      key: `user-workflow-decision:${decision.runId}`,
      priority: 31,
      kind: 'PENDING_DECISION',
      tone: 'warning',
      title: value(decision.goal) || 'Workflow 需要人工决策',
      detail: `${decision.projectId} · Workflow 步骤`,
      actionLabel: '处理 Workflow',
      href: `/workbench?projectId=${encoded(decision.projectId)}&runId=${encoded(decision.runId)}`,
    });
  }
  for (const pkg of overview.pendingOperations || []) {
    result.push({
      key: `user-operation:${pkg.packageId}`,
      priority: 40,
      kind: 'PENDING_OPERATION',
      tone: 'warning',
      title: value(pkg.title) || pkg.packageId,
      detail: `${value(pkg.projectId) || '未知 Project'} · 执行或验证需要你处理`,
      actionLabel: '处理',
      href: packageHref(pkg),
    });
  }
  for (const audit of overview.auditReminders || []) {
    result.push({
      key: `audit:${audit.id}`,
      priority: 60,
      kind: 'AUDIT_REMINDER',
      tone: 'info',
      title: value(audit.summary) || `${value(audit.moduleName)} / ${value(audit.actionName)}`,
      detail: `${value(audit.projectId) || '无 Project'} · ${value(audit.createdAt)}`,
      actionLabel: '查看审计',
      href: '/settings/governance',
    });
  }
  return result.sort((left, right) => left.priority - right.priority || left.key.localeCompare(right.key));
};
