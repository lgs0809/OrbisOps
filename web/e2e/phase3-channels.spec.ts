import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

const phase3Types = [
  {
    type: 'TELEGRAM', displayName: 'Telegram', supportsInbound: true, supportsOutbound: true,
    connectionModes: ['LONG_CONNECTION'],
    capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'MESSAGE_UPDATE', 'INTERACTIVE_ACTIONS', 'LONG_CONNECTION', 'PROACTIVE_PUSH'] },
  },
  {
    type: 'DISCORD', displayName: 'Discord', supportsInbound: true, supportsOutbound: true,
    connectionModes: ['LONG_CONNECTION'],
    capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'MESSAGE_UPDATE', 'INTERACTIVE_ACTIONS', 'LONG_CONNECTION', 'PROACTIVE_PUSH'] },
  },
  {
    type: 'QQ', displayName: 'QQ', supportsInbound: true, supportsOutbound: true,
    connectionModes: ['LONG_CONNECTION'],
    capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'INTERACTIVE_ACTIONS', 'LONG_CONNECTION', 'PROACTIVE_PUSH'] },
  },
];

test('Phase 3 Channel setup exposes only implemented Telegram, Discord, and QQ transports', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }],
      }) });
    }
    if (path.includes('/api/v1/admin/ops-agents')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/types')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(phase3Types) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/integrations?projectId=demo-project');
  await page.getByRole('button', { name: '新建 Channel' }).click();

  const modal = page.locator('.semi-modal').filter({ hasText: 'Connect' });
  const providerSelect = modal.getByRole('combobox').first();

  await providerSelect.click();
  await page.getByRole('option', { name: 'Telegram' }).click();
  await expect(modal.getByPlaceholder('@orbisops_bot')).toBeVisible();
  await expect(modal).toContainText('Bot API long polling');
  await expect(modal).toContainText('Inline Keyboard');

  await providerSelect.click();
  await page.getByRole('option', { name: 'Discord' }).click();
  await expect(modal.getByPlaceholder('@orbisops_bot')).toHaveCount(0);
  await expect(modal).toContainText('Gateway v10 WebSocket');
  await expect(modal).toContainText('MESSAGE_CONTENT privileged intent');
  await expect(modal).toContainText('Button component');

  await providerSelect.click();
  await page.getByRole('option', { name: 'QQ' }).click();
  await expect(modal.getByPlaceholder('1020...')).toBeVisible();
  await expect(modal).toContainText('QQ Gateway WebSocket');
  await expect(modal).toContainText('暂不宣称 QQ Guild、附件或原消息更新');
});

test('Channel detail queries stay on demand and isolate message limits', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  const messageLimits: number[] = [];
  let identityRequests = 0;
  const channel = {
    channelId: 'channel-1', projectId: 'demo-project', name: 'Telegram Ops', type: 'TELEGRAM',
    credentialRef: '${env:TELEGRAM_OPS_BOT_TOKEN}', config: { connectionMode: 'LONG_CONNECTION', botUsername: '@orbisops_bot' },
    executionType: 'NONE', accessPolicy: 'DENY_UNKNOWN', status: 'ACTIVE',
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }],
      }) });
    }
    if (path.includes('/api/v1/admin/ops-agents')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/types')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(phase3Types) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([channel]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/channel-1/messages')) {
      messageLimits.push(Number(url.searchParams.get('limit')));
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels/channel-1/identities')) {
      identityRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/integrations?projectId=demo-project');
  const row = page.getByRole('row').filter({ hasText: 'Telegram Ops' });
  await expect(row).toBeVisible();
  expect(messageLimits).toEqual([]);
  expect(identityRequests).toBe(0);

  await row.getByRole('button', { name: '消息' }).click();
  await expect.poll(() => messageLimits).toEqual([100]);
  expect(identityRequests).toBe(0);

  await page.keyboard.press('Escape');
  await row.getByRole('button', { name: '身份' }).click();
  await expect.poll(() => messageLimits).toEqual([100, 200]);
  await expect.poll(() => identityRequests).toBe(1);
});
