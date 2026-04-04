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
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const data = url.pathname.endsWith('/knowledge-bases/global/stats')
      ? { chunkCount: 0, documentCount: 0, byType: [], bySource: [] }
      : [];
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });
};

test('Platform Settings groups capability catalogs and preserves canonical deep links', async ({ page }) => {
  await authenticateAdmin(page);
  await page.goto('/platform');

  await expect(page.getByRole('heading', { name: 'Platform Settings' })).toBeVisible();
  await expect(page.getByText('平台目录 ≠ 项目授权。', { exact: false })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Integrations' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Intelligence' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Execution & Governance' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Advanced' })).toBeVisible();

  await page.getByRole('button', { name: /Knowledge/ }).click();
  await expect(page).toHaveURL(/\/platform\/knowledge$/);
  await expect(page.getByRole('heading', { name: '通用知识库' })).toBeVisible();
});
