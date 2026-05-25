import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { WorkflowManualTestButton } from './WorkflowManualTestButton';
import { opsAdminService, type OpsAgentDefinition } from '../../../services/ops-admin-service';

vi.mock('../../../services/ops-admin-service', () => ({ opsAdminService: { createChatSession: vi.fn() } }));
vi.mock('../../../services/auth-session', () => ({ getStoredUserInfo: () => ({ username: 'tester' }) }));
const definition: OpsAgentDefinition = { agentId: 'workflow-a', projectId: 'project-a', version: 2, lifecycle: 'PUBLISHED', name: '验收工作流' };
const success = { code: '0000', info: 'ok', data: 'session-new' };
beforeEach(() => { vi.mocked(opsAdminService.createChatSession).mockReset().mockResolvedValue(success); });
afterEach(cleanup);
const mount = (patch: Partial<OpsAgentDefinition> = {}) => {
  const navigate = vi.fn();
  render(<WorkflowManualTestButton projectId="project-a" definition={{ ...definition, ...patch }} onNavigate={navigate} />);
  return navigate;
};

it('creates a new session bound to the editor version and opens its explicit session URL', async () => {
  const navigate = mount();
  fireEvent.click(screen.getByRole('button', { name: '打开对话手动执行' }));
  await waitFor(() => expect(navigate).toHaveBeenCalledExactlyOnceWith('/chat?projectId=project-a&sessionId=session-new'));
  expect(opsAdminService.createChatSession).toHaveBeenCalledExactlyOnceWith({
    userId: 'tester', projectId: 'project-a', agentId: 'workflow-a', agentVersion: 2,
    title: '验收工作流 · v2 手动测试', mode: 'AGENT', engine: 'GRAPH',
    metadata: { executionType: 'WORKFLOW', executionName: '验收工作流' },
  });
});

it('keeps repeated clicks in flight from creating multiple conversations', async () => {
  let finish: (response: typeof success) => void = () => {};
  vi.mocked(opsAdminService.createChatSession).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
  const navigate = mount();
  const button = screen.getByRole('button', { name: '打开对话手动执行' });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(button).toBeDisabled();
  expect(opsAdminService.createChatSession).toHaveBeenCalledTimes(1);
  expect(navigate).not.toHaveBeenCalled();
  finish(success);
  await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
});

it.each([
  { code: '0002', info: '版本已撤销', data: 'session-rejected' },
  { code: '0000', info: 'ok', data: '' },
])('does not open an old conversation after a rejected or empty session response', async (response) => {
  vi.mocked(opsAdminService.createChatSession).mockResolvedValue(response);
  const navigate = mount();
  fireEvent.click(screen.getByRole('button', { name: '打开对话手动执行' }));
  await screen.findByRole('alert');
  expect(navigate).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: '打开对话手动执行' })).toBeEnabled();
});

it.each([{ lifecycle: 'DRAFT' }, { projectId: 'project-b' }, { version: undefined }])('requires a published version in the current project', (patch) => {
  mount(patch);
  const button = screen.getByRole('button', { name: '打开对话手动执行' });
  expect(button).toBeDisabled();
  fireEvent.click(button);
  expect(opsAdminService.createChatSession).not.toHaveBeenCalled();
});

it('shows a retryable failure when session creation loses its connection', async () => {
  vi.mocked(opsAdminService.createChatSession).mockRejectedValue(new Error('连接中断'));
  const navigate = mount();
  fireEvent.click(screen.getByRole('button', { name: '打开对话手动执行' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('连接中断');
  expect(navigate).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: '打开对话手动执行' })).toBeEnabled();
});

it('reuses the same frozen-version session contract for the workflow library entry', async () => {
  const navigate = vi.fn();
  render(<WorkflowManualTestButton projectId="project-a" definition={definition} compact onNavigate={navigate} />);
  fireEvent.click(screen.getByRole('button', { name: '使用工作流' }));
  await waitFor(() => expect(navigate).toHaveBeenCalledExactlyOnceWith('/chat?projectId=project-a&sessionId=session-new'));
  expect(opsAdminService.createChatSession).toHaveBeenCalledWith(expect.objectContaining({ agentId: 'workflow-a', agentVersion: 2, projectId: 'project-a', title: '验收工作流 · v2' }));
});
