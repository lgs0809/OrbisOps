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

test('Execution Targets uses one template catalog query and explicit refresh', async ({ page }) => {
  await authenticateAdmin(page);

  let listCount = 0;
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    let data: unknown = [];
    if (url.pathname.endsWith('/api/v1/admin/ops/execution-adapter-templates')) {
      listCount += 1;
      data = [{
        adapterTemplateId: 'mysql-controlled',
        templateName: 'MySQL Controlled Execution',
        name: 'MySQL Controlled Execution',
        adapterType: 'mysql-controlled',
        supportedActions: ['MYSQL_CREATE_INDEX', 'MYSQL_UPDATE_LIMITED'],
        defaultConfig: { credentialRef: 'ops/project/mysql' },
        riskLevel: 'CRITICAL',
        readOnly: false,
        description: 'Controlled MySQL actions',
        generatedTargetCount: 1,
        status: 'ENABLED',
      }];
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });

  await page.goto('/platform/execution-targets');
  await expect(page.getByRole('heading', { name: '执行目标', exact: true })).toBeVisible();
  await expect(page.getByText('MySQL Controlled Execution', { exact: true }).first()).toBeVisible();
  expect(listCount).toBe(1);

  await page.getByRole('button', { name: /刷新/ }).click();
  await expect.poll(() => listCount).toBe(2);
});
