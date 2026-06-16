import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Tool Routing observability query boundary', () => {
  it('uses the shared project scope and feature Query API instead of page-level loaders', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/tool-routing-observability.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('useCallback(');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('opsProjectService');
    expect(source).not.toContain('setSummary(');
    expect(source).not.toContain('setDecisions(');
    expect(source).not.toContain('loadProjects');
    expect(source).toContain('useProjectScope()');
    expect(source).toContain('useToolRoutingOverviewQuery(projectId)');
    expect(source).toContain('useRebuildToolCatalogMutation(projectId)');
    expect(source).toContain('useSelectToolRouteMutation(projectId)');
    expect(source).toContain('useReviewToolPolicyMutation(projectId)');
  });

  it('invalidates the project overview after routing and policy mutations', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/tool-routing/api/tool-routing-queries.ts'), 'utf8');

    expect(source).toContain("overview: (projectId: string) => [...toolRoutingQueryKeys.all, 'overview', projectId]");
    expect(source.match(/invalidateQueries\(\{ queryKey: toolRoutingQueryKeys\.overview\(projectId\) \}\)/g)?.length).toBe(3);
  });
});
