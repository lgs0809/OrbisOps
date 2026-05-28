import { describe, expect, it, vi } from 'vitest';
import { opsProjectService } from '../../../services/ops-project-service';
import { loadProjectWorkspaceSnapshot } from './project-workspace-queries';
vi.mock('../../../services/ops-project-service', () => ({ opsProjectService: { snapshot: vi.fn() } }));
describe('Project projection response', () => {
  it('preserves authoritative readiness and resource counts rather than deriving them from picker identities', async () => {
    const data = {projects:[{projectId:'p1',defaultAgentPublished:true,readyForInvestigation:true,
      resourceCount:8,generatedMcpCount:3}],templates:[]};
    vi.mocked(opsProjectService.snapshot).mockResolvedValue({code:'0000',info:'ok',data} as any);
    expect(await loadProjectWorkspaceSnapshot()).toBe(data);
  });
  it('rejects failed or malformed reads rather than presenting zero resources', async () => {
    vi.mocked(opsProjectService.snapshot).mockResolvedValue({code:'DENIED',data:{projects:[]}} as any);
    await expect(loadProjectWorkspaceSnapshot()).rejects.toThrow('项目详情暂时无法读取');
    vi.mocked(opsProjectService.snapshot).mockResolvedValue({code:'0000',data:{}} as any);
    await expect(loadProjectWorkspaceSnapshot()).rejects.toThrow('项目详情暂时无法读取');
  });
});
