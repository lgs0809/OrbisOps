import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Channel compatibility matrix renders backend capability facts without inventing support', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

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
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([
        {
          type: 'WECHAT', displayName: 'WeChat', supportsInbound: true, supportsOutbound: true,
          connectionModes: ['WEBHOOK'],
          capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'DIRECT_MESSAGES', 'WEBHOOK', 'REPLY_TO_INBOUND'] },
        },
        {
          type: 'DISCORD', displayName: 'Discord', supportsInbound: true, supportsOutbound: true,
          connectionModes: ['LONG_CONNECTION'],
          capabilitySet: { capabilities: ['INBOUND', 'OUTBOUND', 'DIRECT_MESSAGES', 'GROUP_MESSAGES', 'INTERACTIVE_ACTIONS', 'MESSAGE_UPDATE', 'ATTACHMENTS', 'PROACTIVE_PUSH'] },
        },
      ]) });
    }
    if (path.endsWith('/api/v1/admin/ops/channels')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/integrations?projectId=demo-project');
  const matrix = page.getByTestId('channel-compatibility-matrix');
  await expect(matrix).toBeVisible();
  await expect(matrix).toContainText('Capability facts come directly from the backend Provider Adapter Catalog');

  const wechat = matrix.getByRole('row').filter({ hasText: 'WeChat' });
  await expect(wechat).toContainText('WEBHOOK');
  await expect(wechat.getByLabel('Supported', { exact: true })).toHaveCount(4);
  await expect(wechat.getByLabel('Not supported', { exact: true })).toHaveCount(5);

  const discord = matrix.getByRole('row').filter({ hasText: 'Discord' });
  await expect(discord).toContainText('LONG_CONNECTION');
  await expect(discord.getByLabel('Supported', { exact: true })).toHaveCount(8);
  await expect(discord.getByLabel('Not supported', { exact: true })).toHaveCount(1);

  const slack = matrix.getByRole('row').filter({ hasText: 'Slack' });
  await expect(slack).toContainText('Not installed');
  await expect(slack.getByLabel('Supported', { exact: true })).toHaveCount(0);
  await expect(slack.getByLabel('Not supported', { exact: true })).toHaveCount(9);
});
