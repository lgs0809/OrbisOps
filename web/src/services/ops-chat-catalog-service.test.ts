import { afterEach, expect, it, vi } from 'vitest';
import { opsAdminService } from './ops-admin-service';
import { ApiRequestError } from './ops-http-client';

afterEach(() => vi.unstubAllGlobals());

it('sends the selected project before the server applies the unchanged catalog limit', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: '0000', data: [] }), { status: 200 }));
  vi.stubGlobal('fetch', fetch);
  await opsAdminService.listChatSessions({ userId: 'owner', projectId: 'project/a', limit: 100 });
  const url = new URL(fetch.mock.calls[0][0], 'http://127.0.0.1');
  expect(url.pathname).toBe('/api/v1/user/chat/sessions');
  expect(url.searchParams.get('projectId')).toBe('project/a');
  expect(url.searchParams.get('limit')).toBe('100');
});

it('reads exactly one copied session and preserves native HTTP rejection for route decisions', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ info: 'SESSION_READ_FORBIDDEN' }), { status: 403 }));
  vi.stubGlobal('fetch', fetch);
  await expect(opsAdminService.getChatSession('session/a', 'project-a')).rejects.toBeInstanceOf(ApiRequestError);
  expect(fetch).toHaveBeenCalledTimes(1);
  const url = new URL(fetch.mock.calls[0][0], 'http://127.0.0.1');
  expect(url.pathname).toBe('/api/v1/user/chat/sessions/session%2Fa');
  expect(url.searchParams.get('projectId')).toBe('project-a');
});

it('reads only the selected session participant projection through the authenticated endpoint', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: '0000', data: [{ userId: 'viewer', role: 'OBSERVER', status: 'ACTIVE' }] }), { status: 200 }));
  vi.stubGlobal('fetch', fetch);
  const response = await opsAdminService.listChatSessionParticipants('shared/a');
  expect(response.data?.[0].role).toBe('OBSERVER');
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(new URL(fetch.mock.calls[0][0], 'http://127.0.0.1').pathname).toBe('/api/v1/user/chat/sessions/shared%2Fa/participants');
});
