import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Global command palette supports keyboard navigation, route search, and project switching', async ({ page }) => {
  const unexpectedPageErrors: string[] = [];
  page.on('pageerror', (error) => {
    if (!error.message.includes('ResizeObserver loop completed with undelivered notifications')) {
      unexpectedPageErrors.push(error.message);
    }
  });

  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [
          { projectId: 'payments', name: 'Payments', defaultAgentId: 'payments-react' },
          { projectId: 'orders', name: 'Orders', defaultAgentId: 'orders-react' },
        ],
        templates: [],
      }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/home?projectId=payments');
  const trigger = page.getByRole('button', { name: 'Open global search' });
  await expect(trigger).toBeVisible();
  await page.keyboard.press('Meta+K');

  const search = page.getByRole('textbox', { name: 'Search pages, actions, and projects' });
  await expect(search).toBeVisible();
  await search.fill('mcp');
  const results = page.getByRole('listbox', { name: 'Search results' });
  await expect(results.getByRole('option').filter({ hasText: 'Tools / MCP' })).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/platform\/tools\?projectId=payments$/);

  await page.getByRole('button', { name: 'Open global search' }).click();
  await search.fill('orders');
  await expect(results.getByRole('option').filter({ hasText: 'Switch project · Orders' })).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/platform\/tools\?projectId=orders$/);

  await expect(trigger).toBeVisible();
  await page.keyboard.press('Meta+K');
  await search.fill('start diagnosis');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('ArrowUp');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/work\/diagnosis\?projectId=orders$/);
  expect(unexpectedPageErrors).toEqual([]);
});
