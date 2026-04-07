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

test('Model catalog loads provider, models and default policy through one query boundary', async ({ page }) => {
  await authenticateAdmin(page);

  const requestCounts = new Map<string, number>();
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    requestCounts.set(url.pathname, (requestCounts.get(url.pathname) || 0) + 1);

    let data: unknown = [];
    if (url.pathname.endsWith('/admin/ai-client-api/query-all')) {
      data = [{
        id: 1,
        apiId: 'provider-main',
        providerName: 'Primary Provider',
        providerType: 'OPENAI_COMPATIBLE',
        baseUrl: 'https://model.example.test/v1',
        status: 1,
      }];
    } else if (url.pathname.endsWith('/admin/ai-client-model/query-all')) {
      data = [{
        id: 11,
        modelId: 'chat-main',
        modelName: 'Chat Main',
        modelUsage: 'CHAT',
        modelType: 'CHAT',
        apiId: 'provider-main',
        status: 1,
        createTime: '2026-08-17T12:00:00',
        updateTime: '2026-08-17T12:00:00',
      }];
    } else if (url.pathname.endsWith('/admin/model-default-policy')) {
      data = {
        defaultChatModelId: 'chat-main',
        defaultEmbeddingModelId: '',
        defaultRerankModelId: '',
        defaultVisionModelId: '',
        status: 'ENABLED',
      };
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });

  await page.goto('/platform/models');
  await expect(page.getByRole('heading', { name: '模型接入' })).toBeVisible();
  await expect(page.getByText('Primary Provider', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Chat Main' })).toBeVisible();

  expect(requestCounts.get('/api/v1/admin/ai-client-api/query-all')).toBe(1);
  expect(requestCounts.get('/api/v1/admin/ai-client-model/query-all')).toBe(1);
  expect(requestCounts.get('/api/v1/admin/model-default-policy')).toBe(1);

  await page.getByRole('button', { name: /刷新/ }).click();

  await expect.poll(() => requestCounts.get('/api/v1/admin/ai-client-api/query-all')).toBe(2);
  await expect.poll(() => requestCounts.get('/api/v1/admin/ai-client-model/query-all')).toBe(2);
  await expect.poll(() => requestCounts.get('/api/v1/admin/model-default-policy')).toBe(2);
});
