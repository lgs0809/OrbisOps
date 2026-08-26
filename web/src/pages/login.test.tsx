import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Toast } from '@douyinfe/semi-ui';

const mocks = vi.hoisted(() => ({ status: vi.fn(), setup: vi.fn(), login: vi.fn() }));
vi.mock('../services', () => ({ AdminUserService: { loginAdminUser: mocks.login } }));
vi.mock('../services/first-time-setup-service', () => ({ FirstTimeSetupService: { isRequired: mocks.status, createFirstAdministrator: mocks.setup } }));
import LoginPage from './login';

beforeEach(() => {
  localStorage.clear(); mocks.status.mockReset().mockResolvedValue(false); mocks.setup.mockReset(); mocks.login.mockReset();
});
afterEach(() => { cleanup(); Toast.destroyAll(); });
const open = () => render(<MemoryRouter initialEntries={['/login']}><Routes>
  <Route path="/login" element={<LoginPage />} /><Route path="/chat" element={<p>团队对话工作台</p>} />
  <Route path="/home" element={<p>管理员首页</p>} />
  <Route path="/account/password" element={<p>设置自己的登录密码</p>} />
</Routes></MemoryRouter>);
const enterPhone = async (value = '13800009001') => {
  await waitFor(() => expect(screen.getByRole('button', { name: '登录' })).toBeEnabled());
  fireEvent.change(screen.getByLabelText('手机号'), { target: { value } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'Example1' } });
};
it('defaults to phone login with sensible labels and autofill and no public signup', async () => {
  mocks.login.mockResolvedValue({ userId: 'stable-id', username: '王晨', phone: '13800009001', userRole: 'user', token: 'synthetic-session' });
  open(); await enterPhone(' +8613800009001 ');
  expect(screen.getByLabelText('手机号')).toHaveAttribute('type', 'tel');
  expect(screen.getByLabelText('手机号')).toHaveAttribute('autocomplete', 'username');
  expect(screen.getByLabelText('密码')).toHaveAttribute('autocomplete', 'current-password');
  expect(screen.queryByRole('button', { name: /注册/ })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '登录' }));
  await screen.findByText('团队对话工作台');
  expect(mocks.login).toHaveBeenCalledWith({ phone: '13800009001', password: 'Example1' });
  expect(JSON.parse(localStorage.getItem('userInfo')!)).toMatchObject({ userId: 'stable-id', username: '王晨', phone: '13800009001' });
});
it('rejects malformed phone before requesting a session', async () => {
  open(); await enterPhone('138-0000-9001'); fireEvent.click(screen.getByRole('button', { name: '登录' }));
  await screen.findByText('请输入有效的中国大陆手机号。'); expect(mocks.login).not.toHaveBeenCalled();
});
it('requires first-login password setup before opening the workspace', async () => {
  mocks.login.mockResolvedValue({ userId: 'stable-id', username: '王晨', userRole: 'user', token: 'initial-session', passwordChangeRequired: true });
  open(); await enterPhone(); fireEvent.click(screen.getByRole('button', { name: '登录' }));
  await screen.findByText('设置自己的登录密码');
  expect(screen.queryByText('团队对话工作台')).not.toBeInTheDocument();
  expect(JSON.parse(localStorage.getItem('userInfo')!).passwordChangeRequired).toBe(true);
});
it.each(['用户已被锁定', '用户已被禁用', '手机号或密码错误'])('shows the safe backend reason without storing a session: %s', async (message) => {
  mocks.login.mockRejectedValue(new Error(message)); open(); await enterPhone();
  fireEvent.click(screen.getByRole('button', { name: '登录' }));
  await screen.findByRole('alert'); expect(screen.getByRole('alert')).toHaveTextContent(message);
  expect(localStorage.getItem('token')).toBeNull();
});
it('keeps submission disabled on setup-status failure until an explicit successful retry', async () => {
  mocks.status.mockRejectedValueOnce(new Error('平台暂时不可用')).mockResolvedValueOnce(false); open();
  await screen.findByText('平台暂时不可用'); expect(screen.getByRole('button', { name: '登录' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '重试读取平台状态' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '登录' })).toBeEnabled());
  expect(mocks.login).not.toHaveBeenCalled();
});
it('keeps legacy accounts reachable only through the explicit migration entry', async () => {
  mocks.login.mockResolvedValue({ userId: 'stable-old', username: 'old-owner', userRole: 'admin', token: 'synthetic-session' });
  open(); await waitFor(() => expect(screen.getByRole('button', { name: '登录' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '使用原账号登录' }));
  fireEvent.change(screen.getByLabelText('原账号'), { target: { value: 'old-owner' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'Original1' } });
  fireEvent.click(screen.getByRole('button', { name: '登录' })); await screen.findByText('团队对话工作台');
  expect(mocks.login).toHaveBeenCalledWith({ username: 'old-owner', password: 'Original1' });
});
it('creates the first administrator with phone and a two-character name through bootstrap', async () => {
  mocks.status.mockResolvedValue(true); mocks.setup.mockResolvedValue({ userId: 'first', username: '王晨', phone: '13800009001', userRole: 'admin', token: 'synthetic-session' });
  open(); await screen.findByRole('heading', { name: '初始化 OrbisOps' });
  await waitFor(() => expect(screen.getByRole('button', { name: '创建管理员并进入系统' })).toBeEnabled());
  fireEvent.change(screen.getByLabelText('手机号'), { target: { value: '13800009001' } });
  fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '王晨' } });
  fireEvent.change(screen.getByLabelText('管理员密码'), { target: { value: 'Example1' } });
  fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'Example1' } });
  fireEvent.click(screen.getByRole('button', { name: '创建管理员并进入系统' })); await screen.findByText('管理员首页');
  expect(mocks.setup).toHaveBeenCalledWith({ username: '王晨', phone: '13800009001', password: 'Example1' }); expect(mocks.login).not.toHaveBeenCalled();
});
it('does not issue duplicate login calls while a request is in flight', async () => {
  let acknowledge!: (value: unknown) => void;
  mocks.login.mockImplementation(() => new Promise((resolve) => { acknowledge = resolve; }));
  open(); await enterPhone();
  const button = screen.getByRole('button', { name: '登录' });
  fireEvent.click(button); await waitFor(() => expect(mocks.login).toHaveBeenCalledTimes(1));
  expect(button).toBeDisabled(); fireEvent.click(button); expect(mocks.login).toHaveBeenCalledTimes(1);
  await act(async () => { acknowledge(null); });
});
it('does not persist a late login response after the login page is unmounted', async () => {
  let acknowledge!: (value: unknown) => void;
  mocks.login.mockImplementation(() => new Promise((resolve) => { acknowledge = resolve; }));
  const view = open(); await enterPhone(); fireEvent.click(screen.getByRole('button', { name: '登录' }));
  await waitFor(() => expect(mocks.login).toHaveBeenCalledTimes(1)); view.unmount();
  await act(async () => { acknowledge({ userId: 'stable-id', username: '王晨', userRole: 'user', token: 'synthetic-session' }); });
  expect(localStorage.getItem('token')).toBeNull();
});
