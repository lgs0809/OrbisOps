import { opsAdminService } from '../../../services/ops-admin-service';

// Both the capability summary and governance panel use the same authoritative
// project catalog, including disabled entries; mutations invalidate this key.
export const projectSkillCatalogOptions = (projectId: string) => ({
  queryKey: ['project-skill-governance', projectId],
  staleTime: 15_000,
  queryFn: async () => {
    const response = await opsAdminService.listProjectSkills(projectId);
    if (response.code !== '0000') throw new Error(response.info || '读取项目 Skill 失败');
    const entries = response.data || [];
    if (entries.some((skill) => skill.scope !== 'PROJECT' || skill.projectId !== projectId)) {
      throw new Error('项目 Skill 作用域不一致，请刷新后重试。');
    }
    return entries;
  },
});
