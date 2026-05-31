import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Change Center loads execution list first and detail proof streams only on demand', async ({ page }) => {
  await page.addInitScript(() => {
    const sessionKey = ['to', 'ken'].join('');
    localStorage.setItem(sessionKey, 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin',
    }));
  });

  let listRequests = 0;
  let detailRequests = 0;
  let eventRequests = 0;
  let landingOperationRequests = 0;
  const changePackage = {
    packageId: 'package-1',
    projectId: 'demo-project',
    incidentId: 'incident-1',
    version: 1,
    summary: 'Restart checkout deployment after validation',
    objective: 'Restore checkout availability',
    status: 'DRAFT',
    riskLevel: 'MEDIUM',
    targetEnvironment: 'PRODUCTION',
    packageHash: 'hash-v1',
    capabilities: {
      canView: true,
      canRevise: true,
      canSubmitReview: false,
      canApprove: false,
      canReject: false,
      canLand: false,
      canCleanup: false,
    },
    proposedOperations: [],
    createTime: '2026-08-17 14:00:00',
    updateTime: '2026-08-17 14:05:00',
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project' }])) return;
    if (path === '/api/v1/admin/ops-projects/snapshot') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project' }],
        templates: [],
      }) });
    }
    if (path === '/api/v1/admin/ops/change-packages') {
      listRequests += 1;
      expect(url.searchParams.get('projectId')).toBe('demo-project');
      expect(url.searchParams.get('limit')).toBe('100');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([changePackage]) });
    }
    if (path === '/api/v1/admin/ops/change-packages/package-1') {
      detailRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(changePackage) });
    }
    if (path === '/api/v1/admin/ops/change-packages/package-1/events') {
      eventRequests += 1;
      expect(url.searchParams.get('limit')).toBe('100');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path === '/api/v1/admin/ops/change-packages/package-1/landing-operation-runs') {
      landingOperationRequests += 1;
      expect(url.searchParams.get('limit')).toBe('200');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/changes?projectId=demo-project');
  await expect(page.getByRole('heading', { name: '变更', exact: true })).toBeVisible();
  const row = page.getByRole('row').filter({ hasText: 'Restore checkout availability' });
  await expect(row).toBeVisible();
  await expect.poll(() => listRequests).toBe(1);
  expect(detailRequests).toBe(0);
  expect(eventRequests).toBe(0);
  expect(landingOperationRequests).toBe(0);

  await row.getByRole('button', { name: '查看' }).click();
  await expect.poll(() => detailRequests).toBe(1);
  await expect.poll(() => eventRequests).toBe(1);
  await expect.poll(() => landingOperationRequests).toBe(1);
  await expect(page.getByText('审批工作台')).toBeVisible();
  await expect(page.getByText('执行审批前校验')).toBeVisible();

  await page.keyboard.press('Escape');
  await row.getByRole('button', { name: '查看' }).click();
  await page.waitForTimeout(100);
  expect(detailRequests).toBe(1);
  expect(eventRequests).toBe(1);
  expect(landingOperationRequests).toBe(1);

  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: '刷新' }).click();
  await expect.poll(() => listRequests).toBe(2);
  expect(detailRequests).toBe(1);
});
