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
  await expect(page.getByTestId('platform-resource-flow-hint')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '用户与访问控制' })).toBeVisible();
  await expect(page.getByRole('button', { name: '用户与访问控制', exact: true }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: '连接', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '智能能力', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '执行与治理', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '高级', exact: true })).toBeVisible();

  await page.getByRole('button', { name: '智能能力', exact: true }).click();
  await expect(page.getByRole('heading', { name: '智能能力' })).toBeVisible();
  await expect(page.getByText('先接入平台，再按项目使用', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '查看项目能力', exact: true })).toBeVisible();
  await page.getByRole('button', { name: /知识库/ }).click();
  await expect(page).toHaveURL(/\/settings\/knowledge$/);
});
