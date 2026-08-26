import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { PlatformUserService } from './platform-user-service';
import { AdminUserService } from './admin-user-service';
import { FirstTimeSetupService } from './first-time-setup-service';

const response = (data: unknown, status = 200, code = '0000', info = '成功') => new Response(JSON.stringify({ code, info, data }), {
  status, headers: { 'Content-Type': 'application/json' },
});
const fetchMock = vi.fn();
beforeEach(() => { localStorage.clear(); fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => vi.unstubAllGlobals());

it('sends only phone and password for normal login and preserves the display name in response', async () => {
  fetchMock.mockResolvedValue(response({ userId: 'stable-id', username: '王晨', phone: '13800009001', userRole: 'user', token: 'synthetic-session' }));
  const result = await AdminUserService.loginAdminUser({ phone: '13800009001', password: 'Example1' });
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ phone: '13800009001', password: 'Example1' });
  expect(result).toMatchObject({ userId: 'stable-id', username: '王晨', phone: '13800009001' });
});
it.each([[200, '用户已被锁定'], [200, '用户已被禁用'], [401, '手机号或密码错误'], [400, '请输入有效的中国大陆手机号']])('keeps a safe login rejection at HTTP %s', async (status, info) => {
  fetchMock.mockResolvedValue(response(null, status, '0002', info));
  await expect(AdminUserService.loginAdminUser({ phone: '13800009001', password: 'Example1' })).rejects.toThrow(info);
});
it('does not turn a broken transport or invalid response into wrong-password feedback', async () => {
  fetchMock.mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(new Response('<html>not json</html>', { status: 502 }));
  await expect(AdminUserService.loginAdminUser({ phone: '13800009001', password: 'Example1' })).rejects.toThrow('offline');
  await expect(AdminUserService.loginAdminUser({ phone: '13800009001', password: 'Example1' })).rejects.toThrow('登录失败，请稍后重试。');
});
it('preserves the explicit legacy login contract', async () => {
  fetchMock.mockResolvedValue(response({ username: 'old-account', token: 'synthetic-session' }));
  await AdminUserService.loginAdminUser({ username: 'old-account', password: 'Original1' });
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ username: 'old-account', password: 'Original1' });
});
it('creates an admin-managed account with backend-generated identity and dynamic authorization', async () => {
  localStorage.setItem('token', 'synthetic-admin-session'); fetchMock.mockResolvedValue(response(true));
  const payload = { username: '王晨', phone: '13800009001', password: 'Example1', userRole: 'user', status: 1 };
  await PlatformUserService.create(payload);
  expect(fetchMock.mock.calls[0][0]).toMatch(/\/admin-user\/create$/);
  expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer synthetic-admin-session');
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(payload);
});
it('binds phone through the id-based API without sending userId or a password reset', async () => {
  fetchMock.mockResolvedValue(response(true));
  const payload = { id: 12, username: 'legacy-owner', phone: '13800009001', userRole: 'user', status: 1 };
  await PlatformUserService.update(payload);
  expect(fetchMock.mock.calls[0][0]).toMatch(/\/admin-user\/update-by-id$/);
  expect(fetchMock.mock.calls[0][1].method).toBe('PUT');
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(payload);
});
it('surfaces duplicate phone errors even when the backend rejects HTTP400', async () => {
  fetchMock.mockResolvedValue(response(null, 400, '0002', '手机号已被使用'));
  await expect(PlatformUserService.create({ username: '王晨', phone: '13800009001', password: 'Example1' })).rejects.toThrow('手机号已被使用');
});
it('does not claim success when the mutation acknowledgement is false', async () => {
  fetchMock.mockResolvedValue(response(false));
  await expect(PlatformUserService.update({ id: 12, username: 'legacy-owner', status: 0 })).rejects.toThrow('成员信息未保存');
});
it('bootstrap includes phone but remains the single existing setup endpoint', async () => {
  fetchMock.mockResolvedValue(response({ username: '管理员', phone: '13800009001', token: 'synthetic-session' }));
  const payload = { username: '管理员', phone: '13800009001', password: 'Example1' };
  await FirstTimeSetupService.createFirstAdministrator(payload);
  expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/v1\/setup$/);
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(payload);
});
it('requires a real successful logout acknowledgement before resolving', async () => {
  localStorage.setItem('token', 'synthetic-session'); fetchMock.mockResolvedValue(response(true));
  await expect(AdminUserService.logout()).resolves.toBeUndefined();
  expect(fetchMock.mock.calls[0][0]).toMatch(/\/admin-user\/logout$/);
  expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer synthetic-session');
});
it.each([[200, '0001'], [200, '0000'], [401, '0001']])('rejects unsuccessful or false logout acknowledgements HTTP %s code %s', async (status, code) => {
  fetchMock.mockResolvedValue(response(false, status, code, '注销未完成，请稍后重试'));
  await expect(AdminUserService.logout()).rejects.toThrow('注销未完成，请稍后重试');
});
