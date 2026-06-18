import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });
const assetsDir = resolve(process.cwd(), '../docs/assets');

const dashboardOverview = {
  currentIncidentCount: 5,
  actionRequiredIncidentCount: 2,
  unownedActionRequiredIncidentCount: 1,
  investigatingIncidentCount: 2,
  verifyingIncidentCount: 1,
  pendingChangeCount: 2,
  failedChangeCount: 1,
  runningChangeCount: 1,
  pendingWorkflowDecisionCount: 1,
  pendingWorkflowDecisions: [],
  recentIncidents: [
    {
      incidentId: 'INC-2417', projectId: 'checkout-platform', title: 'Checkout API latency regression',
      severity: 'P1', status: 'INVESTIGATING', updateTime: '2026-08-17 18:42:10', ownerUserId: '',
      actionRequired: true,
    },
    {
      incidentId: 'INC-2413', projectId: 'payments-core', title: 'Payment worker backlog increasing',
      severity: 'P2', status: 'VERIFYING', updateTime: '2026-08-17 18:31:44', ownerUserId: 'ops-1',
      actionRequired: false,
    },
  ],
  recentChanges: [
    {
      packageId: 'CP-882', projectId: 'checkout-platform', objective: 'Tune checkout circuit-breaker thresholds',
      status: 'PENDING_APPROVAL',
    },
    {
      packageId: 'CP-879', projectId: 'payments-core', objective: 'Roll forward consumer concurrency',
      status: 'VERIFICATION_SUCCEEDED',
    },
  ],
  capabilityHealth: { healthyCount: 18, degradedCount: 2, checks: [{ name: 'runtime', status: 'UP' }] },
  projects: [
    {
      projectId: 'checkout-platform', name: 'Checkout Platform', readyForInvestigation: true,
      diagnosisReadiness: { ready: true, missing: [] }, remediationReadiness: { ready: false, missing: ['Emergency Stop'] },
    },
    {
      projectId: 'payments-core', name: 'Payments Core', readyForInvestigation: true,
      diagnosisReadiness: { ready: true, missing: [] }, remediationReadiness: { ready: true, missing: [] },
    },
    {
      projectId: 'inventory-api', name: 'Inventory API', readyForInvestigation: false,
      diagnosisReadiness: { ready: false, missing: ['Real Evidence Source'] }, remediationReadiness: { ready: false, missing: ['Diagnosis'] },
    },
  ],
};

const productMetrics = {
  activation: {
    projectCount: 8,
    evidenceConnectedProjects: 7,
    queryProofVerifiedProjects: 6,
    defaultAgentReadyProjects: 7,
    onboardingCompleteProjects: 5,
    activatedProjects: 5,
    diagnosisReadyProjects: 6,
    activationRate: 0.625,
    definition: 'Activated = all required Project onboarding steps complete',
    source: 'authoritative ProjectWorkspace onboarding/readiness projection',
    highCardinalityLabels: false,
  },
};

const projectSnapshot = {
  projects: [
    { projectId: 'checkout-platform', name: 'Checkout Platform', defaultAgentId: 'checkout-react' },
    { projectId: 'payments-core', name: 'Payments Core', defaultAgentId: 'payments-react' },
    { projectId: 'inventory-api', name: 'Inventory API', defaultAgentId: 'inventory-react' },
  ],
  templates: [],
};

test('capture README dashboard and command-palette demo frames', async ({ page }) => {
  mkdirSync(assetsDir, { recursive: true });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'readme-demo');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: 'demo-admin', username: 'ops-admin', userRole: 'admin', role: 'admin', token: 'readme-demo',
    }));
  });

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/admin/dashboard/overview') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(dashboardOverview) });
    }
    if (path === '/api/v1/admin/ops/product-metrics') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(productMetrics) });
    }
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(projectSnapshot) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/home?projectId=checkout-platform');
  await expect(page.getByRole('heading', { name: 'Attention & Action' })).toBeVisible();
  await expect(page.getByTestId('project-activation')).toContainText('63%');
  await page.screenshot({ path: resolve(assetsDir, 'orbisops-dashboard.png'), fullPage: false, animations: 'disabled' });
  await page.screenshot({ path: resolve(assetsDir, 'orbisops-demo-01.png'), fullPage: false, animations: 'disabled' });

  await page.getByRole('button', { name: 'Open global search' }).click();
  await expect(page.getByRole('textbox', { name: 'Search pages, actions, and projects' })).toBeVisible();
  await page.screenshot({ path: resolve(assetsDir, 'orbisops-demo-02.png'), fullPage: false, animations: 'disabled' });

  const search = page.getByRole('textbox', { name: 'Search pages, actions, and projects' });
  await search.fill('channels');
  await expect(page.getByRole('listbox', { name: 'Search results' })).toContainText('Channels');
  await page.screenshot({ path: resolve(assetsDir, 'orbisops-demo-03.png'), fullPage: false, animations: 'disabled' });

  await search.fill('payments');
  await expect(page.getByRole('listbox', { name: 'Search results' })).toContainText('Switch project · Payments Core');
  await page.screenshot({ path: resolve(assetsDir, 'orbisops-demo-04.png'), fullPage: false, animations: 'disabled' });
});
