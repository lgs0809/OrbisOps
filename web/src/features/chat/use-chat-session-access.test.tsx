import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useChatSessionAccess } from './use-chat-session-access';

const mocks = vi.hoisted(() => ({ participants: vi.fn() }));
vi.mock('../../services/ops-admin-service', () => ({ opsAdminService: { listChatSessionParticipants: mocks.participants } }));
const session = { sessionId: 'shared', projectId: 'a', userId: 'owner', stateVersion: 2 };
beforeEach(() => mocks.participants.mockReset());
afterEach(cleanup);

it('keeps owners writable without depending on the participant endpoint', () => {
  const { result } = renderHook(() => useChatSessionAccess(session, 'owner', 'shared'));
  expect(result.current.canWrite).toBe(true);
  expect(mocks.participants).not.toHaveBeenCalled();
});
it.each(['OBSERVER', 'UNKNOWN', 'EDITOR'])('projects only the active actor role %s', async (role) => {
  mocks.participants.mockResolvedValue({ code: '0000', data: [
    { userId: 'other', role: 'OWNER', status: 'ACTIVE' },
    { userId: 'viewer', role, status: 'ACTIVE' },
  ] });
  const { result } = renderHook(() => useChatSessionAccess(session, 'viewer', 'shared'));
  expect(result.current.canWrite).toBe(false);
  await waitFor(() => expect(result.current.access).not.toBe('loading'));
  expect(result.current.canWrite).toBe(role === 'EDITOR');
});
it('does not grant writes for a removed participant or foreign participant', async () => {
  mocks.participants.mockResolvedValue({ code: '0000', data: [
    { userId: 'viewer', role: 'EDITOR', status: 'REMOVED' },
    { userId: 'foreign', role: 'EDITOR', status: 'ACTIVE' },
  ] });
  const { result } = renderHook(() => useChatSessionAccess(session, 'viewer', 'shared'));
  await waitFor(() => expect(result.current.access).toBe('readonly'));
  expect(result.current.canWrite).toBe(false);
});
it('fails closed on a network error and recovers only after an explicit retry', async () => {
  mocks.participants.mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ code: '0000', data: [{ userId: 'editor', role: 'EDITOR', status: 'ACTIVE' }] });
  const { result } = renderHook(() => useChatSessionAccess(session, 'editor', 'shared'));
  await waitFor(() => expect(result.current.access).toBe('unavailable'));
  expect(result.current.canWrite).toBe(false);
  act(() => result.current.retry());
  await waitFor(() => expect(result.current.canWrite).toBe(true));
  expect(mocks.participants).toHaveBeenCalledTimes(2);
});
it('ignores late permissions from an old session and keeps the new scope read only', async () => {
  let resolve!: (value: unknown) => void;
  mocks.participants.mockImplementationOnce(() => new Promise((next) => { resolve = next; }))
    .mockResolvedValue({ code: '0000', data: [{ userId: 'viewer', role: 'OBSERVER', status: 'ACTIVE' }] });
  const { result, rerender } = renderHook(({ current }) => useChatSessionAccess(current, 'viewer', current.sessionId), { initialProps: { current: session } });
  rerender({ current: { ...session, sessionId: 'new-scope', projectId: 'b' } });
  await waitFor(() => expect(result.current.access).toBe('readonly'));
  await act(async () => resolve({ code: '0000', data: [{ userId: 'viewer', role: 'EDITOR', status: 'ACTIVE' }] }));
  expect(result.current.canWrite).toBe(false);
});
it('does not enable an unresolved copied session', () => {
  const { result } = renderHook(() => useChatSessionAccess(undefined, 'viewer', 'not-loaded'));
  expect(result.current.canWrite).toBe(false);
  expect(result.current.access).toBe('loading');
});
