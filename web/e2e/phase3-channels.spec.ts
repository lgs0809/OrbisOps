import { fulfillAccessibleProjects } from './fixtures/project-catalog';
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
  const consoleErrors: string[] = [];
  const pageErrors: string[] = [];
  const failedResponses: string[] = [];
  page.on('console', (message) => {
    if (message.type() !== 'error') return;
    const text = message.text();
    const knownThirdPartyDevWarning = text.includes('findDOMNode is deprecated')
      && text.includes('ReactResizeObserver');
    if (!knownThirdPartyDevWarning && !text.includes('ResizeObserver loop completed with undelivered notifications')) {
      consoleErrors.push(text);
    }
  });
  page.on('pageerror', (error) => {
    if (!error.message.includes('ResizeObserver loop completed with undelivered notifications')) pageErrors.push(error.message);
  });
  page.on('response', (response) => {
    if (response.status() >= 400) failedResponses.push(`${response.status()} ${response.url()}`);
  });

  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }])) return;
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
  const providerGrid = page.getByTestId('channel-provider-cards');
  const connectButtons = providerGrid.getByRole('button', { name: '接入' });

  await connectButtons.nth(0).click();
  let modal = page.locator('.semi-modal').filter({ hasText: '接入渠道' });
  await expect(modal).toContainText('Telegram');
  await expect(modal.getByPlaceholder('@orbisops_bot')).toBeVisible();
  await expect(modal).toContainText('LONG_CONNECTION');
  await expect(modal).toContainText('群聊中必须 @Bot 才响应');
  await modal.getByRole('button', { name: 'cancel' }).click();

  await connectButtons.nth(1).click();
  modal = page.locator('.semi-modal').filter({ hasText: '接入渠道' });
  await expect(modal).toContainText('Discord');
  await expect(modal.getByPlaceholder('@orbisops_bot')).toBeVisible();
  await expect(modal).toContainText('已启用 Message Content Intent');
  await modal.getByRole('button', { name: 'cancel' }).click();

  await connectButtons.nth(2).click();
  modal = page.locator('.semi-modal').filter({ hasText: '接入渠道' });
  await expect(modal).toContainText('QQ');
  await expect(modal).toContainText('App / Client ID');
  await expect(modal).toContainText('凭据环境变量');

  expect(consoleErrors).toEqual([]);
  expect(pageErrors).toEqual([]);
  expect(failedResponses).toEqual([]);
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
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' }])) return;
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

  await row.getByRole('button', { name: '查看' }).click();
  await page.getByRole('tab', { name: '消息' }).click();
  await expect.poll(() => messageLimits).toEqual([100]);
  expect(identityRequests).toBe(0);

  await page.getByRole('tab', { name: '身份与访问控制' }).click();
  await expect.poll(() => messageLimits).toEqual([100, 200]);
  await expect.poll(() => identityRequests).toBe(1);
});
