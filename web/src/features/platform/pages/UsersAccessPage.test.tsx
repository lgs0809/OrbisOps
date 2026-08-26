import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Toast } from '@douyinfe/semi-ui';
import { PlatformUserService } from '../../../services/platform-user-service';
import { UsersAccessPage } from './UsersAccessPage';
import { accountCredentialService } from '../../../services/account-credential-service';

vi.mock('../../../services/platform-user-service', () => ({ PlatformUserService: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }));
vi.mock('../../../services/account-credential-service', () => ({ accountCredentialService: { reset: vi.fn() } }));
vi.mock('../../../components/ops-layout', async (original) => ({
  ...await original<typeof import('../../../components/ops-layout')>(), OpsPageShell: ({ children }: any) => <>{children}</>,
}));
const originalUsers = [
  { id: 12, userId: 'stable-legacy', username: '原有管理员', phone: null, userRole: 'admin', status: 1 },
  { id: 13, userId: 'stable-locked', username: '锁定成员', phone: '13800009002', userRole: 'user', status: 2 },
  { id: 14, userId: 'stable-disabled', username: '停用成员', phone: '13800009003', userRole: 'user', status: 0 },
];
const rangeRect = Object.getOwnPropertyDescriptor(Range.prototype, 'getBoundingClientRect');
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', { configurable: true, value: () => new DOMRect() });
  vi.mocked(PlatformUserService.list).mockReset().mockResolvedValue(originalUsers);
  vi.mocked(PlatformUserService.create).mockReset().mockResolvedValue(undefined);
  vi.mocked(PlatformUserService.update).mockReset().mockResolvedValue(undefined);
  vi.mocked(accountCredentialService.reset).mockReset().mockResolvedValue(true);
});
afterEach(() => {
  cleanup(); Toast.destroyAll(); vi.unstubAllGlobals();
  if (rangeRect) Object.defineProperty(Range.prototype, 'getBoundingClientRect', rangeRect);
  else delete (Range.prototype as any).getBoundingClientRect;
});
const open = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } })}>
  <UsersAccessPage />
</QueryClientProvider>);
const addForm = async () => {
  await screen.findByText('原有管理员'); fireEvent.click(screen.getByRole('button', { name: '管理员添加成员' }));
  return screen.getByRole('dialog');
};
const fillCreate = (dialog: HTMLElement, phone = '13800009001') => {
  fireEvent.change(within(dialog).getByLabelText('姓名'), { target: { value: '王晨' } });
  fireEvent.change(within(dialog).getByLabelText('手机号'), { target: { value: phone } });
  fireEvent.change(within(dialog).getByLabelText('初始密码'), { target: { value: 'Example1' } });
};
const waitClosed = async (dialog: HTMLElement) => {
  // JSDOM does not execute CSS animations. Deliver the same end event a browser emits.
  await waitFor(() => expect(dialog).toHaveClass('semi-modal-content-animate-hide'));
  fireEvent.animationEnd(dialog);
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
};
it('confirms restoring the original password and calls the reset operation without choosing a new password', async () => {
  open(); await screen.findByText('原有管理员');
  fireEvent.click(within(screen.getByRole('article', { name: '成员 锁定成员' })).getByRole('button', { name: '重置初始密码' }));
  const dialog = screen.getByRole('dialog');
  expect(within(dialog).getByText(/成员下次登录必须设置自己的密码/)).toBeInTheDocument();
  expect(within(dialog).queryByLabelText('新密码')).not.toBeInTheDocument();
  fireEvent.click(within(dialog).getByRole('button', { name: '重置初始密码' }));
  await waitFor(() => expect(vi.mocked(accountCredentialService.reset).mock.calls[0]?.[0]).toBe('stable-locked'));
  await waitFor(() => expect(PlatformUserService.list).toHaveBeenCalledTimes(2));
  await waitClosed(dialog);
});
it('renders legacy unbound and locked/disabled accounts accurately without exposing internal identity', async () => {
  open(); await screen.findByText('原有管理员');
  expect(within(screen.getByRole('article', { name: '成员 原有管理员' })).getByText('未绑定手机号')).toBeInTheDocument();
  const locked = within(screen.getByRole('article', { name: '成员 锁定成员' }));
  expect(locked.getByText('已锁定')).toBeInTheDocument(); expect(locked.queryByRole('button', { name: '启用' })).not.toBeInTheDocument();
  expect(locked.queryByRole('button', { name: '停用' })).not.toBeInTheDocument();
  expect(within(screen.getByRole('article', { name: '成员 停用成员' })).getByText('已停用')).toBeInTheDocument();
  expect(screen.queryByText('stable-legacy')).not.toBeInTheDocument();
});
it('creates a normal member by default with normalized phone and backend-generated identity', async () => {
  open(); const dialog = await addForm(); fillCreate(dialog, '+86 13800009001');
  fireEvent.click(within(dialog).getByRole('button', { name: '添加成员' }));
  await waitFor(() => expect(PlatformUserService.create).toHaveBeenCalledWith({ username: '王晨', phone: '13800009001', password: 'Example1', userRole: 'user', status: 1 }));
  await waitClosed(dialog);
  expect(PlatformUserService.update).not.toHaveBeenCalled();
});
it('validates phone before submitting an account mutation', async () => {
  open(); const dialog = await addForm(); fillCreate(dialog, '138-0000-9001');
  fireEvent.click(within(dialog).getByRole('button', { name: '添加成员' }));
  expect(within(dialog).getByRole('alert')).toHaveTextContent('请输入有效的中国大陆手机号。');
  expect(PlatformUserService.create).not.toHaveBeenCalled();
});
it('validates the initial password before submitting an account mutation', async () => {
  open(); const dialog = await addForm(); fillCreate(dialog);
  fireEvent.change(within(dialog).getByLabelText('初始密码'), { target: { value: 'abcdefgh' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '添加成员' }));
  expect(within(dialog).getByRole('alert')).toHaveTextContent('初始密码必须同时包含字母和数字。');
  expect(PlatformUserService.create).not.toHaveBeenCalled();
});
it('shows duplicate-phone rejection inside the retained form rather than claiming success', async () => {
  vi.mocked(PlatformUserService.create).mockRejectedValue(new Error('手机号已被使用'));
  open(); const dialog = await addForm(); fillCreate(dialog); fireEvent.click(within(dialog).getByRole('button', { name: '添加成员' }));
  await waitFor(() => expect(within(dialog).getByRole('alert')).toHaveTextContent('手机号已被使用'));
  expect(within(dialog).getByLabelText('姓名')).toHaveValue('王晨');
  expect(within(dialog).getByLabelText('手机号')).toHaveValue('13800009001');
  expect(screen.getByRole('dialog')).toBeInTheDocument();
});
it('binds a legacy account without changing its name/id, password or project relationship', async () => {
  open(); await screen.findByText('原有管理员');
  fireEvent.click(within(screen.getByRole('article', { name: '成员 原有管理员' })).getByRole('button', { name: '绑定手机号' }));
  const dialog = screen.getByRole('dialog'); expect(within(dialog).getByLabelText('姓名')).toBeDisabled();
  expect(within(dialog).queryByLabelText('初始密码')).not.toBeInTheDocument();
  fireEvent.change(within(dialog).getByLabelText('手机号'), { target: { value: '13800009001' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '保存成员信息' }));
  await waitFor(() => expect(PlatformUserService.update).toHaveBeenCalledWith({ id: 12, username: '原有管理员', phone: '13800009001', userRole: 'admin', status: 1 }));
  expect(PlatformUserService.create).not.toHaveBeenCalled();
});
it('updates enabled state without erasing the bound phone or password', async () => {
  open(); await screen.findByText('停用成员');
  fireEvent.click(within(screen.getByRole('article', { name: '成员 停用成员' })).getByRole('button', { name: '启用' }));
  await waitFor(() => expect(PlatformUserService.update).toHaveBeenCalledWith({ id: 14, username: '停用成员', userRole: 'user', status: 1 }));
});
it('does not let a late successful acknowledgement close a new editor after the submitted dialog was closed', async () => {
  let acknowledge!: () => void;
  vi.mocked(PlatformUserService.create).mockImplementationOnce(() => new Promise<void>((resolve) => { acknowledge = resolve; }));
  open(); const dialog = await addForm(); fillCreate(dialog); fireEvent.click(within(dialog).getByRole('button', { name: '添加成员' }));
  await waitFor(() => expect(PlatformUserService.create).toHaveBeenCalledTimes(1));
  fireEvent.click(within(dialog).getByRole('button', { name: '关闭窗口' }));
  await waitClosed(dialog);
  fireEvent.click(screen.getByRole('button', { name: '管理员添加成员' }));
  const next = screen.getByRole('dialog'); fireEvent.change(within(next).getByLabelText('姓名'), { target: { value: '下一位成员' } });
  await act(async () => { acknowledge(); });
  await waitFor(() => expect(PlatformUserService.list).toHaveBeenCalledTimes(2));
  expect(screen.getByRole('dialog')).toBeInTheDocument(); expect(within(next).getByLabelText('姓名')).toHaveValue('下一位成员');
  expect(within(next).queryByRole('alert')).not.toBeInTheDocument();
});
it('does not show a late failure in a newly opened member form', async () => {
  let reject!: (error: Error) => void;
  vi.mocked(PlatformUserService.create).mockImplementationOnce(() => new Promise<void>((_resolve, fail) => { reject = fail; }));
  open(); const dialog = await addForm(); fillCreate(dialog); fireEvent.click(within(dialog).getByRole('button', { name: '添加成员' }));
  await waitFor(() => expect(PlatformUserService.create).toHaveBeenCalledTimes(1));
  fireEvent.click(within(dialog).getByRole('button', { name: '关闭窗口' }));
  await waitClosed(dialog);
  fireEvent.click(screen.getByRole('button', { name: '管理员添加成员' }));
  await act(async () => { reject(new Error('手机号已被使用')); });
  expect(within(screen.getByRole('dialog')).queryByRole('alert')).not.toBeInTheDocument();
});
it('keeps safe load errors visible and permits explicit refresh', async () => {
  vi.mocked(PlatformUserService.list).mockRejectedValueOnce(new Error('暂时无法读取团队成员')).mockResolvedValueOnce(originalUsers);
  open(); expect(await screen.findByRole('alert')).toHaveTextContent('暂时无法读取团队成员');
  fireEvent.click(screen.getByRole('button', { name: /刷新/ })); await screen.findByText('原有管理员');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
it('supports searching by display name or phone without using internal IDs', async () => {
  open(); await screen.findByText('原有管理员'); fireEvent.change(screen.getByLabelText('搜索成员'), { target: { value: '13800009002' } });
  expect(screen.getByText('锁定成员')).toBeInTheDocument(); expect(screen.queryByText('原有管理员')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('搜索成员'), { target: { value: '' } }); expect(screen.getByText('原有管理员')).toBeInTheDocument();
});
