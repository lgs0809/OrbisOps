import { expect, test } from '@playwright/test';

type Json = Record<string, any>;

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

    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: '0000', info: 'success', data: {
            projects: [{ projectId: 'demo-project', name: 'Example Service', defaultAgentId: 'default-react' }],
          },
        }),
      });
    }
    if (path.includes('/api/v1/admin/ops-agents')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: [
          { agentId: 'default-react', name: '内部默认 Agent', definitionKind: 'MAIN_ASSISTANT', version: 4 },
          { agentId: 'daily-inspection', name: '每日生产巡检', definitionKind: 'SPECIALIZED_WORKFLOW', version: 7 },
        ] }),
      });
    }
    if (path.endsWith('/api/v1/admin/ops/channels') && request.method() === 'POST') {
      const body = request.postDataJSON() as Json;
      createRequests.push(body);
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: { channelId: 'notify-only', ...body } }),
      });
    }
    if (path.endsWith('/api/v1/admin/ops/channels')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: [] }),
      });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data: [] }),
    });
  });

  await page.goto('/channel-management?projectId=demo-project');
  await page.getByRole('button', { name: '新建渠道' }).click();

  const inboundSection = page.getByTestId('channel-inbound-execution');
  const inboundSelect = inboundSection.getByRole('combobox');
  await expect(inboundSelect).toBeVisible();
  await expect(inboundSection).toHaveAttribute('data-workflow-count', '1');
  await inboundSelect.click();
  await expect(page.getByText('不处理入站（仅用于通知 / 主动发送）', { exact: true }).last()).toBeVisible();
  await expect(page.getByText('ReAct', { exact: true }).last()).toBeVisible();
  await expect(page.getByText('Workflow', { exact: true }).last()).toBeVisible();
  await page.getByText('Workflow', { exact: true }).last().click();
  const workflowSelect = page.getByTestId('channel-workflow-selector').getByRole('combobox');
  await expect(workflowSelect).toBeVisible();
  await workflowSelect.click();
  const workflowOption = page.getByRole('option', { name: /每日生产巡检/ });
  await expect(workflowOption).toBeVisible();
  await expect(page.getByRole('option', { name: /内部默认 Agent/ })).toHaveCount(0);
  await workflowOption.click();

  await inboundSelect.click();
  await page.getByText('不处理入站（仅用于通知 / 主动发送）', { exact: true }).last().click();
  await page.getByPlaceholder('生产告警群').fill('只发通知');
  await page.getByRole('button', { name: 'confirm' }).click();
  await expect.poll(() => createRequests.length).toBe(1);
  expect(createRequests[0].executionType).toBe('NONE');
  expect(createRequests[0].workflowId).toBeUndefined();
  expect(createRequests[0].agentId).toBeUndefined();
});
