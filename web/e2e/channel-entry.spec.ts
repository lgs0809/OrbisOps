import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

type Json = Record<string, any>;

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Channel is transport-first and can be output-only, ReAct, or Workflow', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  const createRequests: Json[] = [];
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }])) return;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response({ projects: [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }] }),
      });
    }
    if (path.includes('/api/v1/admin/ops-agents')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response([
          { agentId: 'default-react', name: '内部默认 Agent', definitionKind: 'MAIN_ASSISTANT', version: 4 },
          { agentId: 'daily-inspection', name: '每日生产巡检', definitionKind: 'SPECIALIZED_WORKFLOW', lifecycle: 'PUBLISHED', version: 7 },
        ]),
      });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/types')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response([
          {
            type: 'GENERIC_WEBHOOK',
            displayName: 'Generic Webhook',
            supportsInbound: true,
            supportsOutbound: true,
            connectionModes: ['WEBHOOK'],
            capabilitySet: { capabilities: ['INBOUND_MESSAGE', 'OUTBOUND_MESSAGE'] },
          },
        ]),
      });
    }
    if (path.endsWith('/api/v1/admin/ops/channels') && request.method() === 'POST') {
      const body = request.postDataJSON() as Json;
      createRequests.push(body);
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response({ channelId: 'notify-only', ...body }),
      });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/notify-only/readiness')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response({
          channelId: 'notify-only',
          projectId: 'demo-project',
          type: 'GENERIC_WEBHOOK',
          ready: false,
          checks: [],
          checkedAt: '2026-08-17T00:00:00Z',
        }),
      });
    }
    if (path.endsWith('/api/v1/admin/ops/channels')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/integrations?projectId=demo-project');
  await page.getByRole('button', { name: '接入渠道' }).first().click();
  await page.getByPlaceholder('生产值班群').fill('Notification only');

  const inboundSection = page.getByTestId('channel-inbound-execution');
  const inboundSelect = inboundSection.getByRole('combobox');
  await expect(inboundSelect).toBeVisible();
  await expect(inboundSection).toHaveAttribute('data-workflow-count', '1');
  await inboundSelect.click();
  await expect(page.getByRole('option', { name: '仅出站' })).toBeVisible();
  await expect(page.getByRole('option', { name: '默认助手' })).toBeVisible();
  await expect(page.getByRole('option', { name: '专用工作流' })).toBeVisible();
  await page.getByRole('option', { name: '专用工作流' }).click();

  const workflowSelect = page.getByTestId('channel-workflow-selector').getByRole('combobox');
  await expect(workflowSelect).toBeVisible();
  await workflowSelect.click();
  const workflowOption = page.getByRole('option', { name: /每日生产巡检/ });
  await expect(workflowOption).toBeVisible();
  await expect(page.getByRole('option', { name: /内部默认 Agent/ })).toHaveCount(0);
  await workflowOption.click();

  await inboundSection.getByRole('combobox').click();
  await page.getByRole('option', { name: '仅出站' }).click();
  await expect(inboundSection.getByRole('combobox')).toContainText('仅出站');
  await page.getByRole('button', { name: 'confirm' }).click();

  await expect.poll(() => createRequests.length).toBe(1);
  expect(createRequests[0].executionType).toBe('NONE');
  expect(createRequests[0].workflowId).toBeUndefined();
  expect(createRequests[0].agentId).toBeUndefined();
});
