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

test('protected operations routes fail closed to login without a session', async ({ page }) => {
  await page.goto('/executions');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText('欢迎登录')).toBeVisible();
});

test('authenticated project members land in the operations inbox', async ({ page }) => {
  await authenticate(page, 'user');
  await page.goto('/login');

  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByText('工作台', { exact: true }).first()).toBeVisible();
});

test('legacy user execution route converges on the canonical Execution Center', async ({ page }) => {
  await authenticate(page, 'user');
  await page.goto('/my-executions');

  await expect(page).toHaveURL(/\/executions/);
  await expect(page.getByText('执行中心', { exact: true }).first()).toBeVisible();
  await expect(page.getByText(/后端 capability facts/)).toBeVisible();
});

test('legacy admin change route converges on the same Execution Center', async ({ page }) => {
  await authenticate(page, 'admin');
  await page.goto('/change-center');

  await expect(page).toHaveURL(/\/executions/);
  await expect(page.getByText('执行中心', { exact: true }).first()).toBeVisible();
});

test('historical chat restores persisted run events and terminal status', async ({ page }) => {
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
      data = [{ projectId: 'demo-project', name: 'Example Service', defaultAgentId: 'demo-ops-agent' }];
    } else if (path.endsWith('/api/v1/user/chat/catalog/projects/demo-project/agents')) {
      data = [{ agentId: 'demo-ops-agent', name: '默认诊断 Agent', engine: 'REACT', definitionKind: 'DEFAULT_REACT' }];
    } else if (path.endsWith('/api/v1/user/chat/model-status')) {
      data = { chatAvailable: true };
    } else if (path.endsWith('/api/v1/user/chat/sessions')) {
      data = [{
        sessionId: 'history-session',
        projectId: 'demo-project',
        agentId: 'demo-ops-agent',
        title: '历史验收会话',
        stateVersion: 1,
        metadata: {},
      }];
    } else if (path.endsWith('/api/v1/user/chat/sessions/history-session/messages')) {
      data = [{ messageId: 'm1', role: 'assistant', content: '历史诊断完成。', createdAt: '2026-08-13 09:31:10' }];
    } else if (path.endsWith('/api/v1/user/chat/sessions/history-session/events')) {
      data = [
        {
          runId: 'run-1', analysisId: 'history-session', sequence: 1,
          eventType: 'SOURCE_QUERY_FINISHED', status: 'SUCCEEDED',
          summary: 'PROMETHEUS authoritative datasource query succeeded',
          payload: { sessionId: 'history-session', sourceType: 'PROMETHEUS', resultId: 'tool-result-1', outputHash: 'hash-1', evidenceId: 'evidence-1', verified: true },
        },
        {
          runId: 'run-1', analysisId: 'history-session', sequence: 2,
          eventType: 'DONE', status: 'SUCCEEDED', summary: 'Agent 任务执行完成。',
          payload: { sessionId: 'history-session' },
        },
      ];
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });

  await page.goto('/chat?projectId=demo-project');
  await page.getByText('历史验收会话', { exact: true }).click();
  await expect(page.getByRole('status')).toContainText('诊断完成');
  await expect(page.getByRole('status')).not.toContainText('等待开始诊断');
  await page.getByRole('button', { name: '查看运行详情' }).click();
  await expect(page.getByText('PROMETHEUS authoritative datasource query succeeded', { exact: true })).toBeVisible();
});
