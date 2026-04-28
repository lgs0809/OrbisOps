import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Skill Management query boundary', () => {
  it('keeps server state behind the skills Query API while retaining UI lifecycle effects', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/skill-management.tsx'), 'utf8');

    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('opsProjectService');
    expect(source).not.toContain('setSkills(');
    expect(source).not.toContain('setDetail(');
    expect(source).not.toContain('setUsageRecords(');
    expect(source).not.toContain('setSkillVersions(');
    expect(source).not.toContain('detailRequestSeq');
    expect(source).not.toContain('loadSkillDetail');
    expect(source).not.toContain('const loadSkills');
    expect(source).toContain('useSkillCatalogQuery(sessionReady)');
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
