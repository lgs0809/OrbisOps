import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

const authenticateAdmin = async (page: import('@playwright/test').Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });
};

const routeCommonBackend = async (page: import('@playwright/test').Page) => {
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response({
          projects: [{ projectId: 'mobile-project', name: 'Mobile Project', defaultAgentId: 'default-react' }],
          templates: [],
        }),
      });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/types')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response([
          {
            type: 'WECHAT', displayName: 'WeChat', supportsInbound: true, supportsOutbound: true,
            connectionModes: ['WEBHOOK'],
            capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'DIRECT_MESSAGES', 'WEBHOOK', 'REPLY_TO_INBOUND'] },
          },
          {
            type: 'DISCORD', displayName: 'Discord', supportsInbound: true, supportsOutbound: true,
            connectionModes: ['LONG_CONNECTION'],
            capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'DIRECT_MESSAGES', 'GROUP_MESSAGES', 'INTERACTIVE_ACTIONS', 'MESSAGE_UPDATE'] },
          },
        ]),
      });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });
};

const expectNoDocumentOverflow = async (page: import('@playwright/test').Page) => {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
  }));
  expect(metrics.documentWidth).toBeLessThanOrEqual(metrics.viewport);
  expect(metrics.bodyWidth).toBeLessThanOrEqual(metrics.viewport);
};

test('390px shell supports skip navigation, accessible menus, reduced motion, and no page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await authenticateAdmin(page);
  await routeCommonBackend(page);

  await page.goto('/home?projectId=mobile-project');
  const navigationToggle = page.getByRole('button', { name: 'Open navigation menu' });
  await expect(navigationToggle).toBeVisible();
  await expect(navigationToggle).toHaveAttribute('aria-expanded', 'false');
  await expectNoDocumentOverflow(page);

  const skipLink = page.getByRole('link', { name: 'Skip to main content' });
  await page.keyboard.press('Tab');
  await expect(skipLink).toBeFocused();
  await expect(skipLink).toBeVisible();
  await skipLink.press('Enter');
  await expect(page.locator('#main-content')).toBeFocused();

  await navigationToggle.click();
  const collapseToggle = page.getByRole('button', { name: 'Collapse navigation menu' });
  await expect(collapseToggle).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('complementary', { name: 'Application navigation' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'Open navigation menu' })).toHaveAttribute('aria-expanded', 'false');

  const userMenu = page.getByRole('button', { name: 'Open user menu for e2e-admin' });
  await expect(userMenu).toBeVisible();
  await userMenu.press('Enter');
  await expect(page.getByText('退出登录', { exact: true })).toBeVisible();

  const transitionDuration = await page.locator('#orbisops-sidebar').evaluate((element) => getComputedStyle(element).transitionDuration);
  expect(transitionDuration).toBe('0s');
  await expectNoDocumentOverflow(page);
});

test('390px integrations keeps the compatibility matrix inside an internal scroll surface', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await authenticateAdmin(page);
  await routeCommonBackend(page);

  await page.goto('/platform/integrations?projectId=mobile-project');
  await expect(page.getByTestId('channel-compatibility-matrix')).toBeVisible();
  await expectNoDocumentOverflow(page);

  const matrix = page.getByTestId('channel-compatibility-matrix');
  const matrixScrollWidth = await matrix.evaluate((element) => element.scrollWidth);
  expect(matrixScrollWidth).toBeGreaterThan(0);
});
