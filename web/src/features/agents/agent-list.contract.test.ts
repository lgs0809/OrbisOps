import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Agent List query boundary', () => {
  it('keeps project workflow catalog and reference impacts behind TanStack Query', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/agent-list.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('loadAgentReferenceImpacts');
    expect(source).not.toContain('setDataSource(');
    expect(source).not.toContain('setReferenceImpacts(');
    expect(source).not.toContain('loadData');
    expect(source).toContain('useAgentListQuery(projectScope.projectId)');
    expect(source).toContain('useCloneAgentMutation()');
    expect(source).toContain('useDisableAgentMutation(projectScope.projectId)');
  });

  it('keeps search and pagination as local derivation and invalidates only project query keys after writes', () => {
    const page = readFileSync(resolve(process.cwd(), 'src/pages/agent-list.tsx'), 'utf8');
    const api = readFileSync(resolve(process.cwd(), 'src/features/agents/api/agent-list-queries.ts'), 'utf8');

    expect(page).toContain('const filteredWorkflows = useMemo');
    expect(page).toContain('filteredWorkflows.slice');
    expect(api).toContain("project: (projectId: string) => [...agentListQueryKeys.all, projectId]");
    expect(api.match(/invalidateQueries\(\{ queryKey: agentListQueryKeys\.project/g)?.length).toBe(2);
  });
});
