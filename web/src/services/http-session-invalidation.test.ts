import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const original = window.fetch;
beforeEach(() => { vi.resetModules(); localStorage.clear(); });
afterEach(() => { window.fetch = original; });
it('keeps a newly authenticated session when an earlier request returns unauthorized late', async () => {
  let acknowledge!: (response: Response) => void;
  window.fetch = vi.fn(() => new Promise<Response>(resolve => { acknowledge = resolve; }));
  const { installHttpInterceptor } = await import('./http-interceptor'); installHttpInterceptor();
  localStorage.setItem('token', 'before-password-change');
  const request = window.fetch('/api/v1/user/account', { headers: { Authorization: 'Bearer before-password-change' } });
  localStorage.setItem('token', 'after-password-change'); localStorage.setItem('userInfo', JSON.stringify({ userRole: 'user' }));
  acknowledge(new Response('{}', { status: 401 })); await request;
  expect(localStorage.getItem('token')).toBe('after-password-change'); expect(localStorage.getItem('userInfo')).not.toBeNull();
});
it('clears the rejected current session and keeps the original unauthorized response available', async () => {
  window.fetch = vi.fn().mockResolvedValue(new Response('{}', { status: 401 }));
  const { installHttpInterceptor } = await import('./http-interceptor'); installHttpInterceptor();
  localStorage.setItem('token', 'rejected'); localStorage.setItem('userInfo', '{}');
  expect((await window.fetch('/api/v1/user/account', { headers: { Authorization: 'Bearer rejected' } })).status).toBe(401);
  expect(localStorage.getItem('token')).toBeNull(); expect(localStorage.getItem('userInfo')).toBeNull();
});
