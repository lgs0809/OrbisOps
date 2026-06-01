import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Memory Management queries by filter and refetches after archive mutation', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  let listRequests = 0;
  let archiveRequests = 0;
  let archived = false;
  const memory = {
    memoryId: 'memory-1',
    scopeType: 'PROJECT',
    scopeId: 'demo-project',
    memoryType: 'PROJECT_CONTEXT',
    title: 'Deployment window',
    summary: 'Production changes happen after 20:00',
    content: 'Production changes happen after 20:00',
    status: 'ACTIVE',
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/v1/admin/ops/context-memories' && route.request().method() === 'GET') {
      listRequests += 1;
      expect(url.searchParams.get('scopeType')).toBe('PROJECT');
      expect(url.searchParams.get('status')).toBe('ACTIVE');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(archived ? [] : [memory]) });
    }
    if (path === '/api/v1/admin/ops/context-memories/memory-1/status' && route.request().method() === 'PATCH') {
      archiveRequests += 1;
      archived = true;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({ ...memory, status: 'ARCHIVED' }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/advanced/memory');
  const row = page.getByRole('row').filter({ hasText: 'Deployment window' });
  await expect(row).toBeVisible();
  expect(listRequests).toBe(1);

  await row.getByRole('button', { name: '归档' }).click();
  await expect.poll(() => archiveRequests).toBe(1);
  await expect.poll(() => listRequests).toBe(2);
  await expect(page.getByText('暂无记忆')).toBeVisible();
});
