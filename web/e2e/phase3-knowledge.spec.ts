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

test('knowledge search stays separate from the selected knowledge context', async ({ page }) => {
  await authenticateAdmin(page);

  const requestedPaths: string[] = [];
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    requestedPaths.push(`${route.request().method()} ${path}${url.search}`);

    let data: unknown = [];
    if (path.endsWith('/api/v1/admin/ai-client-rag-order/knowledge-bases/global')) {
      data = [
        {
          kbId: 'ops-public',
          knowledgeTag: 'ops-public',
          kbName: 'Operations Knowledge',
          description: 'Shared operations procedures',
          scope: 'GLOBAL',
          status: 'ENABLED',
          chunkCount: 2,
          documentCount: 1,
          usedProjectCount: 1,
        },
      ];
    } else if (path.endsWith('/knowledge-bases/global/stats')) {
      data = { chunkCount: url.searchParams.get('kbId') ? 2 : 2, documentCount: 1, byType: [], bySource: [] };
    } else if (path.endsWith('/knowledge-bases/global/ops-public/chunks')) {
      data = [
        {
          chunkId: 'chunk-1',
          kbId: 'ops-public',
          knowledgeTag: 'ops-public',
          tag: 'ops-public',
          fileName: 'runbook.md#chunk-1',
          displayName: 'runbook.md',
          documentType: 'MARKDOWN',
          chunkStrategy: 'STRUCTURE_FIRST',
          size: 256,
          updateTime: '2026-08-17T12:00:00',
        },
      ];
    } else if (path.endsWith('/knowledge-bases/global/ops-public/usage-projects')) {
      data = [{ projectId: 'project-1', projectName: 'Core Platform', status: 'ENABLED' }];
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });

  await page.goto('/platform/knowledge');
  await expect(page.getByRole('heading', { name: '知识库', exact: true }).first()).toBeVisible();
  await expect(page.getByText('Operations Knowledge', { exact: true })).toBeVisible();

  expect(requestedPaths.some((value) => value.includes('/ops-public/chunks'))).toBe(false);
  expect(requestedPaths.some((value) => value.includes('/ops-public/usage-projects'))).toBe(false);

  const searchInput = page.getByPlaceholder('搜索知识库');
  await searchInput.fill('ops');
  await expect(page.getByText('Operations Knowledge', { exact: true })).toBeVisible();

  expect(requestedPaths.some((value) => value.includes('/ops-public/chunks'))).toBe(false);
  expect(requestedPaths.some((value) => value.includes('/ops-public/usage-projects'))).toBe(false);
  await expect(page.getByRole('button', { name: '检索策略' })).toHaveCount(0);

  const knowledgeRow = page.locator('tr').filter({ hasText: 'Operations Knowledge' }).first();
  await knowledgeRow.getByRole('button', { name: '打开', exact: true }).click();

  await expect.poll(() => requestedPaths.some((value) => value.includes('/ops-public/chunks'))).toBe(true);
  await expect.poll(() => requestedPaths.some((value) => value.includes('/ops-public/usage-projects'))).toBe(true);
  await page.getByRole('tab', { name: 'Project 授权' }).click();
  await expect(page.getByText('Core Platform', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '检索策略' })).toBeEnabled();
});
