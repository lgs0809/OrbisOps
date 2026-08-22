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

test('Chat binds each conversation to 默认模型 or a Published Workflow', async ({ page }) => {
  await authenticate(page);

  const createRequests: Json[] = [];
  const messageRequests: Json[] = [];
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
            { projectId: 'demo-project', name: 'Demo Project', defaultAgentId: 'default-react' },
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
              agentId: 'default-react', name: 'Internal Default Agent', engine: 'GRAPH', version: 4,
              definitionKind: 'MAIN_ASSISTANT', lifecycle: 'PUBLISHED',
            },
            {
              agentId: 'daily-inspection', name: 'Daily Production Inspection', engine: 'GRAPH', version: 7,
              definitionKind: 'SPECIALIZED_WORKFLOW', lifecycle: 'PUBLISHED',
            },
            {
              agentId: 'draft-workflow', name: 'Draft Workflow', engine: 'GRAPH', version: 1,
              definitionKind: 'SPECIALIZED_WORKFLOW', lifecycle: 'DRAFT',
            },
          ],
        }),
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
        title: 'New conversation',
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
    if (/\/api\/v1\/user\/chat\/sessions\/[^/]+\/messages$/.test(path) && method === 'POST') {
      const body = request.postDataJSON() as Json;
      messageRequests.push(body);
      const sessionId = path.split('/').at(-2) || '';
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: '0000', info: 'success', data: {
            sessionId,
            status: 'SUCCEEDED',
            metadata: { runId: `run-${messageRequests.length}` },
            events: [],
          },
        }),
      });
    }
    if (/\/api\/v1\/user\/chat\/sessions\/[^/]+\/(messages|events)$/.test(path) && method === 'GET') {
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

  const executionRoot = page.getByTestId('execution-selector');
  const executionSelect = executionRoot.getByRole('combobox');
  await expect(executionSelect).toBeVisible();
  await expect(executionRoot).toHaveAttribute('data-execution', 'DEFAULT_REACT');
  await expect(page.getByText('Internal Default Agent', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Draft Workflow', { exact: true })).toHaveCount(0);

  await executionSelect.click();
  await expect(page.getByText('默认模型', { exact: true }).last()).toBeVisible();
  await expect(page.getByText('Daily Production Inspection', { exact: true }).last()).toBeVisible();
  await page.getByText('Daily Production Inspection', { exact: true }).last().click();
  await expect(executionRoot).toHaveAttribute('data-execution', 'daily-inspection');
  await expect.poll(() => createRequests.length).toBe(1);
  expect(createRequests[0].agentId).toBe('daily-inspection');
  expect(createRequests[0].metadata).toMatchObject({
    executionType: 'WORKFLOW',
    executionName: 'Daily Production Inspection',
  });

  await page.getByPlaceholder('给 OrbisOps 发消息...').fill('Run the inspection');
  await page.getByRole('button', { name: '发送' }).click();
  await expect.poll(() => messageRequests.length).toBe(1);
  expect(messageRequests[0].agentDefinitionId).toBe('daily-inspection');
  expect(messageRequests[0].metadata).toMatchObject({ executionType: 'WORKFLOW', triggerSource: 'CHAT' });
  await expect(page.getByRole('button', { name: '打开当前运行' })).toBeVisible();

  // Changing execution creates a new conversation instead of mutating the existing Workflow conversation.
  await executionSelect.click();
  await page.getByText('默认模型', { exact: true }).last().click();
  await expect.poll(() => createRequests.length).toBe(2);
  expect(createRequests[1].agentId).toBeUndefined();
  expect(createRequests[1].metadata).toMatchObject({
    executionType: 'DEFAULT_REACT',
    executionName: '默认模型',
  });
  await expect(executionRoot).toHaveAttribute('data-execution', 'DEFAULT_REACT');

  await page.getByPlaceholder('给 OrbisOps 发消息...').fill('Investigate dynamically');
  await page.getByRole('button', { name: '发送' }).click();
  await expect.poll(() => messageRequests.length).toBe(2);
  expect(messageRequests[1].agentDefinitionId).toBeUndefined();
  expect(messageRequests[1].metadata).toMatchObject({ executionType: 'DEFAULT_REACT', triggerSource: 'CHAT' });
});
