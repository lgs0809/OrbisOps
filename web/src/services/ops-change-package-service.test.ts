import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, ApiTimeoutError } from './ops-http-client';
import { opsChangePackageService } from './ops-change-package-service';

const okResponse = (data: unknown) => new Response(JSON.stringify({ code: '0000', info: 'ok', data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
});

describe('opsChangePackageService', () => {
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('uses the admin execution endpoint and preserves incident filters', async () => {
    localStorage.setItem('token', 'session-value');
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await opsChangePackageService.list({
      scope: 'admin',
      projectId: 'project-a',
      incidentId: 'incident-1',
      status: 'REVIEWING',
      limit: 25,
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/v1/admin/ops/change-packages?');
    expect(String(url)).toContain('projectId=project-a');
    expect(String(url)).toContain('incidentId=incident-1');
    expect(String(url)).toContain('status=REVIEWING');
    expect(String(url)).toContain('limit=25');
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer session-value');
  });

  it('uses the user endpoint without forwarding unsupported incident filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await opsChangePackageService.list({
      scope: 'user',
      projectId: 'project-a',
      incidentId: 'incident-hidden',
      sessionId: 'session-1',
    });

    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/v1/user/ops/change-packages?');
    expect(String(url)).toContain('projectId=project-a');
    expect(String(url)).toContain('sessionId=session-1');
    expect(String(url)).not.toContain('incidentId=');
  });

  it('sends approval identity facts to the selected scope endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse({ packageId: 'cp-1' }));
    vi.stubGlobal('fetch', fetchMock);

    await opsChangePackageService.approve('cp-1', 3, 'hash-3', 'user');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/v1/user/ops/change-packages/cp-1/approve');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ version: 3, packageHash: 'hash-3' });
  });

  it('covers the complete execution package read/write routing surface', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(okResponse({})));
    vi.stubGlobal('fetch', fetchMock);

    const assertLastCall = (path: string, method: string, body?: Record<string, unknown>) => {
      const call = fetchMock.mock.lastCall;
      expect(call).toBeDefined();
      const [url, init] = call!;
      expect(String(url)).toContain(path);
      expect(init.method).toBe(method);
      if (body) expect(JSON.parse(String(init.body))).toEqual(body);
    };

    await opsChangePackageService.listVersions('cp 1', 'admin');
    assertLastCall('/api/v1/admin/ops/change-packages/cp%201/versions', 'GET');

    await opsChangePackageService.listEvents('cp-1', 'user', 7);
    assertLastCall('/api/v1/user/ops/change-packages/cp-1/events?limit=7', 'GET');

    await opsChangePackageService.listLandingOperationRuns('cp-1', 'admin', 9);
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/landing-operation-runs?limit=9', 'GET');

    await opsChangePackageService.listLandingEvents('cp-1');
    assertLastCall('/api/v1/user/ops/change-packages/cp-1/landing-events?limit=100', 'GET');

    await opsChangePackageService.create({ projectId: 'project-a' });
    assertLastCall('/api/v1/admin/ops/change-packages', 'POST', { projectId: 'project-a' });

    await opsChangePackageService.createFromSession('session 1', { objective: 'recover' });
    assertLastCall('/api/v1/user/chat/sessions/session%201/change-packages', 'POST', { objective: 'recover' });

    await opsChangePackageService.revise('cp-1', { objective: 'revised' }, 'user');
    assertLastCall('/api/v1/user/ops/change-packages/cp-1/revise', 'POST', { objective: 'revised' });

    await opsChangePackageService.validate('cp-1');
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/validate', 'POST', {});

    await opsChangePackageService.submitReview('cp-1', { summary: 'ready' }, 'user');
    assertLastCall('/api/v1/user/ops/change-packages/cp-1/submit-review', 'POST', { summary: 'ready' });

    await opsChangePackageService.listApprovalChannels('cp-1', 'user');
    assertLastCall('/api/v1/user/ops/change-packages/cp-1/approval-channels', 'GET');

    await opsChangePackageService.sendApprovalCard('cp-1', 'channel-1', 'room-1', 'admin');
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/approval-card', 'POST', { channelId: 'channel-1', target: 'room-1' });

    await opsChangePackageService.reject('cp-1', { reason: 'unsafe' });
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/reject', 'POST', { reason: 'unsafe' });

    await opsChangePackageService.getLandingPlan('cp-1', 'user');
    assertLastCall('/api/v1/user/ops/change-packages/cp-1/landing-plan', 'GET');

    await opsChangePackageService.land('cp-1', { version: 2 }, 'user');
    assertLastCall('/api/v1/user/ops/change-packages/cp-1/land', 'POST', { version: 2 });

    await opsChangePackageService.cleanup('cp-1');
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/cleanup', 'POST', {});

    await opsChangePackageService.listApprovedValidationScripts('cp-1', 2, 'hash 2');
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/approved-validation-scripts?version=2&packageHash=hash+2', 'GET');

    await opsChangePackageService.createApprovedValidationScript('cp-1', { script: 'smoke' });
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/approved-validation-scripts', 'POST', { script: 'smoke' });

    await opsChangePackageService.updateApprovedValidationScriptStatus('cp-1', 'script 1', { status: 'DISABLED' });
    assertLastCall('/api/v1/admin/ops/change-packages/cp-1/approved-validation-scripts/script%201/status', 'PATCH', { status: 'DISABLED' });
  });

  it('keeps structured HTTP failures instead of flattening them', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ message: 'capability denied' }),
      { status: 403, statusText: 'Forbidden' },
    )));

    await expect(opsChangePackageService.get('cp-denied', 'user')).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 403,
      responseBody: 'capability denied',
    } satisfies Partial<ApiRequestError>);
  });

  it.each(['LANDING_RUNNING', 'LANDED'])('reconciles a lost response with the same approved %s run without replaying the write', async (status) => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new ApiTimeoutError())
      .mockResolvedValueOnce(okResponse({packageId:'cp-1', version:3, approvedVersion:3,
        approvedPackageHash:'hash-3', landingRunId:'lr-1', status}));
    vi.stubGlobal('fetch', fetchMock);
    await expect(opsChangePackageService.land('cp-1', {version:3, packageHash:'hash-3'}, 'user'))
      .resolves.toMatchObject({data:{status, landingRunId:'lr-1'}});
    expect(fetchMock.mock.calls.map(([url, init]) => [String(url).split('/change-packages/')[1], init.method]))
      .toEqual([['cp-1/land','POST'], ['cp-1','GET']]);
  });

  it.each([
    {status:'APPROVED'}, {status:'LANDING_FAILED'}, {landingRunId:''},
    {version:4}, {approvedVersion:2}, {approvedPackageHash:'other-hash'}, {packageId:'cp-other'},
  ])('keeps an uncertain or mismatched receipt unconfirmed: %j', async (difference) => {
    const lost = new ApiTimeoutError();
    const fetchMock = vi.fn().mockRejectedValueOnce(lost).mockResolvedValueOnce(okResponse({
      packageId:'cp-1', version:3, approvedVersion:3, approvedPackageHash:'hash-3',
      landingRunId:'lr-1', status:'LANDING_RUNNING', ...difference,
    }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(opsChangePackageService.land('cp-1', {version:3, packageHash:'hash-3'}, 'user')).rejects.toBe(lost);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1);
  });

  it.each([400, 401, 403, 409])('does not hide HTTP %s or issue a reconciliation read', async (status) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('denied', {status}));
    vi.stubGlobal('fetch', fetchMock);
    await expect(opsChangePackageService.land('cp-1', {version:3, packageHash:'hash-3'})).rejects.toMatchObject({status});
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('can reconcile a fetch network failure but still propagates denied read access', async () => {
    const fetchMock = vi.fn().mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(new Response('denied', {status:403}));
    vi.stubGlobal('fetch', fetchMock);
    await expect(opsChangePackageService.land('cp-1', {version:3, packageHash:'hash-3'})).rejects.toMatchObject({status:403});
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
