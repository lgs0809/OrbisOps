import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('WeChat setup exposes only the secure Official Account webhook contract', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  let createPayload: any = null;
  const descriptor = {
    type: 'WECHAT', displayName: 'WeChat', supportsInbound: true, supportsOutbound: true,
    connectionModes: ['WEBHOOK'],
    capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'DIRECT_MESSAGES', 'WEBHOOK', 'REPLY_TO_INBOUND'] },
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }])) return;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }], templates: [],
      }) });
    }
    if (path.includes('/api/v1/admin/ops-agents')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/types')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([descriptor]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels') && route.request().method() === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels') && route.request().method() === 'POST') {
      createPayload = route.request().postDataJSON();
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        channelId: 'channel-wechat',
        ...createPayload,
      }) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/channel-wechat/readiness')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        channelId: 'channel-wechat', projectId: 'demo-project', type: 'WECHAT', ready: false,
        checks: [
          { kind: 'CREDENTIALS', status: 'BLOCKED_EXTERNAL', reasonCode: 'WECHAT_APP_SECRET_UNAVAILABLE', detail: 'Configure environment references' },
          { kind: 'CONNECTION', status: 'BLOCKED_EXTERNAL', reasonCode: 'WECHAT_APP_SECRET_UNAVAILABLE', detail: 'Configure environment references' },
          { kind: 'INBOUND', status: 'ACTION_REQUIRED', reasonCode: 'CHANNEL_INBOUND_TEST_REQUIRED', detail: 'Send a real provider message' },
          { kind: 'OUTBOUND', status: 'ACTION_REQUIRED', reasonCode: 'CHANNEL_OUTBOUND_TEST_REQUIRED', detail: 'Run a real outbound test' },
          { kind: 'IDENTITY_ACCESS', status: 'ACTION_REQUIRED', reasonCode: 'CHANNEL_IDENTITY_MAPPING_REQUIRED', detail: 'Bind an identity' },
        ],
      }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/integrations?projectId=demo-project');
  await page.getByRole('button', { name: '接入渠道' }).first().click();
  const modal = page.locator('.semi-modal').filter({ hasText: '接入渠道' });

  await expect(modal).toContainText('WeChat');
  await expect(modal).toContainText('WEBHOOK');
  await expect(modal).toContainText('校验凭据环境变量');
  await expect(modal).toContainText('加密凭据环境变量');

  await modal.getByPlaceholder('生产值班群').fill('WeChat Production');
  await modal.getByRole('textbox', { name: '凭据环境变量', exact: true }).fill('WECHAT_APP_SECRET');
  await modal.getByRole('textbox', { name: 'App / Client ID' }).fill('wx-test-app');
  await modal.getByRole('textbox', { name: '校验凭据环境变量' }).fill('WECHAT_VERIFY_TOKEN');
  await modal.getByRole('textbox', { name: '加密凭据环境变量' }).fill('WECHAT_ENCODING_AES_KEY');
  await modal.getByRole('button', { name: 'confirm' }).click();

  await expect.poll(() => createPayload).not.toBeNull();
  expect(createPayload.type).toBe('WECHAT');
  expect(createPayload.config.connectionMode).toBe('WEBHOOK');
  expect(createPayload.config.appId).toBe('wx-test-app');
  expect(String(createPayload.credentialRef)).toContain('WECHAT_APP_SECRET');
  expect(String(createPayload.config.verificationTokenRef)).toContain('WECHAT_VERIFY_TOKEN');
  expect(String(createPayload.config.encodingAesKeyRef)).toContain('WECHAT_ENCODING_AES_KEY');

  await expect(page.getByText(/凭据使用部署环境变量引用/)).toBeVisible();
});
