import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Skills keeps detail auxiliaries on demand and refreshes catalog plus active detail coherently', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  const counts = {
    global: 0,
    builtIn: 0,
    detail: 0,
    artifacts: 0,
    context: 0,
    versions: 0,
    usage: 0,
    projects: 0,
    reload: 0,
  };
  const summary = {
    skillId: 'skill-http',
    name: 'HTTP Diagnosis',
    description: 'Diagnose elevated HTTP errors',
    scope: 'GLOBAL',
    status: 'ENABLED',
    origin: 'MANUAL',
    updateMode: 'MANUAL_ONLY',
    contentLength: 42,
    referencedBy: [],
  };
  const detail = {
    ...summary,
    content: '# HTTP Diagnosis\nInspect metrics before logs.',
    markdown: '# HTTP Diagnosis\nInspect metrics before logs.',
    frontMatter: { category: 'OBSERVABILITY', whenToUse: ['HTTP 5xx'] },
    autoUpdateEnabled: false,
    autoMergeEnabled: false,
  };

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    if (path === '/api/v1/admin/ops/skills/global' && method === 'GET') {
      counts.global += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([summary]) });
    }
    if (path === '/api/v1/admin/ops/skills' && method === 'GET') {
      counts.builtIn += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path === '/api/v1/admin/ops/skills/global/skill-http' && method === 'GET') {
      counts.detail += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response(detail) });
    }
    if (path === '/api/v1/admin/ops/skills/global/skill-http/artifacts' && method === 'GET') {
      counts.artifacts += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    if (path === '/api/v1/admin/ops/skills/context' && method === 'GET') {
      counts.context += 1;
      expect(url.searchParams.getAll('names')).toEqual(['HTTP Diagnosis']);
      expect(url.searchParams.get('maxChars')).toBe('12000');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        names: ['HTTP Diagnosis'], content: 'Rendered HTTP diagnosis context', length: 31,
      }) });
    }
    if (path === '/api/v1/admin/ops/skills/global/skill-http/versions' && method === 'GET') {
      counts.versions += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        skillId: 'skill-http', version: 2, sourceType: 'MANUAL', changeSummary: 'Refine evidence order',
      }]) });
    }
    if (path === '/api/v1/admin/ops/skills/global/skill-http/usage' && method === 'GET') {
      counts.usage += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        projectId: 'demo-project', agentId: 'default-react', agentName: 'Default ReAct', type: 'agent',
      }]) });
    }
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project' }])) return;
    if (path === '/api/v1/admin/ops-projects/snapshot' && method === 'GET') {
      counts.projects += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        projects: [{ projectId: 'demo-project', name: 'Demo Project' }], templates: [],
      }) });
    }
    if (path === '/api/v1/admin/ops/skills/reload' && method === 'POST') {
      counts.reload += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/skills');
  await expect(page.getByText('HTTP Diagnosis').first()).toBeVisible();
  await expect(page.getByText('Rendered HTTP diagnosis context')).toBeVisible();
  expect(counts.global).toBe(1);
  expect(counts.builtIn).toBe(1);
  expect(counts.detail).toBe(1);
  expect(counts.artifacts).toBe(1);
  expect(counts.context).toBe(1);
  expect(counts.versions).toBe(0);
  expect(counts.usage).toBe(0);
  const projectRequestBaseline = counts.projects;

  await page.getByRole('button', { name: '版本' }).click();
  await expect.poll(() => counts.versions).toBe(1);
  await expect(page.getByText('Refine evidence order')).toBeVisible();
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: '使用位置' }).click();
  await expect.poll(() => counts.usage).toBe(1);
  await expect(page.getByText('Default ReAct')).toBeVisible();
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: '复制到 Project' }).click();
  await expect.poll(() => counts.projects).toBe(projectRequestBaseline + 1);
  await expect(page.getByText('Demo Project（demo-project）')).toBeVisible();
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: '刷新' }).click();
  await expect.poll(() => counts.reload).toBe(1);
  await expect.poll(() => counts.global).toBe(2);
  await expect.poll(() => counts.detail).toBe(2);
  await expect.poll(() => counts.artifacts).toBe(2);
  await expect.poll(() => counts.context).toBe(2);
});
