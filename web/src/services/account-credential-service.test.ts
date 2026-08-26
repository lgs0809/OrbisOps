import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { accountCredentialService } from './account-credential-service';
beforeEach(() => { localStorage.clear(); localStorage.setItem('token', 'personal-session'); });
afterEach(() => vi.unstubAllGlobals());
it('uses personal authorization and accepts the replacement session only after application success', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: '0000', data: { token: 'replacement', passwordChangeRequired: false } })));
  vi.stubGlobal('fetch', fetcher);
  expect(await accountCredentialService.change('initial8', 'Personal9')).toMatchObject({ token: 'replacement', passwordChangeRequired: false });
  expect(fetcher).toHaveBeenCalledWith('/api/v1/user/account/password', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ Authorization: 'Bearer personal-session' }), body: JSON.stringify({ currentPassword: 'initial8', newPassword: 'Personal9' }) }));
});
it.each([{ code: 'A0001', info: '当前密码错误' }, { code: '0000', data: false }])('does not treat a rejected or unacknowledged reset as success: %j', async (body) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))));
  await expect(accountCredentialService.reset('account/a')).rejects.toThrow();
});
it('requires a positive reset acknowledgment and URL-encodes the immutable identity', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: '0000', data: true })));
  vi.stubGlobal('fetch', fetcher); expect(await accountCredentialService.reset('account/a')).toBe(true);
  expect(fetcher.mock.calls[0][0]).toContain('/account%2Fa/reset-password');
});
it('preserves a safe permission denial from the real HTTP error response', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: '403', info: '权限不足' }), { status: 403 })));
  await expect(accountCredentialService.profile()).rejects.toThrow('权限不足');
});
