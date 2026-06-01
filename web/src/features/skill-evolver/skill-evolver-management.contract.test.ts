import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Skill Evolver query boundary', () => {
  it('keeps jobs and patches behind the feature Query API', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/skill-evolver-management.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('useCallback(');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('setJobs(');
    expect(source).not.toContain('setPatches(');
    expect(source).not.toContain('const load =');
    expect(source).toContain('useSkillEvolverOverviewQuery({ status, projectId })');
    expect(source).toContain('useRunSkillEvolverMutation()');
    expect(source).toContain('overviewQuery.isError');
  });

  it('invalidates every filtered overview after the worker is triggered', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/skill-evolver/api/skill-evolver-queries.ts'), 'utf8');

    expect(source).toContain('skillEvolverQueryKeys.overviews()');
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: skillEvolverQueryKeys.overviews() })');
  });
});
