import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Project Workspace reuses the shared project snapshot and keeps capability/runtime reads project-scoped', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  let snapshotRequests = 0;
  let catalogRequests = 0;
  let projectSkillRequests = 0;
  let memberRequests = 0;
  let repositoryRequests = 0;

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{
          projectId: 'demo-project', name: 'Demo Project', description: 'Project Workspace query acceptance', owner: 'e2e-admin',
          environments: ['dev', 'prod'], resources: [], generatedMcps: [], resourceCount: 0, generatedMcpCount: 0,
          defaultAgentId: 'default-react', skillIds: [], sharedMcpIds: [], defaultAgentPublished: true,
          readyForInvestigation: false, readinessReason: 'NO_RESOURCE_CONNECTED',
        }])) { catalogRequests += 1; return; }
    if (path === '/api/v1/admin/ops-projects/snapshot') {
      snapshotRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{
          projectId: 'demo-project', name: 'Demo Project', description: 'Project Workspace query acceptance', owner: 'e2e-admin',
          environments: ['dev', 'prod'], resources: [], generatedMcps: [], resourceCount: 0, generatedMcpCount: 0,
          defaultAgentId: 'default-react', skillIds: [], sharedMcpIds: [], defaultAgentPublished: true,
          readyForInvestigation: false, readinessReason: 'NO_RESOURCE_CONNECTED',
        }],
        templates: [{ templateId: 'mysql-readonly-template', type: 'mysql', name: 'MySQL readonly' }],
      }) });
    }
    if (path === '/api/v1/admin/ops/skills/projects/demo-project') projectSkillRequests += 1;
    if (path === '/api/v1/admin/ops-projects/projects/demo-project/members') memberRequests += 1;
    if (path.includes('/repositories') && url.searchParams.get('projectId') === 'demo-project') repositoryRequests += 1;
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/projects?projectId=demo-project');
  await expect(page.getByRole('tab', { name: '概览' })).toBeVisible();
  await expect(page.getByText('Demo Project').first()).toBeVisible();
  await expect.poll(() => snapshotRequests).toBe(1);
  await expect.poll(() => projectSkillRequests).toBe(1);
  await expect.poll(() => memberRequests).toBe(1);

  await page.waitForTimeout(150);
  expect(snapshotRequests).toBe(1);
  expect(catalogRequests).toBe(1);
  expect(repositoryRequests).toBeLessThanOrEqual(1);

  await page.getByRole('button', { name: '刷新项目', exact: true }).click();
  await expect.poll(() => snapshotRequests).toBe(2);
  expect(projectSkillRequests).toBe(1);
  expect(memberRequests).toBe(1);
  expect(catalogRequests).toBe(2);
});
