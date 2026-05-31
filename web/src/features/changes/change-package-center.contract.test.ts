import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Change Center query boundary', () => {
  it('keeps list and detail server state behind feature Query hooks', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/change-package-center.tsx'), 'utf8');

    expect(source.match(/useEffect\(/g)?.length).toBe(1);
    expect(source).not.toContain('opsChangePackageService');
    expect(source).not.toContain('useQuery({');
    expect(source).not.toContain('setEvents(');
    expect(source).not.toContain('setLandingOperations(');
    expect(source).not.toContain('setActionLoading(');
    expect(source).not.toContain('await openDetail(');
    expect(source).toContain('useChangePackagesQuery({');
    expect(source).toContain('useChangePackageDetailQuery(selectedPackageId, apiScope, detailVisible)');
    expect(source).toContain('useChangePackageActionMutation(apiScope)');
  });

  it('loads detail aggregates on demand and invalidates list plus detail after governed actions', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/changes/api/change-package-queries.ts'), 'utf8');

    expect(source).toContain('enabled: Boolean(packageId && enabled)');
    expect(source).toContain('opsChangePackageService.listEvents(packageId, scope, 100)');
    expect(source).toContain('opsChangePackageService.listLandingOperationRuns(packageId, scope, 200)');
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: changePackageQueryKeys.lists() })');
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: changePackageQueryKeys.detail(action.packageId, scope) })');
  });
});
