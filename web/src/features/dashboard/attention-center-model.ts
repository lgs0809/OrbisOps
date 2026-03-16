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
  `/incidents?projectId=${encoded(incident.projectId)}&incidentId=${encoded(incident.incidentId)}`;

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
  detail: [value(incident.projectId) || 'Unknown project', value(incident.severity), value(incident.serviceName)]
    .filter(Boolean)
    .join(' · '),
  actionLabel: '处理 Incident',
  href: incidentHref(incident),
});

const packageTitle = (pkg: OpsChangePackage) => value(pkg.objective) || value(pkg.summary) || pkg.packageId;
const packageHref = (pkg: { projectId?: string; packageId?: string }) => {
  const params = new URLSearchParams();
  if (pkg.projectId) params.set('projectId', pkg.projectId);
  if (pkg.packageId) params.set('packageId', pkg.packageId);
  return `/executions${params.toString() ? `?${params.toString()}` : ''}`;
};

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
      detail: `${value(pkg.projectId) || 'Unknown project'} · ChangePackage ${value(pkg.status)}`,
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
      title: value(decision.goal) || 'Workflow requires human decision',
      detail: `${decision.projectId} · Workflow step · ${value(decision.owner) || 'Unassigned owner'}`,
      actionLabel: '处理 Workflow',
      href: `/analysis-tasks?projectId=${encoded(decision.projectId)}&runId=${encoded(decision.runId)}`,
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
      detail: `${value(pkg.projectId) || 'Unknown project'} · ${value(pkg.status)} · requires intervention`,
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
      detail: 'Project readiness is incomplete: check default Agent, connections and required onboarding.',
      actionLabel: '修复 Readiness',
      href: `/project-workspace?projectId=${encoded(project.projectId)}`,
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
      detail: `${value(pkg.projectId) || 'Unknown project'} · approval required`,
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
      title: value(decision.goal) || 'Workflow requires human decision',
      detail: `${decision.projectId} · Workflow step`,
      actionLabel: '处理 Workflow',
      href: `/analysis-tasks?projectId=${encoded(decision.projectId)}&runId=${encoded(decision.runId)}`,
    });
  }
  for (const pkg of overview.pendingOperations || []) {
    result.push({
      key: `user-operation:${pkg.packageId}`,
      priority: 40,
      kind: 'PENDING_OPERATION',
      tone: 'warning',
      title: value(pkg.title) || pkg.packageId,
      detail: `${value(pkg.projectId) || 'Unknown project'} · execution or verification needs you`,
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
      detail: `${value(audit.projectId) || 'No project'} · ${value(audit.createdAt)}`,
      actionLabel: '查看审计',
      href: '/my-audit',
    });
  }
  return result.sort((left, right) => left.priority - right.priority || left.key.localeCompare(right.key));
};
