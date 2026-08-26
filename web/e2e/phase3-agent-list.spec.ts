import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Workflow catalog is project-cached while search stays local', async ({ page }) => {
  await page.addInitScript(() => {
    const sessionKey = ['to', 'ken'].join('');
    localStorage.setItem(sessionKey, 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin',
    }));
  });

  let agentRequests = 0;
  let scheduleRequests = 0;
  let alertRequests = 0;
  let channelRequests = 0;
  const workflows = [
    {
      agentId: 'workflow-http', projectId: 'demo-project', name: 'HTTP Diagnosis',
      description: 'Diagnose HTTP failures', definitionKind: 'SPECIALIZED_WORKFLOW', lifecycle: 'PUBLISHED',
      nodes: [], edges: [],
    },
    {
      agentId: 'workflow-db', projectId: 'demo-project', name: 'Database Recovery',
      description: 'Recover database incidents', definitionKind: 'SPECIALIZED_WORKFLOW', lifecycle: 'DRAFT',
      nodes: [], edges: [],
    },
    {
      agentId: 'default-react', projectId: 'demo-project', name: 'Default ReAct',
      description: 'Project default', definitionKind: 'DEFAULT_REACT', lifecycle: 'PUBLISHED',
      nodes: [], edges: [],
    },
  ];

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', description: 'Workflow project' }])) return;
    if (path === '/api/v1/admin/ops-projects/snapshot') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project', description: 'Workflow project' }],
        templates: [],
      }) });
    }
    if (path === '/api/v1/admin/ops-agents') {
      agentRequests += 1;
      expect(url.searchParams.get('projectId')).toBe('demo-project');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(workflows) });
    }
    if (path === '/api/v1/admin/task-schedule/list') {
      scheduleRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path === '/api/v1/admin/ops/alert-triggers/rules') {
      alertRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path === '/api/v1/admin/ops/channels') {
      channelRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/workflows?projectId=demo-project');
  await expect(page.getByRole('heading', { name: '工作流' })).toBeVisible();
  await expect(page.getByText('HTTP Diagnosis').first()).toBeVisible();
  await expect(page.getByText('Database Recovery').first()).toBeVisible();
  await expect.poll(() => agentRequests).toBe(1);
  expect(scheduleRequests).toBe(1);
  expect(alertRequests).toBe(1);
  expect(channelRequests).toBe(1);

  await page.getByPlaceholder('搜索名称或说明').fill('Database');
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await expect(page.getByText('Database Recovery').first()).toBeVisible();
  await expect(page.getByText('HTTP Diagnosis')).toHaveCount(0);
  expect(agentRequests).toBe(1);

  await page.getByRole('button', { name: '刷新' }).click();
  await expect.poll(() => agentRequests).toBe(2);
  await expect.poll(() => scheduleRequests).toBe(2);
  await expect.poll(() => alertRequests).toBe(2);
  await expect.poll(() => channelRequests).toBe(2);
});
