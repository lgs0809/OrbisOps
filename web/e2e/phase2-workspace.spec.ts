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

  await page.goto('/projects/advanced?projectId=demo-project');
  await expect(page.getByRole('button', { name: 'Overview' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Access' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Knowledge & Skills' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Code & Runtime' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Data & Tools' })).toBeVisible();

  await expect(page.getByText('开始第一次真实诊断', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Access' }).click();
  await expect(page.getByText('项目成员与使用权限', { exact: true })).toBeVisible();
  await expect(page.getByText('暂无显式项目成员', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Code & Runtime' }).click();
  await expect(page.getByText('代码与服务', { exact: true })).toBeVisible();
  await expect(page.getByText('变更执行目标', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Data & Tools' }).click();
  await expect(page.getByText('数据连接与工具', { exact: true })).toBeVisible();
  await expect(page.getByText('已生成工具', { exact: true }).last()).toBeVisible();
});

test('Agent Builder defaults to Basic and only exposes low-frequency internals in Advanced', async ({ page }) => {
  await authenticateAdmin(page);
  await mockProjectApis(page);

  await page.goto('/automations/workflows/config?projectId=demo-project');
  const basic = page.getByRole('button', { name: /Basic/ });
  const advanced = page.getByRole('button', { name: /Advanced/ });
  await expect(basic).toBeVisible();
  await expect(advanced).toBeVisible();
  await expect(basic).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: '节点能力' })).toBeVisible();
  await expect(page.getByRole('button', { name: '测试', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '连接路由' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '高级 JSON' })).toHaveCount(0);

  await advanced.click();
  await expect(advanced).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: '连接路由' })).toBeVisible();
  await expect(page.getByRole('button', { name: '高级 JSON' })).toBeVisible();
  await expect(page.getByText('版本', { exact: true }).first()).toBeVisible();
});
