import { describe, it, expect, vi } from 'vitest';
import { followChatRun } from './follow-chat-run';

describe('read-only follow after SSE closure', () => {
  it('keeps following a running task until the authoritative answer arrives', async () => {
    const read = vi.fn().mockResolvedValueOnce({ status: 'RUNNING' })
      .mockResolvedValueOnce({ status: 'SUCCEEDED', response: { content: '等待审批' } });
    const update = vi.fn();
    const result = await followChatRun(read, update, new AbortController().signal, 1);
    expect(read).toHaveBeenCalledTimes(2);
    expect(result?.response?.content).toBe('等待审批');
    expect(update).toHaveBeenCalledTimes(2);
  });
  it('retains failure and approval boundaries instead of treating them as success', async () => {
    for (const status of ['FAILED', 'CANCELED', 'WAITING_APPROVAL', 'RECOVERY_REVIEW_REQUIRED']) {
      const read = vi.fn().mockResolvedValue({ status });
      expect((await followChatRun(read, vi.fn(), new AbortController().signal, 1))?.status).toBe(status);
      expect(read).toHaveBeenCalledTimes(1);
    }
  });
  it('retries transient reads without resubmitting the task', async () => {
    const read = vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValue({ status: 'FAILED' });
    expect((await followChatRun(read, vi.fn(), new AbortController().signal, 1))?.status).toBe('FAILED');
    expect(read).toHaveBeenCalledTimes(2);
  });
  it('stops updates when canceled during an outstanding read', async () => {
    const abort = new AbortController();
    const update = vi.fn();
    const read = vi.fn().mockImplementation(async () => { abort.abort(); return { status: 'RUNNING' }; });
    await followChatRun(read, update, abort.signal, 1);
    expect(update).not.toHaveBeenCalled();
  });
});
