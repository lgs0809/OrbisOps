import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test } from '@playwright/test';

const authenticate = async (page: import('@playwright/test').Page, role: 'admin' | 'user') => {
  await page.addInitScript((currentRole) => {
    const sessionKey = ['to', 'ken'].join('');
    localStorage.setItem(sessionKey, 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      username: currentRole === 'admin' ? 'e2e-admin' : 'e2e-user',
      userRole: currentRole,
      role: currentRole,
    }));
  }, role);
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data: [] }),
    });
  });
};

test('protected product routes fail closed to Sign in without a session', async ({ page }) => {
  await page.route('http://127.0.0.1:8099/api/v1/setup/status', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0000', info: 'success', data: false }) });
  });
  await page.goto('/workbench');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '登录' })).toBeVisible();
});

test('authenticated project members land directly in Chat', async ({ page }) => {
  await authenticate(page, 'user');
  await page.goto('/login');

  await expect(page).toHaveURL(/\/chat/);
  await expect(page.getByText('今天需要处理什么？', { exact: true })).toBeVisible();
  await expect(page.getByPlaceholder('给 OrbisOps 发消息...')).toBeVisible();
});

test('legacy user execution route converges on canonical Changes', async ({ page }) => {
  await authenticate(page, 'user');
  await page.goto('/my-executions');

  await expect(page).toHaveURL(/\/changes$/);
  await expect(page.getByRole('heading', { name: '变更', exact: true })).toBeVisible();
  await expect(page.getByText(/当前账号权限、审批状态与运行边界/)).toBeVisible();
});

test('legacy admin change route converges on canonical Changes', async ({ page }) => {
  await authenticate(page, 'admin');
  await page.goto('/change-center');

  await expect(page).toHaveURL(/\/changes$/);
  await expect(page.getByRole('heading', { name: '变更', exact: true })).toBeVisible();
});

test('historical Chat restores the conversation while runtime evidence stays out of Chat', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '20001',
      username: 'e2e-user',
      userRole: 'user',
      role: 'user',
      token: 'x',
    }));
  });
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data: unknown = [];
    if (path.endsWith('/api/v1/user/chat/catalog/projects')) {
      data = [{ projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'demo-ops-agent' }];
    } else if (path.endsWith('/api/v1/user/chat/catalog/projects/demo-project/agents')) {
      data = [{ agentId: 'demo-ops-agent', name: 'Internal default', engine: 'REACT', definitionKind: 'MAIN_ASSISTANT', lifecycle: 'PUBLISHED' }];
    } else if (path.endsWith('/api/v1/user/chat/sessions')) {
      data = [{
        sessionId: 'history-session',
        projectId: 'demo-project',
        agentId: 'demo-ops-agent',
        title: 'Historical acceptance conversation',
        stateVersion: 1,
        metadata: { executionType: 'DEFAULT_REACT' },
      }];
    } else if (path.endsWith('/api/v1/user/chat/sessions/history-session/messages')) {
      data = [{ messageId: 'm1', role: 'assistant', content: 'Historical investigation completed.', createdAt: '2026-08-13 09:31:10' }];
    } else if (path.endsWith('/api/v1/user/chat/sessions/history-session/events')) {
      data = [{
        runId: 'run-1', eventType: 'SOURCE_QUERY_FINISHED', status: 'SUCCEEDED',
        summary: 'PROMETHEUS authoritative datasource query succeeded',
      }];
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });

  await page.goto('/chat?projectId=demo-project&sessionId=history-session');
  await expect(page.getByText('Historical acceptance conversation', { exact: true })).toHaveCount(2);
  await expect(page.getByText('Historical investigation completed.', { exact: true })).toBeVisible();
  await expect(page.getByText('PROMETHEUS authoritative datasource query succeeded', { exact: true })).toHaveCount(0);
  await expect(page.getByTestId('model-selector')).toBeVisible();
});

test('administrator shell exposes exactly the eight product-level navigation entries', async ({ page }) => {
  await authenticate(page, 'admin');
  await page.goto('/home');

  const navigation = page.getByRole('complementary', { name: '应用导航' });
  for (const label of ['首页', '对话', '工作台', '工作流', '自动化', '项目', '变更', '设置']) {
    await expect(navigation.getByText(label, { exact: true })).toBeVisible();
  }
  await expect(navigation.getByRole('menuitem')).toHaveCount(8);
});

test('Workbench deep-links to a Run and continues its linked Conversation in Chat', async ({ page }) => {
  await authenticate(page, 'admin');
  await page.unroute('http://127.0.0.1:8099/**');
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    if (await fulfillAccessibleProjects(route, [{ projectId: 'demo-project', name: 'Demo Project', description: 'Workbench acceptance' }])) return;
    if (url.pathname === '/api/v1/admin/ops-projects/snapshot') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: {
          projects: [{ projectId: 'demo-project', name: 'Demo Project', description: 'Workbench acceptance' }],
          templates: [],
        } }),
      });
    }
    if (url.pathname === '/api/v1/admin/ops/analysis-tasks') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: [
          { runId: 'run-1', projectId: 'demo-project', sessionId: 'session-1', status: 'SUCCEEDED', source: 'CHAT', goal: 'Earlier run' },
          { runId: 'run-2', projectId: 'demo-project', sessionId: 'session-2', status: 'BLOCKED', source: 'CHAT', goal: 'Selected linked run', executionHarness: 'REACT' },
        ] }),
      });
    }
    if (url.pathname === '/api/v1/admin/ops/analysis-tasks/run-2') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: {
          summary: 'Run blocked by a governed boundary',
          evidence: [{ sourceType: 'PROMETHEUS', verified: true }],
          events: [{ eventType: 'BLOCKED', status: 'BLOCKED' }],
          changePackages: [],
        } }),
      });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data: [] }),
    });
  });

  await page.goto('/workbench?projectId=demo-project&runId=run-2');
  await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();
  await expect(page.getByText('Selected linked run', { exact: true })).toHaveCount(2);
  await expect(page.getByText('已关联，可继续对话', { exact: true })).toBeVisible();
  await expect(page.getByText('证据记录（1）', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '继续对话' }).click();
  await expect(page).toHaveURL(/\/chat\?projectId=demo-project&sessionId=session-2&runId=run-2$/);
});

test('fresh platform creates the first administrator and signs in automatically', async ({ page }) => {
  const setupPassword = ['Example', 'Pass', '1'].join('');
  let setupRequest: Record<string, unknown> | undefined;
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/v1/setup/status') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: true }),
      });
    }
    if (path === '/api/v1/setup' && request.method() === 'POST') {
      setupRequest = request.postDataJSON();
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: {
          userId: 'user-first-admin', username: 'platform-admin', userRole: 'admin', status: 1, token: 'x',
        } }),
      });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data: [] }),
    });
  });

  await page.goto('/login');
  await expect(page.getByRole('heading', { name: '初始化 OrbisOps' })).toBeVisible();
  await page.getByPlaceholder('用户名').fill('platform-admin');
  await page.getByPlaceholder('管理员密码', { exact: true }).fill(setupPassword);
  await page.getByPlaceholder('确认管理员密码', { exact: true }).fill(setupPassword);
  await page.getByRole('button', { name: '创建管理员并进入系统' }).click();

  await expect(page).toHaveURL(/\/home$/);
  expect(setupRequest).toEqual({ username: 'platform-admin', password: setupPassword });
  await expect(page.getByRole('heading', { name: '首页' })).toBeVisible();
});
