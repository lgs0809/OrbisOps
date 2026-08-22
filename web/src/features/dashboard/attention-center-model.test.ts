import { describe, expect, it } from 'vitest';

import type { OpsAdminDashboardOverview, OpsChangePackage } from '../../services/ops-admin-service';
import type { OpsProjectWorkspace } from '../../services/ops-project-service';
import type { UserDashboardOverview } from '../../services/ops-user-service';
import { buildAdminAttentionItems, buildUserAttentionItems } from './attention-center-model';

const baseAdmin = (): OpsAdminDashboardOverview => ({
  currentIncidentCount: 2,
  actionRequiredIncidentCount: 1,
  unownedActionRequiredIncidentCount: 0,
  investigatingIncidentCount: 1,
  verifyingIncidentCount: 0,
  recentIncidents: [
    { incidentId: 'inc-action', projectId: 'p1', title: 'Checkout blocked', status: 'ACTION_REQUIRED', severity: 'P1' },
    { incidentId: 'inc-critical', projectId: 'p1', title: 'Payment latency', status: 'INVESTIGATING', severity: 'P0' },
  ],
  pendingChangeCount: 1,
  failedChangeCount: 1,
  runningChangeCount: 0,
  pendingWorkflowDecisionCount: 1,
  pendingWorkflowDecisions: [
    { runId: 'run-1', projectId: 'p1', owner: 'alice', status: 'WAITING_APPROVAL', goal: 'Approve database failover' },
  ],
  recentChanges: [],
  capabilityHealth: { healthyCount: 4, degradedCount: 1 },
  projects: [],
});

const changes: OpsChangePackage[] = [
  { packageId: 'cp-review', projectId: 'p1', status: 'REVIEWING', version: 1 } as OpsChangePackage,
  { packageId: 'cp-failed', projectId: 'p1', status: 'LANDING_FAILED', version: 1 } as OpsChangePackage,
];

const projects: OpsProjectWorkspace[] = [
  { projectId: 'p1', name: 'Payments', readyForInvestigation: false } as OpsProjectWorkspace,
];

describe('attention center model', () => {
  it('orders admin work by action required, critical incident, decisions, failure, then readiness', () => {
    const items = buildAdminAttentionItems(baseAdmin(), projects, changes);

    expect(items.map((item) => item.kind)).toEqual([
      'ACTION_REQUIRED',
      'CRITICAL_INCIDENT',
      'PENDING_DECISION',
      'PENDING_DECISION',
      'FAILED_CHANGE',
      'READINESS_BLOCKER',
    ]);
    expect(items[0].key).toBe('incident:inc-action');
    expect(items.filter((item) => item.key === 'incident:inc-action')).toHaveLength(1);
    expect(items.find((item) => item.key === 'workflow-decision:run-1')?.href)
      .toBe('/workbench?projectId=p1&runId=run-1');
  });

  it('does not treat approved changes as pending approval', () => {
    const items = buildAdminAttentionItems(
      baseAdmin(),
      [],
      [{ packageId: 'cp-approved', projectId: 'p1', status: 'APPROVED', version: 1 } as OpsChangePackage],
    );

    expect(items.some((item) => item.key === 'change-decision:cp-approved')).toBe(false);
  });

  it('orders user action required before critical incidents and approvals', () => {
    const overview: UserDashboardOverview = {
      currentIncidents: [
        { incidentId: 'inc-action', projectId: 'p1', title: 'Action', status: 'ACTION_REQUIRED', severity: 'P1' },
        { incidentId: 'inc-p0', projectId: 'p1', title: 'P0', status: 'INVESTIGATING', severity: 'P0' },
      ],
      actionRequiredIncidents: [
        { incidentId: 'inc-action', projectId: 'p1', title: 'Action', status: 'ACTION_REQUIRED', severity: 'P1' },
      ],
      availableProjects: [],
      recentSessions: [],
      pendingExecutions: [],
      pendingApprovals: [{ packageId: 'cp-1', projectId: 'p1', title: 'Approve change', status: 'REVIEWING' }],
      pendingWorkflowDecisions: [
        { runId: 'run-2', projectId: 'p1', status: 'WAITING_APPROVAL', goal: 'Approve failover' },
      ],
      pendingOperations: [],
      auditReminders: [],
    };

    const items = buildUserAttentionItems(overview);

    expect(items.map((item) => item.kind)).toEqual([
      'ACTION_REQUIRED', 'CRITICAL_INCIDENT', 'PENDING_DECISION', 'PENDING_DECISION',
    ]);
    expect(items.filter((item) => item.key === 'incident:inc-action')).toHaveLength(1);
    expect(items.find((item) => item.key === 'user-workflow-decision:run-2')?.href)
      .toBe('/workbench?projectId=p1&runId=run-2');
  });
});
