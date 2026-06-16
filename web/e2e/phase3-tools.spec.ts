import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Tools / MCP loads template catalog once and generated tools only when detail opens', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  let listRequests = 0;
  let generatedToolRequests = 0;
  const template = {
    templateId: 'mysql-readonly',
    templateName: 'MySQL Readonly',
    resourceType: 'MYSQL',
    transportType: 'STDIO',
    defaultTransportConfig: {},
    supportedActions: ['query'],
    riskLevel: 'LOW',
    readOnly: true,
    description: 'Readonly database access',
    status: 'ENABLED',
    projectCount: 1,
    generatedToolCount: 1,
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/admin/ops/mcp-templates') {
      listRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([template]) });
    }
    if (path === '/api/v1/admin/ops/mcp-templates/mysql-readonly/generated-tools') {
      generatedToolRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        mcpId: 'tool-1', toolId: 'tool-1', mcpName: 'MySQL Readonly', toolName: 'MySQL Readonly',
        projectId: 'demo-project', resourceId: 'mysql-demo', resourceType: 'MYSQL',
        transportType: 'STDIO', transportConfig: {}, status: 'ACTIVE',
      }]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/tools');
  const row = page.getByRole('row').filter({ hasText: 'MySQL Readonly' });
  await expect(row).toBeVisible();
  expect(listRequests).toBe(1);
  expect(generatedToolRequests).toBe(0);

  await row.getByRole('button', { name: '查看' }).click();
  await expect(page.getByText('已生成的 Project 工具')).toBeVisible();
  await expect.poll(() => generatedToolRequests).toBe(1);
  await expect(page.getByText('demo-project')).toBeVisible();

  await page.getByRole('button', { name: '关闭' }).click();
  await page.getByRole('button', { name: '刷新' }).click();
  await expect.poll(() => listRequests).toBe(2);
});
