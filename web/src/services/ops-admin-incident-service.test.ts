import { afterEach, describe, expect, it, vi } from 'vitest';

import { OpsAdminService } from './ops-admin-service';

const okResponse = (data: unknown) => new Response(JSON.stringify({ code: '0000', info: 'ok', data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
});

describe('OpsAdminService incident queries', () => {
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('fails before HTTP when projectId is missing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const service = new OpsAdminService();

    await expect(service.listIncidents({ projectId: '   ' })).rejects.toThrow('INCIDENT_PROJECT_ID_REQUIRED');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('always sends the validated project boundary with named query fields', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const service = new OpsAdminService();

    await service.listIncidents({
      projectId: ' demo-project ',
      status: 'ACTION_REQUIRED',
      limit: 200,
      scope: 'user',
    });

    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/v1/user/ops/incidents?');
    expect(String(url)).toContain('projectId=demo-project');
    expect(String(url)).toContain('status=ACTION_REQUIRED');
    expect(String(url)).toContain('limit=200');
  });
});
