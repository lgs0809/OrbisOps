import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Skill Management canonical boundary', () => {
  it('keeps the legacy module as a thin compatibility shim and server state behind Query hooks', () => {
    const legacySource = readFileSync(resolve(process.cwd(), 'src/pages/skill-management.tsx'), 'utf8');
    const source = readFileSync(resolve(process.cwd(), 'src/pages/skills.tsx'), 'utf8');

    expect(legacySource.trim()).toBe("export { SkillsPage as SkillManagementPage } from './skills';");
    expect(source).not.toContain('opsProjectService');
    expect(source).not.toContain('setSkills(');
    expect(source).not.toContain('loadSkillDetail');
    expect(source).toContain('useSkillCatalogQuery(true)');
    expect(source).toContain('useSkillDetailQuery');
    expect(source).toContain('useSkillContextQuery');
    expect(source).toContain('useSkillUsageQuery');
    expect(source).toContain('useSkillVersionsQuery');
    expect(source).toContain('useSkillCopyProjectsQuery(copyVisible)');
  });

  it('keeps catalog auth-gated and secondary data on demand', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/skills/api/skill-queries.ts'), 'utf8');

    expect(source).toContain('useSkillCatalogQuery = (enabled = true)');
    expect(source).toContain('enabled,');
    expect(source).toContain('enabled: Boolean(skillId && enabled)');
    expect(source).toContain("useSkillCopyProjectsQuery = (enabled = true)");
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: skillQueryKeys.details() })');
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: skillQueryKeys.contexts() })');
  });
});
