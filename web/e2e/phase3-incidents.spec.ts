import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('legacy Incident Center route converges on Workbench without reviving a separate Incident page', async ({ page }) => {
  await page.addInitScript(() => {
    const sessionKey = ['to', 'ken'].join('');
    localStorage.setItem(sessionKey, 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin',
    }));
  });

  let projectRequests = 0;
  let incidentRequests = 0;
  let detailRequests = 0;
  let memberRequests = 0;

  const incident = {
    incidentId: 'incident-1',
    projectId: 'demo-project',
    title: 'Checkout error rate elevated',
    summary: 'Checkout 5xx rate is above baseline',
    status: 'INVESTIGATING',
    severity: 'HIGH',
    sourceType: 'ALERT',
    serviceName: 'checkout',
    ownerUserId: '',
    firstSeenAt: '2026-08-17 13:50:00',
    updateTime: '2026-08-17 14:05:00',
  };
  const detail = {
    incident,
    sourceRefs: [{ sourceType: 'ALERT', sourceId: 'alert-1', title: 'CheckoutErrorRateHigh' }],
    timeline: [{
      id: 1,
      incidentId: 'incident-1',
      eventType: 'INVESTIGATION_STARTED',
      title: 'Investigation started',
      createTime: '2026-08-17 13:51:00',
    }],
    runs: [{ runId: 'run-1', status: 'SUCCEEDED' }],
    changePackages: [],
    watchers: [],
    relatedIncidents: [],
    currentUserWatching: false,
    diagnosis: {
      summary: 'Elevated 5xx responses confirmed on checkout.',
      facts: [],
      impact: ['Checkout requests are failing above baseline'],
      inferences: [],
      excludedHypotheses: [],
      unknowns: [],
      recommendations: ['Inspect the most recent checkout deployment'],
      sourceStatus: [],
      evidenceCompleteness: 'PARTIAL',
      confidence: 'MEDIUM',
      requiresAction: false,
    },
    suggestedUserAction: 'Continue investigation',
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project' }])) { projectRequests += 1; return; }
    if (path === '/api/v1/admin/ops-projects/snapshot') {
      projectRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project' }],
        templates: [],
      }) });
    }
    if (path === '/api/v1/admin/ops/incidents') {
      incidentRequests += 1;
      expect(url.searchParams.get('projectId')).toBe('demo-project');
      expect(url.searchParams.get('limit')).toBe('200');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([incident]) });
    }
    if (path === '/api/v1/admin/ops/incidents/incident-1/detail') {
      detailRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(detail) });
    }
    if (path === '/api/v1/admin/ops-projects/projects/demo-project/members') {
      memberRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        projectId: 'demo-project', userId: '10001', username: 'e2e-admin', memberRole: 'OWNER', status: 'ACTIVE',
      }]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/incidents?projectId=demo-project');
  await expect(page).toHaveURL(/\/workbench\?projectId=demo-project$/);
  await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();
  await expect.poll(() => projectRequests).toBe(1);
  expect(incidentRequests).toBe(0);
  expect(detailRequests).toBe(0);
  expect(memberRequests).toBe(0);
  await expect(page.getByText('Checkout error rate elevated', { exact: true })).toHaveCount(0);
});
