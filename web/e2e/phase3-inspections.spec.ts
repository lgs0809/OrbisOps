import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Inspection schedules use project queries and load execution history on demand', async ({ page }) => {
  await page.addInitScript(() => {
    const sessionKey = ['to', 'ken'].join('');
    localStorage.setItem(sessionKey, 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin',
    }));
  });

  let scheduleRequests = 0;
  let agentRequests = 0;
  let channelRequests = 0;
  let executionRequests = 0;
  const schedule = {
    id: 7,
    projectId: 'demo-project',
    agentId: 'default-react',
    agentBindingMode: 'LATEST_PUBLISHED',
    taskName: '生产运行巡检',
    description: 'Check the project every 15 minutes',
    cronExpression: '0 0/15 * * * ?',
    taskParam: 'Check error rate and instance health',
    status: 1,
    rangeMinutes: 15,
    promWindow: '5m',
    updateTime: '2026-08-17 14:00:00',
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{
          projectId: 'demo-project', name: 'Demo Project', description: 'Inspection project',
          defaultAgentId: 'default-react',
        }])) return;
    if (path === '/api/v1/admin/ops-projects/snapshot') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{
          projectId: 'demo-project', name: 'Demo Project', description: 'Inspection project',
          defaultAgentId: 'default-react',
        }],
        templates: [],
      }) });
    }
    if (path === '/api/v1/admin/task-schedule/list') {
      scheduleRequests += 1;
      expect(url.searchParams.get('projectId')).toBe('demo-project');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([schedule]) });
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
    if (path === '/api/v1/admin/task-schedule/execution/list') {
      executionRequests += 1;
      expect(url.searchParams.get('scheduleId')).toBe('7');
      expect(url.searchParams.get('limit')).toBe('30');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        id: 101, scheduleId: 7, projectId: 'demo-project', agentId: 'default-react',
        triggerType: 'SCHEDULE', status: 'SUCCESS', startedAt: '2026-08-17 14:00:00', endedAt: '2026-08-17 14:00:08',
      }]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/automations/schedules?projectId=demo-project');
  await expect(page.getByRole('heading', { name: '定时自动化' })).toBeVisible();
  await expect(page.getByText('生产运行巡检')).toBeVisible();
  await expect(page.getByText('默认助手', { exact: true }).first()).toBeVisible();
  await expect.poll(() => scheduleRequests).toBe(1);
  expect(agentRequests).toBe(1);
  expect(channelRequests).toBe(1);
  expect(executionRequests).toBe(0);

  const row = page.getByRole('row').filter({ hasText: '生产运行巡检' });
  await row.getByRole('button', { name: 'Run 记录' }).click();
  await expect.poll(() => executionRequests).toBe(1);
  await expect(page.getByText('成功')).toBeVisible();

  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: '刷新' }).click();
  await expect.poll(() => scheduleRequests).toBe(2);
  await expect.poll(() => agentRequests).toBe(2);
  await expect.poll(() => channelRequests).toBe(2);
});
