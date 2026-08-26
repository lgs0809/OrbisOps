import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Tool Routing uses one project-scoped overview query boundary and explicit refresh', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  const requestCounts = new Map<string, number>();
  const overviewPaths = [
    '/api/v1/admin/ops/projects/demo-project/tool-catalog-summary',
    '/api/v1/admin/ops/projects/demo-project/tool-router/decisions',
    '/api/v1/admin/ops/projects/demo-project/mcp-tool-calls',
    '/api/v1/admin/ops/projects/demo-project/mcp-tool-snapshots',
    '/api/v1/admin/ops/projects/demo-project/mcp-tool-policies',
    '/api/v1/admin/ops/projects/demo-project/mcp-tool-activations',
  ];

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', description: 'Project-scoped tools' }])) return;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project', description: 'Project-scoped tools' }],
      }) });
    }

    const matched = overviewPaths.find((candidate) => path === candidate);
    if (matched) {
      requestCounts.set(matched, (requestCounts.get(matched) || 0) + 1);
      const body = matched.endsWith('/tool-catalog-summary')
        ? { catalogVersion: 3, toolCount: 2, tools: [] }
        : [];
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(body) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/advanced/tool-routing?projectId=demo-project');
  await expect(page.getByText('Project-scoped tools')).toBeVisible();
  await expect(page.getByText('目录版本：3')).toBeVisible();
  await expect.poll(() => overviewPaths.every((path) => requestCounts.get(path) === 1)).toBe(true);

  await page.getByRole('button', { name: '刷新' }).click();
  await expect.poll(() => overviewPaths.every((path) => requestCounts.get(path) === 2)).toBe(true);
});
