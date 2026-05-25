import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { opsAdminService } from '../../../services/ops-admin-service';
import { useWorkflowEditorQuery } from './workflow-editor-query';

vi.mock('../../../services/ops-admin-service', () => ({ opsAdminService: {
  getProjectAgentCapabilities: vi.fn(), listAgents: vi.fn(), getAgent: vi.fn(), listAgentVersions: vi.fn(),
} }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });
const definition = (projectId: string, agentId: string) => ({ projectId, agentId, name: agentId, lifecycle: 'PUBLISHED', definitionKind: 'SPECIALIZED_WORKFLOW' });
const response = (data: unknown) => ({ code: '0000', data });
function setup() {
  vi.mocked(opsAdminService.getProjectAgentCapabilities).mockResolvedValue(response({}) as never);
  vi.mocked(opsAdminService.listAgents).mockImplementation(async project => response([definition(project!, 'w')]) as never);
  vi.mocked(opsAdminService.listAgentVersions).mockResolvedValue(response([]) as never);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  const wrapper = ({ children }: { children: React.ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  return { wrapper, client };
}

it('keeps a slow previous project response out of the currently selected editor', async () => {
  const { wrapper } = setup();
  let resolveOld!: (value: never) => void;
  vi.mocked(opsAdminService.getAgent).mockImplementation(id => id === 'old'
    ? new Promise(resolve => { resolveOld = resolve; })
    : Promise.resolve(response(definition('b', id)) as never));
  const { result, rerender } = renderHook(({ project, agent }) => useWorkflowEditorQuery(project, agent), { wrapper, initialProps: { project: 'a', agent: 'old' } });
  await waitFor(() => expect(resolveOld).toBeDefined());
  rerender({ project: 'b', agent: 'new' });
  await waitFor(() => expect(result.current.data?.definition?.agentId).toBe('new'));
  resolveOld(response(definition('a', 'old')) as never);
  await waitFor(() => expect(result.current.data?.projectId).toBe('b'));
  expect(result.current.data?.definition?.agentId).toBe('new');
});

it('rejects an existing definition from a different project rather than relabeling it', async () => {
  const { wrapper } = setup();
  vi.mocked(opsAdminService.getAgent).mockResolvedValue(response(definition('other', 'w')) as never);
  const { result } = renderHook(() => useWorkflowEditorQuery('a', 'w'), { wrapper });
  await waitFor(() => expect(result.current.isError).toBe(true));
  expect(result.current.data).toBeUndefined();
  expect(result.current.error?.message).toContain('当前项目不匹配');
});

it('surfaces a definition failure while allowing missing optional capability data', async () => {
  const { wrapper, client } = setup();
  vi.mocked(opsAdminService.getProjectAgentCapabilities).mockRejectedValue(new Error('offline'));
  vi.mocked(opsAdminService.getAgent).mockResolvedValue(response(definition('a', 'w')) as never);
  const { result } = renderHook(() => useWorkflowEditorQuery('a', 'w'), { wrapper });
  await waitFor(() => expect(result.current.isSuccess).toBe(true));
  expect(result.current.data?.capabilities).toBeNull();
  vi.mocked(opsAdminService.getAgent).mockResolvedValue({ code: '0001', info: '已失去访问权限' } as never);
  await client.invalidateQueries({ queryKey: ['workflow-editor', 'a', 'w'] });
  await waitFor(() => expect(result.current.isError).toBe(true));
  expect(result.current.error?.message).toBe('已失去访问权限');
});

it('does not fetch or prepare a default draft without a project', () => {
  const { wrapper } = setup();
  const { result } = renderHook(() => useWorkflowEditorQuery('', ''), { wrapper });
  expect(result.current.fetchStatus).toBe('idle');
  expect(result.current.data).toBeUndefined();
  expect(opsAdminService.getAgent).not.toHaveBeenCalled();
  expect(opsAdminService.listAgents).not.toHaveBeenCalled();
});
