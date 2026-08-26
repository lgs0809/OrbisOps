import { fulfillAccessibleProjects } from './fixtures/project-catalog';
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

test('Governance loads audit details and policy only when their UI is opened', async ({ page }) => {
  await authenticateAdmin(page);

  let auditListRequests = 0;
  let detailRequests = 0;
  let policyRequests = 0;
  let policySaveRequests = 0;
  let savedPolicyPayload: Record<string, unknown> | null = null;
  let storedRetentionDays = 180;

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();
    let data: unknown = [];

    if (await fulfillAccessibleProjects(route, [{ projectId: 'project-1', name: 'Core Platform' }])) return;
    if (path.endsWith('/api/v1/admin/ops-projects/snapshot')) {
      data = {
        projects: [{ projectId: 'project-1', name: 'Core Platform' }],
        templates: [],
      };
    } else if (path.endsWith('/api/v1/admin/ops/config-audits/policy')) {
      if (method === 'PUT') {
        policySaveRequests += 1;
        savedPolicyPayload = route.request().postDataJSON() as Record<string, unknown>;
        storedRetentionDays = Number(savedPolicyPayload?.retentionDays || storedRetentionDays);
        data = {
          ...(savedPolicyPayload || {}),
          projectId: 'project-1',
          retentionDays: storedRetentionDays,
          maskingEnabled: true,
          persistence: true,
        };
      } else {
        policyRequests += 1;
        data = {
          projectId: 'project-1',
          retentionDays: storedRetentionDays,
          maskingEnabled: true,
          exportApprovalRequired: true,
          highRiskConfirmationRequired: true,
          replayEnabled: true,
          status: 'ENABLED',
        };
      }
    } else if (path.endsWith('/api/v1/admin/ops/config-audits/audit-1')) {
      detailRequests += 1;
      data = {
        id: 1,
        audit_id: 'audit-1',
        project_id: 'project-1',
        module_name: 'change-package',
        action_name: 'APPROVE',
        target_id: 'pkg-1',
        risk_level: 'HIGH',
        result_status: 'SUCCESS',
        operator_name: 'operator-a',
        client_ip: '127.0.0.1',
        create_time: '2026-08-17 12:00:00',
        before_json: '{"status":"PENDING"}',
        after_json: '{"status":"APPROVED"}',
      };
    } else if (path.endsWith('/api/v1/admin/ops/config-audits')) {
      auditListRequests += 1;
      data = [{
        id: 1,
        audit_id: 'audit-1',
        project_id: 'project-1',
        module_name: 'change-package',
        action_name: 'APPROVE',
        target_id: 'pkg-1',
        risk_level: 'HIGH',
        result_status: 'SUCCESS',
        operator_name: 'operator-a',
        client_ip: '127.0.0.1',
        create_time: '2026-08-17 12:00:00',
      }];
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0000', info: 'success', data }),
    });
  });

  await page.goto('/platform/governance');
  await expect(page.getByRole('heading', { name: '治理与审计' })).toBeVisible();
  await expect(page.getByText('pkg-1', { exact: true })).toBeVisible();
  await expect.poll(() => auditListRequests).toBe(1);
  expect(detailRequests).toBe(0);
  expect(policyRequests).toBe(0);

  const auditRow = page.locator('tr').filter({ hasText: 'pkg-1' }).first();
  await auditRow.getByRole('button', { name: '查看' }).click();
  await expect.poll(() => detailRequests).toBe(1);
  await expect(page.getByText('ChangePackage', { exact: true }).first()).toBeVisible();
  await page.getByRole('dialog').getByRole('button', { name: /关闭|close/i }).click().catch(async () => {
    await page.keyboard.press('Escape');
  });

  await page.getByRole('button', { name: '审计策略' }).click();
  await expect.poll(() => policyRequests).toBe(1);
  await expect(page.getByText('审计可见性保留期', { exact: true })).toBeVisible();
  await expect(page.getByText('敏感字段脱敏', { exact: true })).toBeVisible();
  await expect(page.getByText('始终启用 · 请求无法关闭脱敏', { exact: true })).toBeVisible();
  await expect(page.getByText('审计重放', { exact: true })).toBeVisible();
  await expect(page.getByText('UNAVAILABLE', { exact: true })).toBeVisible();

  const retentionInput = page.getByRole('textbox', { name: '审计保留天数' });
  const saveButton = page.getByRole('button', { name: '保存保留策略' });
  await expect(retentionInput).toHaveValue('180');
  await expect(saveButton).toBeDisabled();
  await retentionInput.fill('90');
  await expect(saveButton).toBeEnabled();
  await saveButton.click();

  await expect.poll(() => policySaveRequests).toBe(1);
  expect(savedPolicyPayload?.retentionDays).toBe(90);
  expect(savedPolicyPayload?.maskingEnabled).toBe(true);
  await expect(retentionInput).toHaveValue('90');
  await expect(saveButton).toBeDisabled();
});
