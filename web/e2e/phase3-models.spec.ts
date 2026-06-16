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

const ok = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

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

    await route.fulfill({ status: 200, contentType: 'application/json', body: ok(data) });
  });

  await page.goto('/settings/models');
  await expect(page.getByRole('heading', { name: '模型' })).toBeVisible();
  await expect(page.getByText('Primary Provider', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Chat Main' })).toBeVisible();

  expect(requestCounts.get('/api/v1/admin/ai-client-api/query-all')).toBe(1);
  expect(requestCounts.get('/api/v1/admin/ai-client-model/query-all')).toBe(1);
  expect(requestCounts.get('/api/v1/admin/model-default-policy')).toBe(1);

  await page.getByRole('button', { name: '刷新' }).click();

  await expect.poll(() => requestCounts.get('/api/v1/admin/ai-client-api/query-all')).toBe(2);
  await expect.poll(() => requestCounts.get('/api/v1/admin/ai-client-model/query-all')).toBe(2);
  await expect.poll(() => requestCounts.get('/api/v1/admin/model-default-policy')).toBe(2);
});

test('Provider setup stores an environment reference instead of a browser-managed credential', async ({ page }) => {
  await authenticateAdmin(page);

  let referencePayload: Record<string, unknown> | null = null;
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;

    if (path.endsWith('/admin/ai-client-api/query-all') || path.endsWith('/admin/ai-client-model/query-all')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: ok([]) });
    }
    if (path.endsWith('/admin/model-default-policy')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: ok({
        defaultChatModelId: '', defaultEmbeddingModelId: '', defaultRerankModelId: '', defaultVisionModelId: '', status: 'ENABLED',
      }) });
    }
    if (path.endsWith('/admin/ai-client-provider-references') && route.request().method() === 'POST') {
      referencePayload = route.request().postDataJSON();
      return route.fulfill({ status: 200, contentType: 'application/json', body: ok(true) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: ok([]) });
  });

  await page.goto('/settings/models');
  await page.getByRole('button', { name: '新建 Provider', exact: true }).click();
  const modal = page.locator('.semi-modal').filter({ hasText: '新建 Provider' });

  await modal.getByRole('textbox', { name: 'Provider 名称' }).fill('Production OpenAI');
  await modal.getByRole('textbox', { name: 'Base URL' }).fill('https://api.example.test');
  await modal.getByRole('textbox', { name: '凭据环境变量' }).fill('OPENAI_API_KEY');
  await modal.getByRole('button', { name: 'confirm' }).click();

  await expect.poll(() => referencePayload).not.toBeNull();
  expect(referencePayload?.credentialEnvironmentVariable).toBe('OPENAI_API_KEY');
  expect(referencePayload).not.toHaveProperty('apiKey');
  await expect(modal).toHaveCount(0);
});
