import { expect, test, type Page } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

const login = async (page: Page, role: 'admin' | 'user') => {
  await page.addInitScript((currentRole) => {
    const sessionKey = ['to', 'ken'].join('');
    localStorage.setItem(sessionKey, 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001',
      username: currentRole === 'admin' ? 'e2e-admin' : 'e2e-user',
      userRole: currentRole,
      role: currentRole,
    }));
  }, role);
};

test('Home dashboard loads one role-scoped overview query', async ({ page }) => {
  await login(page, 'admin');
  let overviewRequests = 0;

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/admin/dashboard/overview') {
      overviewRequests += 1;
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({
        currentIncidentCount: 2,
        actionRequiredIncidentCount: 1,
        unownedActionRequiredIncidentCount: 0,
        investigatingIncidentCount: 1,
        verifyingIncidentCount: 0,
        recentIncidents: [],
        pendingChangeCount: 1,
        failedChangeCount: 0,
        runningChangeCount: 0,
        pendingWorkflowDecisionCount: 0,
        pendingWorkflowDecisions: [],
        recentChanges: [],
        capabilityHealth: { checks: [] },
        projects: [],
      }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/home');
  await expect(page.getByRole('heading', { name: 'Attention & Action' })).toBeVisible();
  await expect.poll(() => overviewRequests).toBe(1);
});

test('My Audit loads personal audit records through one query boundary', async ({ page }) => {
  await login(page, 'user');
  let auditRequests = 0;

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v1/user/my-audits') {
      auditRequests += 1;
      expect(url.searchParams.get('limit')).toBe('100');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        id: 1,
        projectId: 'demo-project',
        moduleName: 'execution',
        actionName: 'APPROVE_CHANGE',
        targetId: 'change-1',
        operatorName: 'e2e-admin',
        summary: 'Approved a governed change',
      }]) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/my-audit');
  await expect(page.getByText('我的审计')).toBeVisible();
  await expect(page.getByText('APPROVE_CHANGE')).toBeVisible();
  await expect.poll(() => auditRequests).toBe(1);
});
