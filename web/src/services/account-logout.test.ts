import { act } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Toast } from '@douyinfe/semi-ui';
import { AdminUserService } from './admin-user-service';
import { logoutAccount } from './account-logout';
import { clearAuthSession } from './auth-session';

vi.mock('./admin-user-service', () => ({ AdminUserService: { logout: vi.fn() } }));
beforeEach(() => {
  localStorage.clear(); localStorage.setItem('token', 'synthetic-original-session');
  localStorage.setItem('userInfo', JSON.stringify({ userId: 'stable-id', username: '原有管理员', userRole: 'admin' }));
  localStorage.setItem('isLoggedIn', 'true');
  vi.mocked(AdminUserService.logout).mockReset().mockResolvedValue(undefined);
  vi.spyOn(Toast, 'error').mockImplementation(() => 'synthetic-toast');
});
afterEach(() => { localStorage.clear(); vi.restoreAllMocks(); });

it('clears a session only through the UI callback after the server acknowledged logout', async () => {
  const callback = vi.fn(clearAuthSession);
  await expect(logoutAccount(callback)).resolves.toBe(true);
  expect(AdminUserService.logout).toHaveBeenCalledTimes(1); expect(callback).toHaveBeenCalledTimes(1);
  expect(localStorage.getItem('token')).toBeNull(); expect(Toast.error).not.toHaveBeenCalled();
});
it('retains the current session and offers retry when revocation fails', async () => {
  vi.mocked(AdminUserService.logout).mockRejectedValueOnce(new Error('注销未完成，请稍后重试')).mockResolvedValueOnce(undefined);
  const callback = vi.fn(clearAuthSession);
  await expect(logoutAccount(callback)).resolves.toBe(false);
  expect(callback).not.toHaveBeenCalled(); expect(localStorage.getItem('token')).toBe('synthetic-original-session');
  expect(Toast.error).toHaveBeenCalledWith('注销未完成，请稍后重试');
  await expect(logoutAccount(callback)).resolves.toBe(true); expect(callback).toHaveBeenCalledTimes(1);
});
it('shares one server revocation if Header and Sidebar are clicked concurrently', async () => {
  let acknowledge!: () => void;
  vi.mocked(AdminUserService.logout).mockImplementation(() => new Promise<void>((resolve) => { acknowledge = resolve; }));
  const callback = vi.fn(clearAuthSession); const first = logoutAccount(callback); const second = logoutAccount(callback);
  expect(AdminUserService.logout).toHaveBeenCalledTimes(1);
  await act(async () => { acknowledge(); });
  await expect(first).resolves.toBe(true); await expect(second).resolves.toBe(false);
  expect(callback).toHaveBeenCalledTimes(1);
});
it('does not let an old logout acknowledgement clear a newly signed-in account', async () => {
  let acknowledge!: () => void;
  vi.mocked(AdminUserService.logout).mockImplementation(() => new Promise<void>((resolve) => { acknowledge = resolve; }));
  const callback = vi.fn(clearAuthSession); const request = logoutAccount(callback);
  localStorage.setItem('token', 'synthetic-new-session');
  await act(async () => { acknowledge(); });
  await expect(request).resolves.toBe(false); expect(callback).not.toHaveBeenCalled();
  expect(localStorage.getItem('token')).toBe('synthetic-new-session');
});
it('does not submit an anonymous revocation or invent a successful logout', async () => {
  localStorage.removeItem('token'); const callback = vi.fn(clearAuthSession);
  await expect(logoutAccount(callback)).resolves.toBe(false);
  expect(AdminUserService.logout).not.toHaveBeenCalled(); expect(callback).not.toHaveBeenCalled();
});
