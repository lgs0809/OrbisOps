import { QueryClient } from '@tanstack/react-query';
import { expect, it, vi } from 'vitest';
import { projectSkillCatalogOptions } from './project-skill-catalog';
import { opsAdminService } from '../../../services/ops-admin-service';

vi.mock('../../../services/ops-admin-service', () => ({ opsAdminService: { listProjectSkills: vi.fn() } }));
it('shares one scoped read across consumers and keeps disabled methods available to governance', async () => {
  const entry = { skillId: 'local', scope: 'PROJECT', projectId: 'project-a', status: 'DISABLED' };
  vi.mocked(opsAdminService.listProjectSkills).mockResolvedValue({ code: '0000', data: [entry] } as any);
  const client = new QueryClient();
  const [summary, governance] = await Promise.all([
    client.fetchQuery(projectSkillCatalogOptions('project-a')),
    client.fetchQuery(projectSkillCatalogOptions('project-a')),
  ]);
  expect(summary).toEqual([entry]);
  expect(governance).toEqual(summary);
  expect(opsAdminService.listProjectSkills).toHaveBeenCalledExactlyOnceWith('project-a');
  client.clear();
});
it('rejects wrong-scope entries and a revoked catalog before populating either consumer', async () => {
  vi.mocked(opsAdminService.listProjectSkills).mockResolvedValue({ code: '0000', data: [{ scope: 'GLOBAL', projectId: 'project-a' }] } as any);
  await expect(projectSkillCatalogOptions('project-a').queryFn()).rejects.toThrow('作用域不一致');
  vi.mocked(opsAdminService.listProjectSkills).mockResolvedValue({ code: '0001', info: '项目权限已撤销', data: [] } as any);
  await expect(projectSkillCatalogOptions('project-a').queryFn()).rejects.toThrow('项目权限已撤销');
});
