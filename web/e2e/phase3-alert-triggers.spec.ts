import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Alert Triggers caches catalog and project options while modal reuse stays local', async ({ page }) => {
  await page.addInitScript(() => {
    const sessionKey = ['to', 'ken'].join('');
    localStorage.setItem(sessionKey, 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin',
    }));
  });

  let ruleRequests = 0;
  let eventRequests = 0;
  let agentRequests = 0;
  let channelRequests = 0;
  let incidentRequests = 0;
  const rule = {
    id: 11,
    projectId: 'demo-project',
    agentDefinitionId: 'default-react',
    agentBindingMode: 'LATEST_PUBLISHED',
    ruleName: 'Critical checkout alerts',
    status: 1,
    sourceType: 'ALERTMANAGER',
    severityRegex: 'critical',
    matchLabelsJson: '{}',
    notifyChannel: false,
    updateTime: '2026-08-17 14:00:00',
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }])) return;
    if (path === '/api/v1/admin/ops-projects/snapshot') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }],
        templates: [],
      }) });
    }
    if (path === '/api/v1/admin/ops/alert-triggers/rules') {
      ruleRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([rule]) });
    }
    if (path === '/api/v1/admin/ops/alert-triggers/events') {
      eventRequests += 1;
      expect(url.searchParams.get('limit')).toBe('50');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        id: 21,
        ruleId: 11,
        ruleName: 'Critical checkout alerts',
        alertName: 'CheckoutErrorRateHigh',
        severity: 'critical',
        serviceName: 'checkout',
        fingerprint: 'fp-checkout',
        status: 'TRIGGERED',
        runId: 'run-1',
        runStatus: 'SUCCEEDED',
        createTime: '2026-08-17 14:01:00',
      }]) });
    }
    if (path === '/api/v1/admin/ops-agents') {
      agentRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        agentId: 'default-react', projectId: 'demo-project', name: 'Project ReAct',
        definitionKind: 'DEFAULT_REACT', lifecycle: 'PUBLISHED', nodes: [], edges: [],
      }]) });
    }
    if (path === '/api/v1/admin/ops/channels') {
      channelRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path === '/api/v1/admin/ops/incidents') {
      incidentRequests += 1;
      expect(url.searchParams.get('projectId')).toBe('demo-project');
      expect(url.searchParams.get('limit')).toBe('200');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        incidentId: 'incident-1', projectId: 'demo-project', fingerprint: 'fp-checkout',
        title: 'Checkout incident', status: 'INVESTIGATING', severity: 'critical',
      }]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/automations/alert-triggers?projectId=demo-project');
  await expect(page.getByRole('heading', { name: '告警触发器' })).toBeVisible();
  await expect(page.getByText('Critical checkout alerts').first()).toBeVisible();
  await expect(page.getByText('CheckoutErrorRateHigh')).toBeVisible();
  await expect(page.getByRole('button', { name: '打开 Run' })).toBeVisible();
  await expect.poll(() => ruleRequests).toBe(1);
  expect(eventRequests).toBe(1);
  expect(agentRequests).toBe(1);
  expect(channelRequests).toBe(1);
  expect(incidentRequests).toBe(1);

  await page.getByRole('button', { name: '新建告警自动化' }).click();
  await expect(page.getByRole('heading', { name: '新建告警自动化' })).toBeVisible();
  expect(agentRequests).toBe(1);
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: '刷新' }).first().click();
  await expect.poll(() => ruleRequests).toBe(2);
  await expect.poll(() => eventRequests).toBe(2);
  await expect.poll(() => agentRequests).toBe(2);
  await expect.poll(() => channelRequests).toBe(2);
  await expect.poll(() => incidentRequests).toBe(2);
});
