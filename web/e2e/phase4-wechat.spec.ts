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
  await page.getByRole('button', { name: '新建 Channel' }).click();
  const modal = page.locator('.semi-modal').filter({ hasText: 'Connect' });
  const providerSelect = modal.getByRole('combobox').first();
  await providerSelect.click();
  await page.getByRole('option', { name: 'WeChat' }).click();

  await expect(modal.getByText('Official Account Secure Webhook')).toBeVisible();
  await expect(modal.getByText('不支持个人微信号 QR 注入或非官方 Session Bridge。')).toBeVisible();
  await expect(modal).toContainText('暂不宣称群聊、附件、卡片审批、原消息更新或无限制主动推送');

  await modal.getByPlaceholder('Production On-call').fill('WeChat Production');
  await modal.getByPlaceholder('wx...').fill('wx-test-app');
  await modal.getByRole('button', { name: 'Next' }).click();
  await modal.getByRole('button', { name: 'Save & Test' }).click();

  await expect.poll(() => createPayload).not.toBeNull();
  expect(createPayload.type).toBe('WECHAT');
  expect(createPayload.config.connectionMode).toBe('WEBHOOK');
  expect(createPayload.config.appId).toBe('wx-test-app');
  expect(String(createPayload.credentialRef)).toContain('WECHAT_APP_SECRET');
  expect(String(createPayload.config.verificationTokenRef)).toContain('WECHAT_VERIFY_TOKEN');
  expect(String(createPayload.config.encodingAesKeyRef)).toContain('WECHAT_ENCODING_AES_KEY');

  await expect(modal.locator('input[value*="channel-wechat/wechat/callback"]')).toBeVisible();
  await expect(modal).toContainText('没有真实 Provider 凭据或真实消息时');
});
