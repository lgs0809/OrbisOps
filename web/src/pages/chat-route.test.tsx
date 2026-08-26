import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  principal: { username: 'owner', userId: 'owner', role: 'user' },
  scope: { projectId: 'a', loading: false, projects: [{ projectId: 'a', name: '项目 A' }],
    selectProject: vi.fn(), error: undefined as string | undefined },
  sessions: vi.fn(), detail: vi.fn(), create: vi.fn(), messages: vi.fn(), stream: vi.fn(), models: vi.fn(), agents: vi.fn(), participants: vi.fn(),
}));
vi.mock('../hooks/use-project-scope', () => ({ useProjectScope: () => mocks.scope }));
vi.mock('../services/auth-session', () => ({ getStoredUserInfo: () => mocks.principal,
  isAdminUser: () => false, isAuthenticated: () => true }));
vi.mock('../services/ops-admin-service', () => ({ opsAdminService: {
  listChatSessions: mocks.sessions, listChatMessages: mocks.messages,
  getChatSession: mocks.detail, createChatSession: mocks.create,
  listChatSessionParticipants: mocks.participants,
  streamUserChat: mocks.stream,
  listChatModels: mocks.models, listChatAgents: mocks.agents,
} }));
vi.mock('../components/ops-layout', async (original) => ({
  ...await original<typeof import('../components/ops-layout')>(), OpsPageShell: ({ children }: any) => <>{children}</>,
}));
vi.mock('../features/chat/TaskEpisodePanel', () => ({ TaskEpisodePanel: () => null }));
vi.mock('../features/chat/AssistantAnswer', () => ({ AssistantAnswer: ({ text }: any) => <p>{text}</p> }));
vi.mock('../features/chat/ChatModelSelector', () => ({ ChatModelSelector: () => null }));

import { ChatPage } from './chat';
import { ApiRequestError } from '../services/ops-http-client';
const Probe = () => {
  const location = useLocation(); const navigate = useNavigate();
  return <><output data-testid="route">{location.search}</output>
    <button onClick={() => navigate('/chat?projectId=b&sessionId=b1&runId=live-run')}>打开不同项目链接</button>
    <button onClick={() => navigate('/chat?projectId=a&sessionId=a-old&runId=a-oldolder')}>打开旧会话链接</button></>;
};
const tree = (url: string) => <MemoryRouter initialEntries={[url]}><Probe /><Routes>
  <Route path="/chat" element={<ChatPage />} />
  <Route path="/workbench" element={<p>运行工作台</p>} />
</Routes></MemoryRouter>;
const rangeRect = Object.getOwnPropertyDescriptor(Range.prototype, 'getBoundingClientRect');
const history = (sessionId: string) => ['older', 'newer'].map((runId) => ({
  messageId: sessionId + runId, sessionId, role: 'assistant', content: '已完成 ' + sessionId + runId,
  metadata: { runId: sessionId + runId },
}));
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', { configurable: true, value: () => new DOMRect() });
  localStorage.clear(); mocks.scope.projectId = 'a'; mocks.scope.loading = false; mocks.scope.error = undefined;
  mocks.principal = { username: 'owner', userId: 'owner', role: 'user' };
  mocks.participants.mockReset().mockResolvedValue({ code: '0000', data: [{ userId: 'owner', role: 'OWNER', status: 'ACTIVE' }] });
  mocks.sessions.mockReset().mockResolvedValue({ data: [{ sessionId: 'a1', projectId: 'a', title: '会话一' },
    { sessionId: 'a2', projectId: 'a', title: '会话二' }, { sessionId: 'b1', projectId: 'b', title: '其他项目' }] });
  mocks.messages.mockReset().mockImplementation(async (sessionId: string) => ({ data: history(sessionId) }));
  mocks.detail.mockReset().mockRejectedValue(new ApiRequestError(403, 'Forbidden', 'SESSION_READ_FORBIDDEN'));
  mocks.create.mockReset().mockResolvedValue({ code: '0000', data: 'a-created' });
  mocks.stream.mockReset();
  mocks.models.mockReset().mockResolvedValue({ data: [] });
  mocks.agents.mockReset().mockResolvedValue({ data: [] });
});

it('shows shared OBSERVER history as read only and never submits by button or keyboard', async () => {
  mocks.principal = { username: 'viewer', userId: 'viewer-id', role: 'user' };
  mocks.sessions.mockResolvedValue({ data: [{ sessionId: 'a1', projectId: 'a', userId: 'owner-id', title: '共享会话' }] });
  mocks.participants.mockResolvedValue({ code: '0000', data: [{ userId: 'viewer-id', role: 'OBSERVER', status: 'ACTIVE' }] });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  await waitFor(() => expect(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' })).toBeDisabled());
  expect(screen.getByText('只读协作')).toBeInTheDocument();
  const composer = screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' });
  fireEvent.change(composer, { target: { value: '不能发送的内容' } });
  fireEvent.keyDown(composer, { key: 'Enter', code: 'Enter' });
  const send = screen.getByRole('button', { name: 'send 发送' });
  expect(send).toBeDisabled();
  fireEvent.click(send);
  expect(mocks.stream).not.toHaveBeenCalled();
  expect(mocks.create).not.toHaveBeenCalled();
});

it('keeps an OWNER writable without waiting for shared permission lookup', async () => {
  mocks.sessions.mockResolvedValue({ data: [{ sessionId: 'a1', projectId: 'a', userId: 'owner', title: '自己的会话' }] });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  const composer = screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' });
  expect(composer).toBeEnabled();
  fireEvent.change(composer, { target: { value: '继续自己的任务' } });
  expect(screen.getByRole('button', { name: 'send 发送' })).toBeEnabled();
  expect(mocks.participants).not.toHaveBeenCalled();
});

it('allows a verified active EDITOR to submit through the existing stream', async () => {
  mocks.principal = { username: 'editor', userId: 'editor-id', role: 'user' };
  mocks.sessions.mockResolvedValue({ data: [{ sessionId: 'a1', projectId: 'a', userId: 'owner-id', title: '协作会话' }] });
  mocks.participants.mockResolvedValue({ code: '0000', data: [{ userId: 'editor-id', role: 'EDITOR', status: 'ACTIVE' }] });
  mocks.stream.mockResolvedValue(undefined);
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  const composer = screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' });
  await waitFor(() => expect(composer).toBeEnabled());
  fireEvent.change(composer, { target: { value: '继续协作任务' } });
  fireEvent.keyDown(composer, { key: 'Enter', code: 'Enter' });
  await waitFor(() => expect(mocks.stream).toHaveBeenCalledTimes(1));
  expect(mocks.stream.mock.calls[0][0]).toMatchObject({ sessionId: 'a1', projectId: 'a', query: '继续协作任务' });
});

it('keeps the composer disabled when only foreign or inactive writers exist', async () => {
  mocks.principal = { username: 'viewer', userId: 'viewer-id', role: 'user' };
  mocks.sessions.mockResolvedValue({ data: [{ sessionId: 'a1', projectId: 'a', userId: 'owner-id', title: '共享会话' }] });
  mocks.participants.mockResolvedValue({ code: '0000', data: [{ userId: 'foreign', role: 'OWNER', status: 'ACTIVE' }, { userId: 'viewer-id', role: 'EDITOR', status: 'REMOVED' }] });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('只读协作');
  expect(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' })).toBeDisabled();
});

it('preserves readable history and disables sending on permission failure until explicit retry', async () => {
  mocks.principal = { username: 'editor', userId: 'editor-id', role: 'user' };
  mocks.sessions.mockResolvedValue({ data: [{ sessionId: 'a1', projectId: 'a', userId: 'owner-id', title: '共享会话' }] });
  mocks.participants.mockRejectedValueOnce(new Error('network')).mockResolvedValue({ code: '0000', data: [{ userId: 'editor-id', role: 'EDITOR', status: 'ACTIVE' }] });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  const retry = await screen.findByRole('button', { name: '重试读取权限' });
  expect(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' })).toBeDisabled();
  expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a1');
  fireEvent.click(retry);
  await waitFor(() => expect(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' })).toBeEnabled());
});

it('does not let a late old EDITOR permission unlock the newly selected OBSERVER session', async () => {
  mocks.principal = { username: 'viewer', userId: 'viewer-id', role: 'user' };
  mocks.sessions.mockResolvedValue({ data: [{ sessionId: 'a1', projectId: 'a', userId: 'owner-id', title: '协作一' }, { sessionId: 'a2', projectId: 'a', userId: 'owner-id', title: '协作二' }] });
  let resolve!: (value: unknown) => void;
  mocks.participants.mockImplementationOnce(() => new Promise((next) => { resolve = next; })).mockResolvedValue({ code: '0000', data: [{ userId: 'viewer-id', role: 'OBSERVER', status: 'ACTIVE' }] });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  fireEvent.click(screen.getByRole('button', { name: /协作二/ }));
  await screen.findByText('已完成 a2older');
  await screen.findByText('只读协作');
  await act(async () => resolve({ code: '0000', data: [{ userId: 'viewer-id', role: 'EDITOR', status: 'ACTIVE' }] }));
  expect(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' })).toBeDisabled();
  expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a2');
  expect(mocks.stream).not.toHaveBeenCalled();
});
afterEach(() => {
  cleanup(); vi.unstubAllGlobals();
  if (rangeRect) Object.defineProperty(Range.prototype, 'getBoundingClientRect', rangeRect);
  else Reflect.deleteProperty(Range.prototype, 'getBoundingClientRect');
});

it('aligns the fallback project/session and never opens the foreign run or messages', async () => {
  render(tree('/chat?projectId=foreign&sessionId=b1&runId=b1older&acceptance=proof'));
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a1&acceptance=proof'));
  await screen.findByText('已完成 a1older');
  expect(mocks.messages).not.toHaveBeenCalledWith('b1');
  expect(screen.queryByText('已完成 b1older')).not.toBeInTheDocument();
  expect(mocks.sessions).toHaveBeenCalledWith({ userId: 'owner', projectId: 'a', limit: 100 });
  expect(mocks.detail).toHaveBeenCalledWith('b1', 'a');
});
it('preserves the explicitly authorized session and older real run even when a newer one exists', async () => {
  render(tree('/chat?projectId=a&sessionId=a2&runId=a2older'));
  await screen.findByText('已完成 a2older');
  expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a2&runId=a2older');
  fireEvent.click(screen.getByRole('button', { name: '查看运行' }));
  await screen.findByText('运行工作台');
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('runId=a2older'));
});
it('removes a mismatched run only after reading this session actual history', async () => {
  render(tree('/chat?projectId=a&sessionId=a2&runId=b1older'));
  await screen.findByText('已完成 a2older');
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a2'));
  await waitFor(() => expect(screen.getByTestId('route')).not.toHaveTextContent('runId='));
});
it('does not erase a copied session or run while project and session catalogs are loading', async () => {
  mocks.scope.projectId = ''; mocks.scope.loading = true;
  let resolve!: (value: unknown) => void;
  mocks.sessions.mockReturnValue(new Promise((done) => { resolve = done; }));
  const url = '/chat?projectId=a&sessionId=a2&runId=a2older';
  const view = render(tree(url));
  expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a2&runId=a2older');
  expect(mocks.sessions).not.toHaveBeenCalled();
  mocks.scope.projectId = 'a'; mocks.scope.loading = false; view.rerender(tree(url));
  await waitFor(() => expect(mocks.sessions).toHaveBeenCalledTimes(1));
  expect(screen.getByTestId('route')).toHaveTextContent('sessionId=a2&runId=a2older');
  await act(async () => resolve({ data: [{ sessionId: 'a2', projectId: 'a', title: '会话二' }] }));
  await screen.findByText('已完成 a2older');
  expect(screen.getByTestId('route')).toHaveTextContent('sessionId=a2&runId=a2older');
});
it('keeps ordinary conversation selection and its URL together while clearing the previous run', async () => {
  render(tree('/chat?projectId=a&sessionId=a1&runId=a1older'));
  await screen.findByText('已完成 a1older');
  fireEvent.click(screen.getByRole('button', { name: /会话二.*默认助手/ }));
  await screen.findByText('已完成 a2older');
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a2'));
  expect(screen.getByTestId('route')).not.toHaveTextContent('runId=');
});
it('preserves a server-accepted live run before its message is persisted and never resubmits it', async () => {
  mocks.stream.mockImplementation(async (_request, onEvent, signal) => {
    onEvent({ eventType: 'RUN_STARTED', runId: 'live-run' });
    await new Promise<void>((resolve) => signal.addEventListener('abort', () => resolve(), { once: true }));
  });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  fireEvent.change(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' }), { target: { value: '继续处理这个任务' } });
  fireEvent.click(screen.getByRole('button', { name: /发送$/ }));
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('runId=live-run'));
  await waitFor(() => expect(mocks.messages.mock.calls.length).toBeGreaterThan(1));
  expect(screen.getByTestId('route')).toHaveTextContent('runId=live-run');
  expect(mocks.stream).toHaveBeenCalledTimes(1);
});
it('never carries a live stream run or its later events into another explicitly selected project', async () => {
  let emit!: () => void;
  mocks.stream.mockImplementation(async (_request, onEvent, signal) => {
    emit = () => onEvent({ eventType: 'RUN_STARTED', runId: 'live-run' }); emit();
    await new Promise<void>((resolve) => signal.addEventListener('abort', () => resolve(), { once: true }));
  });
  const view = render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  fireEvent.change(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' }), { target: { value: '继续处理这个任务' } });
  fireEvent.click(screen.getByRole('button', { name: /发送$/ }));
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('runId=live-run'));
  mocks.scope.projectId = 'b';
  fireEvent.click(screen.getByRole('button', { name: '打开不同项目链接' }));
  view.rerender(tree('/chat?projectId=b&sessionId=b1&runId=live-run'));
  await screen.findByText('已完成 b1older');
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('?projectId=b&sessionId=b1'));
  await waitFor(() => expect(screen.getByTestId('route')).not.toHaveTextContent('runId='));
  await act(async () => emit());
  expect(screen.getByTestId('route')).toHaveTextContent('?projectId=b&sessionId=b1');
  await waitFor(() => expect(screen.getByTestId('route')).not.toHaveTextContent('runId='));
  expect(mocks.stream).toHaveBeenCalledTimes(1);
});

it('reads one authenticated older session when the same project bounded catalog cannot include it', async () => {
  mocks.sessions.mockResolvedValue({ code: '0000', data: Array.from({ length: 100 }, (_, i) =>
    ({ sessionId: 'a-listed-' + i, projectId: 'a', title: '最近会话' + i })) });
  mocks.detail.mockResolvedValue({ code: '0000', data: { sessionId: 'a-old', projectId: 'a', title: '较早会话' } });
  render(tree('/chat?projectId=a&sessionId=a-old&runId=a-oldolder'));
  await screen.findByText('已完成 a-oldolder');
  expect(mocks.detail).toHaveBeenCalledTimes(1);
  expect(mocks.detail).toHaveBeenCalledWith('a-old', 'a');
  expect(mocks.sessions).toHaveBeenCalledWith({ userId: 'owner', projectId: 'a', limit: 100 });
  expect(screen.getByTestId('route')).toHaveTextContent('sessionId=a-old&runId=a-oldolder');
});

it.each([403, 404])('clears a definite HTTP %s explicit session and its run after the authenticated point read', async (status) => {
  mocks.detail.mockRejectedValue(new ApiRequestError(status, 'Rejected', 'SESSION_READ_FORBIDDEN'));
  render(tree('/chat?projectId=a&sessionId=a-old&runId=a-oldolder'));
  await screen.findByText('已完成 a1older');
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a1'));
  expect(screen.getByTestId('route')).not.toHaveTextContent('runId=');
  expect(mocks.detail).toHaveBeenCalledWith('a-old', 'a');
  expect(mocks.messages).not.toHaveBeenCalledWith('a-old');
});

it.each([new ApiRequestError(503, 'Unavailable', 'temporary outage'), new TypeError('Failed to fetch')])(
  'preserves an unresolved explicit link on %s and retries the same ID normally', async (error) => {
  mocks.detail.mockRejectedValue(error);
  render(tree('/chat?projectId=a&sessionId=a-old&runId=a-oldolder'));
  await waitFor(() => expect(mocks.detail).toHaveBeenCalledTimes(1));
  await act(async () => {});
  expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a-old&runId=a-oldolder');
  expect(mocks.messages).not.toHaveBeenCalled();
  mocks.detail.mockResolvedValue({ code: '0000', data: { sessionId: 'a-old', projectId: 'a', title: '较早会话' } });
  fireEvent.click(screen.getByRole('button', { name: '刷新对话' }));
  await screen.findByText('已完成 a-oldolder');
  expect(screen.getByTestId('route')).toHaveTextContent('sessionId=a-old&runId=a-oldolder');
  expect(mocks.stream).not.toHaveBeenCalled();
});

it('ignores a late point read after another project has been explicitly selected', async () => {
  let resolve!: (value: unknown) => void;
  mocks.detail.mockReturnValue(new Promise((done) => { resolve = done; }));
  const view = render(tree('/chat?projectId=a&sessionId=a-old&runId=a-oldolder'));
  await waitFor(() => expect(mocks.detail).toHaveBeenCalledWith('a-old', 'a'));
  mocks.scope.projectId = 'b';
  fireEvent.click(screen.getByRole('button', { name: '打开不同项目链接' }));
  view.rerender(tree('/chat?projectId=b&sessionId=b1'));
  await screen.findByText('已完成 b1older');
  await act(async () => resolve({ code: '0000', data: { sessionId: 'a-old', projectId: 'a', title: '较早会话' } }));
  expect(screen.getByTestId('route')).toHaveTextContent('?projectId=b&sessionId=b1');
  expect(screen.getByTestId('route')).not.toHaveTextContent('sessionId=a-old');
  expect(mocks.messages).not.toHaveBeenCalledWith('a-old');
});

it('does not let an old stream rewrite a newly copied older session while its detail is pending', async () => {
  let resolve!: (value: unknown) => void;
  let emit!: () => void;
  mocks.detail.mockReturnValue(new Promise((done) => { resolve = done; }));
  mocks.stream.mockImplementation(async (_request, onEvent, signal) => {
    emit = () => onEvent({ eventType: 'RUN_STARTED', runId: 'live-run' }); emit();
    await new Promise<void>((done) => signal.addEventListener('abort', () => done(), { once: true }));
  });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  fireEvent.change(screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' }), { target: { value: '继续当前任务' } });
  fireEvent.click(screen.getByRole('button', { name: /发送$/ }));
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('runId=live-run'));
  fireEvent.click(screen.getByRole('button', { name: '打开旧会话链接' }));
  await waitFor(() => expect(mocks.detail).toHaveBeenCalledWith('a-old', 'a'));
  await act(async () => emit());
  expect(screen.getByTestId('route')).toHaveTextContent('sessionId=a-old&runId=a-oldolder');
  await act(async () => resolve({ code: '0000', data: { sessionId: 'a-old', projectId: 'a', title: '较早会话' } }));
  await screen.findByText('已完成 a-oldolder');
  await act(async () => emit());
  expect(screen.getByTestId('route')).toHaveTextContent('sessionId=a-old&runId=a-oldolder');
  expect(mocks.stream).toHaveBeenCalledTimes(1);
});

it('preserves a normally created conversation instead of choosing the prior catalog selection', async () => {
  mocks.detail.mockResolvedValue({ code: '0000', data: { sessionId: 'a-created', projectId: 'a', title: '新对话' } });
  render(tree('/chat?projectId=a&sessionId=a1'));
  await screen.findByText('已完成 a1older');
  fireEvent.click(screen.getByText('新对话').closest('button')!);
  await screen.findByText('已完成 a-createdolder');
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a-created'));
  expect(mocks.detail).toHaveBeenCalledWith('a-created', 'a');
  const verifiedReads = mocks.detail.mock.calls.length;
  fireEvent.click(screen.getByRole('button', { name: '刷新对话' }));
  await waitFor(() => expect(mocks.detail.mock.calls.length).toBeGreaterThan(verifiedReads));
  await waitFor(() => expect(screen.getByTestId('route')).toHaveTextContent('?projectId=a&sessionId=a-created'));
  expect(mocks.create).toHaveBeenCalledTimes(1);
});


it('waits for initial session and history resolution before accepting a message into a restored conversation', async () => {
  let resolveCatalog!: (value: any) => void;
  let resolveHistory!: (value: any) => void;
  mocks.sessions.mockReturnValue(new Promise((resolve) => { resolveCatalog = resolve; }));
  mocks.messages.mockReturnValue(new Promise((resolve) => { resolveHistory = resolve; }));
  render(tree('/chat?projectId=a'));
  const composer = screen.getByRole('textbox', { name: '发送给 OrbisOps 的消息' });
  expect(composer).toBeDisabled();
  fireEvent.keyDown(composer, { key: 'Enter', code: 'Enter' });
  expect(mocks.stream).not.toHaveBeenCalled();
  await act(async () => resolveCatalog({ data: [{ sessionId: 'a1', projectId: 'a', userId: 'owner', title: '已有任务' }] }));
  await waitFor(() => expect(mocks.messages).toHaveBeenCalledWith('a1'));
  expect(composer).toBeDisabled();
  await act(async () => resolveHistory({ data: history('a1') }));
  await waitFor(() => expect(composer).toBeEnabled());
  expect(mocks.stream).not.toHaveBeenCalled();
  expect(mocks.create).not.toHaveBeenCalled();
});
