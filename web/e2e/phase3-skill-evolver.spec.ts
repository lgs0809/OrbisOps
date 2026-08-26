import { expect, test } from '@playwright/test';

const response = (data: unknown) => JSON.stringify({ code: '0000', info: 'success', data });

test('Skill Evolver caches one filtered overview and refetches after explicit scan', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'x');
    localStorage.setItem('isLoggedIn', 'true');
    localStorage.setItem('userInfo', JSON.stringify({
      userId: '10001', username: 'e2e-admin', userRole: 'admin', role: 'admin', token: 'x',
    }));
  });

  let jobRequests = 0;
  let patchRequests = 0;
  let runOnceRequests = 0;

  await page.route('http://127.0.0.1:8099/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/v1/admin/ops/skill-evolver/jobs' && route.request().method() === 'GET') {
      jobRequests += 1;
      expect(url.searchParams.get('limit')).toBe('100');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        jobId: 'job-1', projectId: 'demo-project', agentId: 'default-react',
        triggerReason: 'AGENT_RUN_COMPLETED', status: 'DONE', attempts: 1,
        sourceSummary: 'Investigated elevated 5xx responses',
      }]) });
    }
    if (path === '/api/v1/admin/ops/skill-evolver/patches' && route.request().method() === 'GET') {
      patchRequests += 1;
      expect(url.searchParams.get('limit')).toBe('100');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response([{
        patchId: 'patch-1', jobId: 'job-1', projectId: 'demo-project',
        targetSkillTitle: 'HTTP 5xx diagnosis', decision: 'PATCH_SKILL', status: 'APPLIED',
        authoringReason: 'Reusable evidence sequence detected',
      }]) });
    }
    if (path === '/api/v1/admin/ops/skill-evolver/jobs/run-once' && route.request().method() === 'POST') {
      runOnceRequests += 1;
      expect(url.searchParams.get('limit')).toBe('5');
      return route.fulfill({ status: 200, contentType: 'application/json', body: response({ processed: 1 }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: response([]) });
  });

  await page.goto('/platform/advanced/skill-evolver');
  await expect(page.getByText('运维对话完成后自动学习').first()).toBeVisible();
  await page.getByRole('tab', { name: '沉淀结果' }).click();
  await expect(page.getByText('HTTP 5xx diagnosis').first()).toBeVisible();
  expect(jobRequests).toBe(1);
  expect(patchRequests).toBe(1);

  await page.getByRole('button', { name: '刷新' }).click();
  await expect.poll(() => jobRequests).toBe(2);
  await expect.poll(() => patchRequests).toBe(2);

  await page.getByRole('button', { name: '立即分析排队任务' }).click();
  await expect.poll(() => runOnceRequests).toBe(1);
  await expect.poll(() => jobRequests).toBe(3);
  await expect.poll(() => patchRequests).toBe(3);
});
