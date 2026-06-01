import { QueryClient, QueryClientProvider, focusManager } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { opsAdminService } from '../../../services/ops-admin-service';
import { useSkillEvolverJobQuery, useSkillEvolverOverviewQuery, useRetrySkillEvolverMutation } from './skill-evolver-queries';
vi.mock('../../../services/ops-admin-service', () => ({ opsAdminService: {
  getSkillEvolverJob: vi.fn(), listSkillEvolverJobs: vi.fn(), listSkillEvolverPatches: vi.fn(), createSkillEvolverJob: vi.fn(),
} }));
beforeEach(() => vi.resetAllMocks());
afterEach(() => { cleanup(); focusManager.setFocused(undefined); vi.useRealTimers(); });
const open = (id: string) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderHook(() => useSkillEvolverJobQuery(id), {
    wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  });
};
it('loads the exact job source even when the overview does not contain that job', async () => {
  vi.mocked(opsAdminService.getSkillEvolverJob).mockResolvedValue({ code: '0000', info: 'ok', data: { jobId: 'job-a', acceptedSourceId: 'acceptance-a' } });
  const view = open('job-a');
  await waitFor(() => expect(view.result.current.isSuccess).toBe(true));
  expect(opsAdminService.getSkillEvolverJob).toHaveBeenCalledWith('job-a');
  expect(view.result.current.data?.acceptedSourceId).toBe('acceptance-a');
});
it('does not present a different job or permission failure as missing acceptance', async () => {
  vi.mocked(opsAdminService.getSkillEvolverJob).mockResolvedValue({ code: '0000', info: 'ok', data: { jobId: 'job-b', acceptedSourceId: 'wrong' } });
  const view = open('job-a');
  await waitFor(() => expect(view.result.current.isError).toBe(true));
  expect(view.result.current.data).toBeUndefined();
  vi.mocked(opsAdminService.getSkillEvolverJob).mockResolvedValue({ code: '403', info: 'FORBIDDEN', data: {} });
  await view.result.current.refetch();
  await waitFor(() => expect(view.result.current.error?.message).toBe('FORBIDDEN'));
});
it('does not fetch a guessed source for a legacy record without a job identity', () => {
  open('');
  expect(opsAdminService.getSkillEvolverJob).not.toHaveBeenCalled();
});
it('observes a completed background job and stops polling its terminal detail', async () => {
  vi.useFakeTimers(); focusManager.setFocused(true);
  vi.mocked(opsAdminService.getSkillEvolverJob)
    .mockResolvedValueOnce({ code: '0000', info: 'ok', data: { jobId: 'job-a', status: 'RUNNING' } })
    .mockResolvedValue({ code: '0000', info: 'ok', data: { jobId: 'job-a', status: 'DONE', acceptedSourceId: 'acceptance-a' } });
  const view = open('job-a');
  await act(async () => { await vi.advanceTimersByTimeAsync(100); });
  expect(view.result.current.data?.status).toBe('RUNNING');
  await act(async () => { await vi.advanceTimersByTimeAsync(5100); });
  expect(view.result.current.data?.status).toBe('DONE');
  expect(view.result.current.data?.acceptedSourceId).toBe('acceptance-a');
  await act(async () => { await vi.advanceTimersByTimeAsync(20000); });
  expect(opsAdminService.getSkillEvolverJob).toHaveBeenCalledTimes(2);
});
it('refreshes publication facts without creating jobs and does not poll an unfocused workspace', async () => {
  vi.useFakeTimers(); focusManager.setFocused(true);
  vi.mocked(opsAdminService.listSkillEvolverJobs).mockResolvedValue({ code: '0000', info: 'ok', data: [] });
  vi.mocked(opsAdminService.listSkillEvolverPatches)
    .mockResolvedValueOnce({ code: '0000', info: 'ok', data: [{ projectId: 'p', status: 'PENDING_INDEX' }] })
    .mockResolvedValue({ code: '0000', info: 'ok', data: [{ projectId: 'p', publication: { status: 'ACTIVE', releasedVersion: 1 } }] });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = renderHook(() => useSkillEvolverOverviewQuery({ status: '', projectId: 'p' }), {
    wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  });
  await act(async () => { await vi.advanceTimersByTimeAsync(100); });
  expect(view.result.current.data?.patches[0].status).toBe('PENDING_INDEX');
  await act(async () => { await vi.advanceTimersByTimeAsync(10100); });
  expect(view.result.current.data?.patches[0].publication).toEqual({ status: 'ACTIVE', releasedVersion: 1 });
  focusManager.setFocused(false);
  await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
  expect(opsAdminService.listSkillEvolverJobs).toHaveBeenCalledTimes(2);
  expect(opsAdminService.listSkillEvolverPatches).toHaveBeenCalledTimes(2);
});

const failedJob = { jobId: 'job-a', runId: 'run-a', sessionId: 'session-a', projectId: 'p',
  agentId: 'agent-a', status: 'FAILED', acceptedSourceId: 'accepted-a' };
const retryHook = () => {
  const client = new QueryClient();
  return renderHook(() => useRetrySkillEvolverMutation(), {
    wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  });
};
it('recovers the same accepted source and verifies its actual queue status', async () => {
  vi.mocked(opsAdminService.createSkillEvolverJob).mockResolvedValue({ code: '0000', info: 'ok', data: { jobId: 'job-a' } });
  vi.mocked(opsAdminService.getSkillEvolverJob).mockResolvedValue({ code: '0000', info: 'ok', data: { ...failedJob, status: 'PENDING' } });
  const view = retryHook();
  await act(async () => { await view.result.current.mutateAsync(failedJob); });
  await waitFor(() => expect(view.result.current.isSuccess).toBe(true));
  expect(opsAdminService.createSkillEvolverJob).toHaveBeenCalledWith({ runId: 'run-a', sessionId: 'session-a',
    projectId: 'p', agentId: 'agent-a', triggerReason: 'MANUAL_RETRY' });
  expect(view.result.current.data?.acceptedSourceId).toBe('accepted-a');
  expect(view.result.current.data?.status).toBe('PENDING');
});
it('does not report success for an unchanged failed job or a different source', async () => {
  vi.mocked(opsAdminService.createSkillEvolverJob).mockResolvedValue({ code: '0000', info: 'ok', data: { jobId: 'job-a' } });
  const view = retryHook();
  for (const current of [failedJob, { ...failedJob, status: 'PENDING', acceptedSourceId: 'different' }]) {
    vi.mocked(opsAdminService.getSkillEvolverJob).mockResolvedValue({ code: '0000', info: 'ok', data: current });
    await act(async () => { await expect(view.result.current.mutateAsync(failedJob)).rejects.toThrow(); });
    expect(view.result.current.data).toBeUndefined();
  }
});
it('leaves publication and stale-source recovery to their existing lifecycles and never retries a denied command', async () => {
  const view = retryHook();
  for (const job of [{ ...failedJob, authoredPublication: { status: 'ROLLED_BACK' } },
    { ...failedJob, savedExperience: { currentSource: false } }, { ...failedJob, status: 'RUNNING' }]) {
    await act(async () => { await expect(view.result.current.mutateAsync(job)).rejects.toThrow('SKILL_RETRY_NOT_AVAILABLE'); });
  }
  expect(opsAdminService.createSkillEvolverJob).not.toHaveBeenCalled();
  vi.mocked(opsAdminService.createSkillEvolverJob).mockResolvedValue({ code: '403', info: 'FORBIDDEN', data: {} });
  await act(async () => { await expect(view.result.current.mutateAsync(failedJob)).rejects.toThrow('FORBIDDEN'); });
  expect(opsAdminService.createSkillEvolverJob).toHaveBeenCalledTimes(1);
  expect(opsAdminService.getSkillEvolverJob).not.toHaveBeenCalled();
});
