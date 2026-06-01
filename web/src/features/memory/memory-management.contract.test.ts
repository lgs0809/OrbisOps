import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Memory Management query boundary', () => {
  it('keeps context memory server state behind the feature Query API', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/memory-management.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('useCallback(');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('setMemories(');
    expect(source).not.toContain('const load =');
    expect(source).toContain('useContextMemoriesQuery');
    expect(source).toContain('useSaveContextMemoryMutation');
    expect(source).toContain('useArchiveContextMemoryMutation');
    expect(source).toContain('memoriesQuery.isError');
  });

  it('invalidates all filtered memory lists after writes', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/memory/api/memory-queries.ts'), 'utf8');

    expect(source).toContain('memoryQueryKeys.lists()');
    expect(source.match(/invalidateQueries\(\{ queryKey: memoryQueryKeys\.lists\(\) \}\)/g)?.length).toBe(2);
  });
});
