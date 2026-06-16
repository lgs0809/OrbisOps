import { fulfillAccessibleProjects } from './fixtures/project-catalog';
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
    if (await fulfillAccessibleProjects(route, [{ projectId: 'mobile-project', name: 'Mobile Project', defaultAgentId: 'default-react' }])) return;
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
  const navigationToggle = page.getByRole('button', { name: '打开导航菜单' });
  await expect(navigationToggle).toBeVisible();
  await expect(navigationToggle).toHaveAttribute('aria-expanded', 'false');
  await expectNoDocumentOverflow(page);

  const skipLink = page.getByRole('link', { name: '跳到主要内容' });
  await page.keyboard.press('Tab');
  await expect(skipLink).toBeFocused();
  await expect(skipLink).toBeVisible();
  await skipLink.press('Enter');
  await expect(page.locator('#main-content')).toBeFocused();

  await navigationToggle.click();
  const collapseToggle = page.getByRole('button', { name: '收起导航菜单' });
  await expect(collapseToggle).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('complementary', { name: '应用导航' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: '打开导航菜单' })).toHaveAttribute('aria-expanded', 'false');

  const userMenu = page.getByRole('button', { name: '打开 e2e-admin 的用户菜单' });
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

test('820px tablet layout keeps navigation, provider cards, and compatibility matrix usable without document overflow', async ({ page }) => {
  await page.setViewportSize({ width: 820, height: 1180 });
  await authenticateAdmin(page);
  await routeCommonBackend(page);

  await page.goto('/platform/integrations?projectId=mobile-project');
  const navigation = page.getByRole('complementary', { name: '应用导航' });
  await expect(navigation).toBeVisible();
  const navigationToggle = page.getByRole('button', { name: '打开导航菜单' });
  await expect(navigationToggle).toHaveAttribute('aria-expanded', 'false');
  await expect.poll(async () => navigation.evaluate((element) => Math.round(element.getBoundingClientRect().width))).toBe(76);
  await navigationToggle.click();
  await expect(page.getByRole('button', { name: '收起导航菜单' })).toHaveAttribute('aria-expanded', 'true');
  await expect.poll(async () => navigation.evaluate((element) => Math.round(element.getBoundingClientRect().width))).toBe(216);
  await page.getByRole('button', { name: '收起导航菜单' }).click();
  await expect.poll(async () => navigation.evaluate((element) => Math.round(element.getBoundingClientRect().width))).toBe(76);
  await expect(page.getByTestId('channel-compatibility-matrix')).toBeVisible();
  await expectNoDocumentOverflow(page);

  const providerGrid = page.getByTestId('channel-provider-cards');
  const gridColumns = await providerGrid.evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(' ').filter(Boolean).length);
  expect(gridColumns).toBe(2);
});
