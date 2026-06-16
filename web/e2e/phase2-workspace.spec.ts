import { fulfillAccessibleProjects } from './fixtures/project-catalog';
import { expect, test, type Page } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

const authenticateAdmin = async (page: Page) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });
};

const mockProjectApis = async (page: Page) => {
  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (await fulfillAccessibleProjects(route, [{
            projectId: 'demo-project',
            name: 'Demo Project',
            description: 'Phase 2 browser acceptance',
            owner: 'e2e-admin',
            environments: ['dev', 'prod'],
            knowledgeBaseId: 'kb-demo',
            defaultAgentId: 'default-react',
            skillIds: [],
            sharedMcpIds: [],
            resources: [],
            generatedMcps: [],
            resourceCount: 0,
            generatedMcpCount: 0,
            executionResourceCount: 0,
            readyForInvestigation: false,
            defaultAgentPublished: true,
            readinessReason: 'NO_RESOURCE_CONNECTED',
            diagnosisReadiness: { ready: false, checks: [], missing: ['resource'], nextAction: 'CONNECT_RESOURCE' },
            remediationReadiness: { ready: false, checks: [], missing: ['executionTarget'], nextAction: 'CONNECT_EXECUTION_TARGET' },
            onboarding: [
              { key: 'agent', label: '默认 Agent', completed: true },
              { key: 'resource', label: '证据来源', completed: false },
            ],
            diagnosticScenarios: [],
          }])) return;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: response({
          projects: [{
            projectId: 'demo-project',
            name: 'Demo Project',
            description: 'Phase 2 browser acceptance',
            owner: 'e2e-admin',
            environments: ['dev', 'prod'],
            knowledgeBaseId: 'kb-demo',
            defaultAgentId: 'default-react',
            skillIds: [],
            sharedMcpIds: [],
            resources: [],
            generatedMcps: [],
            resourceCount: 0,
            generatedMcpCount: 0,
            executionResourceCount: 0,
            readyForInvestigation: false,
            defaultAgentPublished: true,
            readinessReason: 'NO_RESOURCE_CONNECTED',
            diagnosisReadiness: { ready: false, checks: [], missing: ['resource'], nextAction: 'CONNECT_RESOURCE' },
            remediationReadiness: { ready: false, checks: [], missing: ['executionTarget'], nextAction: 'CONNECT_EXECUTION_TARGET' },
            onboarding: [
              { key: 'agent', label: '默认 Agent', completed: true },
              { key: 'resource', label: '证据来源', completed: false },
            ],
            diagnosticScenarios: [],
          }],
          templates: [],
        }),
      });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });
};

test('Project Workspace Advanced exposes five feature tabs and switches domain content', async ({ page }) => {
  await authenticateAdmin(page);
  await mockProjectApis(page);

  await page.goto('/projects?projectId=demo-project');
  for (const tab of ['概览', '资源', '能力', '成员', '运行配置']) {
    await expect(page.getByRole('tab', { name: tab })).toBeVisible();
  }

  await expect(page.getByText('默认助手：', { exact: false })).toBeVisible();
  await page.getByRole('tab', { name: '成员' }).click();
  await expect(page.getByText('项目成员', { exact: true })).toBeVisible();
  await expect(page.getByText('当前项目尚未分配用户。', { exact: true })).toBeVisible();

  await page.getByRole('tab', { name: '运行配置' }).click();
  await expect(page.getByText(/生产变更仍必须经过受控变更包/)).toBeVisible();

  await page.getByRole('tab', { name: '能力' }).click();
  await expect(page.getByText('默认助手', { exact: true })).toBeVisible();
  await expect(page.getByText(/可用工作流/)).toBeVisible();
});

test('Workflow Builder exposes only DIRECT, LLM, and REACT while Advanced reveals routing and JSON', async ({ page }) => {
  await authenticateAdmin(page);
  await mockProjectApis(page);

  await page.goto('/automations/workflows/config?projectId=demo-project');
  const basic = page.getByRole('button', { name: '基础', exact: true });
  const advanced = page.getByRole('button', { name: '高级', exact: true });
  await expect(basic).toBeVisible();
  await expect(advanced).toBeVisible();
  await expect(basic).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: '节点能力' })).toBeVisible();
  await expect(page.getByRole('button', { name: '测试', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '连接与路由' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '高级 JSON' })).toHaveCount(0);
  await expect(page.getByText('LLM · 单次模型调用', { exact: true })).toBeVisible();
  await expect(page.getByText(/Review 是 LLM 节点的一种职责/)).toBeVisible();
  await expect(page.getByText('REVIEW', { exact: true })).toHaveCount(0);

  await advanced.click();
  await expect(advanced).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: '连接与路由' })).toBeVisible();
  await expect(page.getByRole('button', { name: '高级 JSON' })).toBeVisible();
  await expect(page.getByText('版本', { exact: true }).first()).toBeVisible();
});
