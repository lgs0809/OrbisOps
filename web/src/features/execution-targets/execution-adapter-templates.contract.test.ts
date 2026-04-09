import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('Execution target template query boundary', () => {
  it('keeps template server state behind TanStack Query', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/execution-adapter-templates.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('opsRepairService');
    expect(source).not.toContain('loadTemplates');
    expect(source).not.toContain('setTemplates(');
    expect(source).toContain('useExecutionAdapterTemplatesQuery()');
    expect(source).toContain('templatesQuery.refetch()');
  });

  it('invalidates only the execution target template catalog after writes', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/execution-targets/api/execution-target-queries.ts'),
      'utf8',
    );

    expect(source).toContain("templates: () => [...executionTargetQueryKeys.all, 'templates']");
    expect(source).toContain('queryClient.invalidateQueries({ queryKey: executionTargetQueryKeys.templates() })');
    expect(source).toContain('useSaveExecutionAdapterTemplateMutation');
    expect(source).toContain('useCopyExecutionAdapterTemplateMutation');
    expect(source).toContain('useToggleExecutionAdapterTemplateMutation');
  });
});
