import { expect, test } from '@playwright/test';

type Json = Record<string, any>;

const authenticate = async (page: import('@playwright/test').Page) => {
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
};

test('Chat exposes ReAct plus named workflows and freezes execution per session', async ({ page }) => {
  await authenticate(page);

  const createRequests: Json[] = [];
  const streamRequests: Json[] = [];
  let sequence = 0;
  let sessions: Json[] = [];

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path.endsWith('/api/v1/user/chat/catalog/projects')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: '0000', info: 'success', data: [
            { projectId: 'demo-project', name: 'Example Service', defaultAgentId: 'default-react' },
          ],
        }),
      });
    }
    if (path.endsWith('/api/v1/user/chat/catalog/projects/demo-project/agents')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: '0000', info: 'success', data: [
            {
              agentId: 'default-react', name: '内部默认 Agent', engine: 'GRAPH', version: 4,
              definitionKind: 'MAIN_ASSISTANT',
            },
            {
              agentId: 'daily-inspection', name: '每日生产巡检', engine: 'GRAPH', version: 7,
              definitionKind: 'SPECIALIZED_WORKFLOW',
            },
          ],
        }),
      });
    }
    if (path.endsWith('/api/v1/user/chat/model-status')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: { chatAvailable: true } }),
      });
    }
    if (path.endsWith('/api/v1/user/chat/session') && method === 'POST') {
      const body = request.postDataJSON() as Json;
      createRequests.push(body);
      const sessionId = `session-${++sequence}`;
      sessions = [{
        sessionId,
        projectId: 'demo-project',
        agentId: body.agentId || 'default-react',
        title: '新会话',
        stateVersion: 1,
        metadata: body.metadata || {},
      }, ...sessions];
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: sessionId }),
      });
    }
    if (path.endsWith('/api/v1/user/chat/sessions') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: sessions }),
      });
    }
    if (path.endsWith('/api/v1/user/chat/stream') && method === 'POST') {
      streamRequests.push(request.postDataJSON() as Json);
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: 'data: {"eventType":"DONE","status":"SUCCEEDED","summary":"done","payload":{}}\n\n',
      });
    }
    if (/\/api\/v1\/user\/chat\/sessions\/[^/]+\/(messages|events)$/.test(path)) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0000', info: 'success', data: [] }),
      });
    }

    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data: [] }),
    });
  });

  await page.goto('/chat?projectId=demo-project');

  const executionSelect = page.getByTestId('execution-selector').getByRole('combobox');
  await expect(executionSelect).toBeVisible();
  await expect(page.getByText('内部默认 Agent', { exact: true })).toHaveCount(0);

  await executionSelect.click();
  await expect(page.getByText('ReAct', { exact: true }).last()).toBeVisible();
  await expect(page.getByText('每日生产巡检', { exact: true }).last()).toBeVisible();
  await page.getByText('每日生产巡检', { exact: true }).last().click();
  await expect(page.getByTestId('execution-selector')).toHaveAttribute('data-execution', 'daily-inspection');
  await expect.poll(() => createRequests.length).toBe(1);
  expect(createRequests[0].agentId).toBe('daily-inspection');
  expect(createRequests[0].metadata).toMatchObject({
    executionType: 'WORKFLOW',
    agentName: '每日生产巡检',
  });

  await page.getByPlaceholder(/直接描述问题或任务/).fill('执行巡检');
  await page.getByRole('button', { name: '发送消息' }).click();
  await expect.poll(() => streamRequests.length).toBe(1);
  expect(streamRequests[0].agentDefinitionId).toBe('daily-inspection');

  // Switching execution on an existing Workflow session must create a new session, not mutate the old session.
  await executionSelect.click();
  await page.getByText('ReAct', { exact: true }).last().click();
  await expect.poll(() => createRequests.length).toBe(2);
  expect(createRequests[1].agentId).toBeUndefined();
  expect(createRequests[1].metadata).toMatchObject({ executionType: 'REACT', agentName: 'ReAct' });

  await page.getByPlaceholder(/直接描述问题或任务/).fill('动态排查');
  await page.getByRole('button', { name: '发送消息' }).click();
  await expect.poll(() => streamRequests.length).toBe(2);
  expect(streamRequests[1].agentDefinitionId).toBeUndefined();
});
