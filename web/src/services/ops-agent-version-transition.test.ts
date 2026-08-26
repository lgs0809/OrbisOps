import { afterEach, describe, expect, it, vi } from 'vitest';
import { opsAdminService } from './ops-admin-service';
import { ApiTimeoutError } from './ops-http-client';

const response = (data: unknown, status = 200) =>
  new Response(JSON.stringify({ code: '0000', data }), { status });

const agentId = 'workflow-isolated';
const version = 7;
const draft = { agentId, version, lifecycle: 'DRAFT' };
const validated = { ...draft, lifecycle: 'VALIDATED' };
const published = { ...draft, lifecycle: 'PUBLISHED' };

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('workflow version transition receipt', () => {
  it('reads a delayed validation result after a lost response without repeating the POST', async () => {
    vi.useFakeTimers();
    const request = vi.fn()
      .mockRejectedValueOnce(new ApiTimeoutError())
      .mockResolvedValueOnce(response([draft, { ...validated, version: 8 }]))
      .mockResolvedValueOnce(response([validated]));
    vi.stubGlobal('fetch', request);

    const completion = opsAdminService.validateAgentVersion(agentId, version);
    await vi.advanceTimersByTimeAsync(6000);
    await expect(completion).resolves.toMatchObject({ code: '0000', data: validated });
    expect(request.mock.calls.map(([, init]) => init.method)).toEqual(['POST', 'GET', 'GET']);
    expect(request.mock.calls[0][0]).toContain('/versions/7/validate');
  });

  it('requires PUBLISHED for the requested version after publish loses its response', async () => {
    vi.useFakeTimers();
    const unavailable = new ApiTimeoutError();
    const request = vi.fn()
      .mockRejectedValueOnce(unavailable)
      .mockResolvedValueOnce(response([validated, { ...published, version: 8 }]))
      .mockResolvedValueOnce(response([{ ...published, agentId: 'other-workflow' }]));
    vi.stubGlobal('fetch', request);

    const completion = expect(opsAdminService.publishAgentVersion(agentId, version)).rejects.toBe(unavailable);
    await vi.advanceTimersByTimeAsync(6000);
    await completion;
    expect(request.mock.calls.map(([, init]) => init.method)).toEqual(['POST', 'GET', 'GET']);
  });

  it('returns a persisted publish receipt on the first read after a gateway failure', async () => {
    const request = vi.fn().mockResolvedValueOnce(response(null, 504)).mockResolvedValueOnce(response([published]));
    vi.stubGlobal('fetch', request);
    await expect(opsAdminService.publishAgentVersion(agentId, version)).resolves.toMatchObject({ data: published });
    expect(request.mock.calls.map(([, init]) => init.method)).toEqual(['POST', 'GET']);
  });

  it('keeps explicit authorization errors without treating them as uncertain', async () => {
    const request = vi.fn().mockResolvedValueOnce(response(null, 403));
    vi.stubGlobal('fetch', request);
    await expect(opsAdminService.publishAgentVersion(agentId, version)).rejects.toMatchObject({ status: 403 });
    expect(request).toHaveBeenCalledTimes(1);
  });
});
