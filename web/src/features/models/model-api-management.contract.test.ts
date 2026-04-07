import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Model management query boundary', () => {
  it('keeps catalog server state behind TanStack Query', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/model-api-management.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('aiClientApiAdminService');
    expect(source).not.toContain('aiClientModelAdminService');
    expect(source).not.toContain('loadData');
    expect(source).not.toContain('setProviders(');
    expect(source).not.toContain('setModels(');
    expect(source).toContain('useModelCatalogQuery(emptyDefaultPolicy)');
    expect(source).toContain('catalogQuery.refetch()');
  });

  it('uses query invalidation for model catalog mutations', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/models/api/model-queries.ts'), 'utf8');

    expect(source).toContain("catalog: () => [...modelQueryKeys.all, 'catalog']");
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: modelQueryKeys.catalog() })');
    expect(source).toContain('useUpdateDefaultModelPolicyMutation');
    expect(source).toContain('useProviderModelSyncMutation');
    expect(source).toContain('useSaveModelMutation');
  });
});
