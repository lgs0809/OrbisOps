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
  await page.goto('/settings');

  await expect(page.getByRole('heading', { name: '设置' })).toBeVisible();
  await expect(page.getByText('共享能力需要在项目中显式启用。', { exact: false })).toBeVisible();
  await expect(page.getByRole('heading', { name: '用户与访问控制' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '连接' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '智能能力' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '执行与治理' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '高级' })).toBeVisible();
  await expect(page.getByRole('button', { name: /用户与访问控制/ })).toBeVisible();

  await page.getByRole('button', { name: /知识库/ }).click();
  await expect(page).toHaveURL(/\/settings\/knowledge$/);
});
