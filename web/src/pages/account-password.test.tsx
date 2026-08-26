import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Toast } from '@douyinfe/semi-ui';
const mocks = vi.hoisted(() => ({ profile: vi.fn(), change: vi.fn(), logout: vi.fn() }));
vi.mock('../services/account-credential-service', () => ({ accountCredentialService: mocks }));
vi.mock('../services/account-logout', () => ({ logoutAccount: mocks.logout }));
import { AccountPasswordPage } from './account-password';
const initial = { userId: 'account-a', username: '成员', token: 'initial-token', userRole: 'user', passwordChangeRequired: true };
beforeEach(() => {
  localStorage.clear(); localStorage.setItem('token', initial.token);
  mocks.profile.mockReset().mockResolvedValue(initial); mocks.change.mockReset(); mocks.logout.mockReset();
});
afterEach(() => { cleanup(); Toast.destroyAll(); });
const open = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } })}>
  <MemoryRouter initialEntries={['/account/password']}><Routes>
    <Route path="/account/password" element={<AccountPasswordPage />} /><Route path="/chat" element={<p>工作空间</p>} />
  </Routes></MemoryRouter>
</QueryClientProvider>);
const fill = async (newPassword = 'MyPassword9', confirmPassword = newPassword) => {
  await screen.findByRole('heading', { name: '设置自己的登录密码' });
  fireEvent.change(screen.getByLabelText('初始密码'), { target: { value: 'Initial8' } });
  fireEvent.change(screen.getByLabelText('新密码'), { target: { value: newPassword } });
  fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: confirmPassword } });
};
const submit = () => fireEvent.click(screen.getByRole('button', { name: '保存并进入工作空间' }));
it('shows mandatory setup and autofill without an entry to skip it', async () => {
  open(); await fill(); expect(screen.queryByRole('button', { name: '返回工作空间' })).not.toBeInTheDocument();
  expect(screen.getByLabelText('初始密码')).toHaveAttribute('autocomplete', 'current-password');
  expect(screen.getByLabelText('新密码')).toHaveAttribute('autocomplete', 'new-password');
});
it.each([['abcdefgh', 'abcdefgh', '新密码必须同时包含字母和数字。'], ['MyPassword9', 'different9', '两次输入的新密码不一致。']])('keeps invalid input local: %s', async (password, confirm, reason) => {
  open(); await fill(password, confirm); submit(); expect(await screen.findByRole('alert')).toHaveTextContent(reason);
  expect(mocks.change).not.toHaveBeenCalled();
});
it('stores the refreshed token and enters the workspace only after successful change', async () => {
  mocks.change.mockResolvedValue({ ...initial, token: 'new-token', passwordChangeRequired: false });
  open(); await fill(); submit(); await screen.findByText('工作空间');
  expect(mocks.change).toHaveBeenCalledWith('Initial8', 'MyPassword9');
  expect(localStorage.getItem('token')).toBe('new-token');
  expect(JSON.parse(localStorage.getItem('userInfo')!).passwordChangeRequired).toBe(false);
});
it('retains input and the initial session when the current password is rejected', async () => {
  mocks.change.mockRejectedValue(new Error('当前密码错误')); open(); await fill(); submit();
  expect(await screen.findByRole('alert')).toHaveTextContent('当前密码错误');
  expect(screen.getByLabelText('新密码')).toHaveValue('MyPassword9'); expect(localStorage.getItem('token')).toBe(initial.token);
});
it.each(['unmount', 'new-session'])('does not restore a late password-change session after %s', async (mode) => {
  let acknowledge!: (value: unknown) => void;
  mocks.change.mockImplementation(() => new Promise(resolve => { acknowledge = resolve; }));
  const view = open(); await fill(); submit(); await waitFor(() => expect(mocks.change).toHaveBeenCalledTimes(1));
  expect(screen.getByRole('button', { name: '保存并进入工作空间' })).toBeDisabled();
  if (mode === 'unmount') { view.unmount(); localStorage.removeItem('token'); }
  else localStorage.setItem('token', 'another-user-session');
  await act(async () => { acknowledge({ ...initial, token: 'late-token', passwordChangeRequired: false }); });
  expect(localStorage.getItem('token')).toBe(mode === 'unmount' ? null : 'another-user-session');
});
it('blocks submission until an explicitly retried account load succeeds', async () => {
  mocks.profile.mockRejectedValueOnce(new Error('unavailable')).mockResolvedValueOnce(initial);
  open(); await screen.findByRole('alert'); expect(screen.getByRole('button', { name: '保存并进入工作空间' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '重新读取' })); await fill(); expect(mocks.change).not.toHaveBeenCalled();
});
