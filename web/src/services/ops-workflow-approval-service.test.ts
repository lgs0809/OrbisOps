import { afterEach, describe, expect, it, vi } from 'vitest';

import { opsWorkflowApprovalService } from './ops-workflow-approval-service';

const okResponse = (data: unknown) => new Response(JSON.stringify({ code: '0000', info: 'ok', data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
});

describe('opsWorkflowApprovalService', () => {
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('routes admin and user reads through their existing chat surfaces', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => okResponse({ available: true }));
    vi.stubGlobal('fetch', fetchMock);

    await opsWorkflowApprovalService.get('run-1', 'project-a', 'admin');
    await opsWorkflowApprovalService.get('run-2', 'project-b', 'user');

    expect(String(fetchMock.mock.calls[0][0])).toContain(
      '/api/v1/agent/chat/runs/run-1/workflow-approval?projectId=project-a',
    );
    expect(String(fetchMock.mock.calls[1][0])).toContain(
      '/api/v1/user/chat/runs/run-2/workflow-approval?projectId=project-b',
    );
  });

  it('binds the reviewed immutable approval and never forwards an actor', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse({ status: 'APPROVED' }));
    vi.stubGlobal('fetch', fetchMock);

    await opsWorkflowApprovalService.decide('run-1', 'project-a', 'APPROVE', 'approval-1', 'user');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/v1/user/chat/runs/run-1/workflow-approval/decision?projectId=project-a');
    expect(JSON.parse(String(init.body))).toEqual({ decision: 'APPROVE', approvalId: 'approval-1' });
  });

  it('uses the explicit durable resume retry endpoint after a committed decision', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse({ resumeRequired: true }));
    vi.stubGlobal('fetch', fetchMock);

    await opsWorkflowApprovalService.retryResume('run-1', 'project-a', 'approval-1', 'admin');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/v1/agent/chat/runs/run-1/workflow-approval/resume?projectId=project-a');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ approvalId: 'approval-1' });
  });
});
