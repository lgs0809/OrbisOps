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

test('Chat binds each conversation to 默认助手 or a Published Workflow and streams the selected model', async ({ page }) => {
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
    if (path.endsWith('/api/v1/user/chat/catalog/models')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: '0000', info: 'success', data: [
            { modelId: 'model-fast', modelName: 'Fast Model', modelUsage: 'CHAT', modelType: 'CHAT' },
            { modelId: 'model-deep', modelName: 'Deep Model', modelUsage: 'CHAT', modelType: 'CHAT' },
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
    if (path.endsWith('/api/v1/user/chat/stream') && method === 'POST') {
      const body = request.postDataJSON() as Json;
      streamRequests.push(body);
      const runId = `run-${streamRequests.length}`;
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: [
          `data: ${JSON.stringify({ eventType: 'RUN_ACCEPTED', status: 'RUNNING', runId })}`,
          `data: ${JSON.stringify({ eventType: 'TEXT_DELTA', status: 'RUNNING', runId, content: 'Streaming ' })}`,
          `data: ${JSON.stringify({ eventType: 'TEXT_DELTA', status: 'RUNNING', runId, content: 'reply' })}`,
          `data: ${JSON.stringify({ eventType: 'FINAL_OUTPUT', status: 'SUCCEEDED', runId, content: 'Streaming reply' })}`,
          '',
        ].join('\n\n'),
      });
    }
    if (/\/api\/v1\/user\/chat\/runs\/run-\d+$/.test(path) && method === 'GET') {
      expect(url.searchParams.get('projectId')).toBe('demo-project');
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        code: '0000', info: 'success', data: {
          runId: path.split('/').pop(), projectId: 'demo-project', status: 'SUCCEEDED',
          response: { content: 'Streaming reply' },
        },
      }) });
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

  const composer = page.getByTestId('chat-composer');
  const messageScroll = page.getByTestId('chat-message-scroll');
  const projectRoot = page.getByTestId('project-selector');
  await expect(composer).toBeVisible();
  await expect(messageScroll).toBeVisible();
  await expect(projectRoot).toBeVisible();
  await expect(projectRoot).toHaveAttribute('data-project', 'demo-project');
  await expect.poll(async () => page.evaluate(() => document.scrollingElement?.scrollHeight === document.scrollingElement?.clientHeight)).toBe(true);
  const composerBox = await composer.boundingBox();
  const viewport = page.viewportSize();
  expect(composerBox).not.toBeNull();
  expect(viewport).not.toBeNull();
  expect((composerBox?.y || 0) + (composerBox?.height || 0)).toBeLessThanOrEqual((viewport?.height || 0) + 1);

  const executionRoot = page.getByTestId('execution-selector');
  const executionSelect = executionRoot.getByRole('combobox');
  const modelRoot = page.getByTestId('model-selector');
  const modelSelect = modelRoot.getByRole('combobox');
  await expect(executionSelect).toBeVisible();
  await expect(modelSelect).toBeVisible();
  await expect(executionRoot).toHaveAttribute('data-execution', 'DEFAULT_REACT');
  await expect(modelRoot).toHaveAttribute('data-model', 'DEFAULT');
  await expect(page.getByText('Internal Default Agent', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Draft Workflow', { exact: true })).toHaveCount(0);

  await modelSelect.click();
  await expect(page.getByText('Fast Model', { exact: true }).last()).toBeVisible();
  await page.getByText('Fast Model', { exact: true }).last().click();
  await expect(modelRoot).toHaveAttribute('data-model', 'model-fast');

  await executionSelect.click();
  await expect(page.getByText('默认助手', { exact: true }).last()).toBeVisible();
  await expect(page.getByText('Daily Production Inspection', { exact: true }).last()).toBeVisible();
  await page.getByText('Daily Production Inspection', { exact: true }).last().click();
  await expect(executionRoot).toHaveAttribute('data-execution', 'daily-inspection');
  await expect.poll(() => createRequests.length).toBe(1);
  expect(createRequests[0].agentId).toBe('daily-inspection');
  expect(createRequests[0].metadata).toMatchObject({
    executionType: 'WORKFLOW',
    executionName: 'Daily Production Inspection',
  });

  await page.getByPlaceholder('给 OrbisOps 发消息...').fill('帮我按平时的巡检流程看一下今天线上有没有明显异常，有的话把重点告诉我');
  await page.getByRole('button', { name: '发送' }).click();
  await expect.poll(() => streamRequests.length).toBe(1);
  expect(streamRequests[0].agentDefinitionId).toBe('daily-inspection');
  expect(streamRequests[0].modelId).toBe('model-fast');
  expect(streamRequests[0].metadata).toMatchObject({ executionType: 'WORKFLOW', triggerSource: 'CHAT' });
  await expect(page.getByRole('button', { name: '查看运行' })).toBeVisible();

  // Changing execution creates a new conversation instead of mutating the existing Workflow conversation.
  await executionSelect.click();
  await page.getByText('默认助手', { exact: true }).last().click();
  await expect.poll(() => createRequests.length).toBe(2);
  expect(createRequests[1].agentId).toBeUndefined();
  expect(createRequests[1].metadata).toMatchObject({
    executionType: 'DEFAULT_REACT',
    executionName: '默认助手',
  });
  await expect(executionRoot).toHaveAttribute('data-execution', 'DEFAULT_REACT');

  await page.getByPlaceholder('给 OrbisOps 发消息...').fill('这个先别按固定流程了，你自己看看刚才提到的问题到底怎么回事');
  await page.getByRole('button', { name: '发送' }).click();
  await expect.poll(() => streamRequests.length).toBe(2);
  expect(streamRequests[1].agentDefinitionId).toBeUndefined();
  expect(streamRequests[1].modelId).toBe('model-fast');
  expect(streamRequests[1].metadata).toMatchObject({ executionType: 'DEFAULT_REACT', triggerSource: 'CHAT' });
});
