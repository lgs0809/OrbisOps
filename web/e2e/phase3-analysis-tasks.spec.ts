import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const authenticateAdmin = async (page: import('@playwright/test').Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001',
      username: 'e2e-admin',
      userRole: 'admin',
      role: 'admin',
      token: 'x',
    }));
  });
};

test('completed Analysis Tasks stay cached until explicit refresh and detail is loaded on demand', async ({ page }) => {
  await authenticateAdmin(page);

  let taskListRequests = 0;
  let detailRequests = 0;
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data: unknown = [];

    if (await fulfillAccessibleProjects(route, [{ projectId: 'project-1', name: 'Core Platform' }])) return;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      data = {
        projects: [{ projectId: 'project-1', name: 'Core Platform' }],
        templates: [],
      };
    } else if (path.endsWith('/api/v1/admin/ops/analysis-tasks')) {
      taskListRequests += 1;
      data = [{
        projectId: 'project-1',
        runId: 'run-1',
        goal: 'Investigate payment latency',
        source: 'CHAT',
        status: 'SUCCEEDED',
        updatedAt: '2026-08-17T12:00:00',
      }];
    } else if (path.endsWith('/api/v1/admin/ops/analysis-tasks/run-1')) {
      detailRequests += 1;
      data = {
        projectId: 'project-1',
        runId: 'run-1',
        goal: 'Investigate payment latency',
        source: 'CHAT',
        status: 'SUCCEEDED',
        summary: 'Latency was caused by an upstream dependency.',
        evidenceSufficient: true,
        evidence: [],
        toolResults: [],
        changePackages: [],
        incidents: [],
        skillUsages: [],
        feedback: [],
        hypotheses: [],
        excludedFindings: [],
        unknowns: [],
        recommendations: [],
      };
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });

  await page.goto('/platform/advanced/analysis-tasks');
  await expect(page).toHaveURL(/\/workbench$/);
  await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();
  await expect(page.getByText('Investigate payment latency', { exact: true })).toBeVisible();
  expect(taskListRequests).toBe(1);
  expect(detailRequests).toBe(0);

  await page.waitForTimeout(3300);
  expect(taskListRequests).toBe(1);
  expect(detailRequests).toBe(0);

  await page.getByRole('button', { name: '刷新运行' }).click();
  await expect.poll(() => taskListRequests).toBe(2);
  expect(detailRequests).toBe(0);

  await page.getByRole('button', { name: /Investigate payment latency/ }).click();
  await expect(page.getByText('Latency was caused by an upstream dependency.', { exact: true })).toBeVisible();
  expect(detailRequests).toBe(1);
});
